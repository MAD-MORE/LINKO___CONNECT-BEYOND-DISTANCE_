package com.linkshare.app.provider

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.linkshare.app.MainActivity
import com.linkshare.app.R
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.DirectP2pNegotiator
import com.linkshare.app.network.LinkoDeviceControlApi
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoNetworkException
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.network.LinkoSignalingClient
import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import com.linkshare.app.tunnel.FullIpProviderTransportAdapter
import com.linkshare.app.tunnel.ProviderTunnelRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.util.concurrent.ConcurrentHashMap

/** Provider foreground service. It shares the real device Internet only over a direct P2P tunnel. */
class LinkoProviderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var auth: LinkoAuth
    private lateinit var api: LinkoDeviceControlApi
    private val seen = mutableSetOf<String>()
    private val runners = ConcurrentHashMap<String, ProviderTunnelRunner>()
    private val startingSessions = ConcurrentHashMap.newKeySet<String>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun acquireLocks() {
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LINKO:ProviderWakeLock")?.apply { setReferenceCounted(false); acquire(24 * 60 * 60 * 1000L) }
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LINKO:ProviderWifiLock")?.apply { setReferenceCounted(false); acquire() }
        }.onFailure { Log.w(TAG, "Could not acquire locks: ${it.message}") }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release(); wakeLock = null
            if (wifiLock?.isHeld == true) wifiLock?.release(); wifiLock = null
        }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Network became available for provider sharing")
                notificationManager().notify(NOTIFICATION_ID, serviceNotification("Provider Ready", "Internet connection is available"))
            }
            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost on provider device")
                notificationManager().notify(NOTIFICATION_ID, serviceNotification("Network Interrupted", "Waiting for Internet to return…"))
            }
        }
        runCatching { cm.registerNetworkCallback(request, networkCallback!!) }
    }

    private fun hasActiveInternet(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun listenToRealtime() {
        LinkoRealtimeManager.events.collect { event ->
            if (!currentCoroutineContext().isActive) return@collect
            if (event is LinkoRealtimeEvent.SessionStateChanged && event.state == "requested") notifyPendingConnectionRequests()
        }
    }

    private suspend fun notifyPendingConnectionRequests() {
        runCatching { api.pendingProviderRequests() }.getOrDefault(emptyList()).forEach { request -> postRequestNotification(request.id, request.receiverDeviceId) }
    }

    private suspend fun heartbeat() {
        while (scope.isActive) {
            runCatching { api.ensureRegistered(); api.touchPresence(); LinkoRealtimeManager.start(this@LinkoProviderService) }
                .onFailure { error -> Log.w(TAG, "Presence heartbeat failed: ${error.message}") }
            delay(15_000L)
        }
    }

    private fun accept(requestId: String) {
        scope.launch {
            Log.i(TAG, "ACCEPT_REQUEST session=$requestId")
            notificationManager().cancel(requestId.hashCode())
            if (!hasActiveInternet()) {
                failSession(requestId, "provider_internet_unavailable")
                return@launch
            }
            runCatching { api.transition(requestId, "approved") }
                .onSuccess {
                    Log.i(TAG, "SESSION_APPROVED session=$requestId")
                    startApproved(requestId)
                }
                .onFailure { error -> failSession(requestId, error.message ?: "approval_failed", error) }
        }
    }

    private fun startApproved(requestId: String) {
        if (requestId.isBlank()) return
        if (runners.containsKey(requestId)) {
            Log.i(TAG, "PROVIDER_SERVICE_ALREADY_RUNNING session=$requestId")
            return
        }
        if (!startingSessions.add(requestId)) {
            Log.i(TAG, "PROVIDER_SERVICE_ALREADY_RUNNING session=$requestId startup_already_in_progress")
            return
        }

        scope.launch {
            Log.i(TAG, "PROVIDER_SERVICE_START session=$requestId")
            try {
                notificationManager().cancel(requestId.hashCode())
                if (!hasActiveInternet()) throw LinkoNetworkException("provider_internet_unavailable")

                var config = runCatching { api.tunnelConfig(requestId) }.getOrNull()
                if (config == null) {
                    repeat(TUNNEL_CONFIG_RETRIES) {
                        delay(TUNNEL_CONFIG_RETRY_MS)
                        config = runCatching { api.tunnelConfig(requestId) }.getOrNull()
                    }
                }
                val activeConfig = config ?: throw LinkoNetworkException("tunnel_config_unavailable")
                Log.i(TAG, "TUNNEL_CONFIG_LOADED session=${activeConfig.sessionId} transport=${activeConfig.transport}")
                if (activeConfig.sessionId != requestId) throw LinkoNetworkException("session_id_mismatch")
                if (activeConfig.transport != "direct_udp") throw LinkoNetworkException("unsupported_direct_transport")
                if (activeConfig.role != "provider") throw LinkoNetworkException("invalid_provider_role")
                if (activeConfig.key.size != 32) throw LinkoNetworkException("invalid_tunnel_key")

                val token = auth.currentAccessToken()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("device_auth_required")
                val socket = runCatching { DatagramSocket(0) }.getOrElse { throw LinkoNetworkException("udp_socket_creation_failed: ${it.message}") }
                try {
                    Log.i(TAG, "UDP_SOCKET_CREATED session=$requestId port=${socket.localPort}")
                    LinkoEngineBridge.reportTunnelState("direct_connecting", "Finding a direct peer path")
                    Log.i(TAG, "P2P_NEGOTIATION_STARTED session=$requestId")
                    val negotiated = DirectP2pNegotiator.establish(
                        sessionId = activeConfig.sessionId,
                        sessionKey = activeConfig.key,
                        role = EncryptedDatagramTunnel.Role.PROVIDER,
                        signaling = LinkoSignalingClient(accessToken = token),
                        socket = socket,
                    )
                    Log.i(TAG, "UDP_CHECK_SUCCEEDED session=$requestId peer=${negotiated.peer}")
                    val runner = ProviderTunnelRunner(
                        socket = negotiated.socket,
                        endpoint = negotiated.peer,
                        sessionId = activeConfig.sessionId,
                        sessionKey = activeConfig.key,
                        scope = scope,
                        adapter = FullIpProviderTransportAdapter(),
                    )
                    runners[requestId] = runner
                    runner.start()
                    api.transition(activeConfig.sessionId, "connected")
                    LinkoEngineBridge.reportTunnelState("connected", "Direct connection established; Provider is sharing Internet")
                    notificationManager().notify(NOTIFICATION_ID, serviceNotification("Sharing Active", "Direct encrypted connection is live"))
                    Log.i(TAG, "TUNNEL_STARTED session=$requestId peer=${negotiated.peer}")
                    Log.i(TAG, "SESSION_CONNECTED session=$requestId")
                } catch (error: Exception) {
                    runCatching { socket.close() }
                    throw error
                }
            } catch (error: Exception) {
                failSession(requestId, error.message ?: "direct_connection_failed", error)
            } finally {
                startingSessions.remove(requestId)
            }
        }
    }

    private fun failSession(sessionId: String, reason: String, error: Throwable? = null) {
        if (error != null) Log.e(TAG, "TUNNEL_FAILED session=$sessionId reason=$reason", error)
        else Log.e(TAG, "TUNNEL_FAILED session=$sessionId reason=$reason")
        scope.launch {
            runCatching { api.transition(sessionId, "failed") }
                .onFailure { Log.e(TAG, "SESSION_FAILED state update failed session=$sessionId reason=${it.message}", it) }
            stopRunner(sessionId)
            LinkoEngineBridge.reportTunnelState("failed", reason)
            Log.e(TAG, "SESSION_FAILED session=$sessionId reason=$reason")
            notificationManager().notify(NOTIFICATION_ID, serviceNotification("Connection Failed", reason.replace('_', ' ')))
        }
    }

    private fun decline(requestId: String) {
        scope.launch {
            notificationManager().cancel(requestId.hashCode())
            stopRunner(requestId)
            runCatching { api.transition(requestId, "denied") }
            notificationManager().notify(NOTIFICATION_ID, serviceNotification("Provider Ready", "The request was declined"))
        }
    }

    private fun postRequestNotification(requestId: String, receiverDeviceId: String) {
        if (!seen.add(requestId)) return
        val accept = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) PendingIntent.getForegroundService(this, requestId.hashCode(), actionIntent(ACTION_ACCEPT, requestId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        else PendingIntent.getService(this, requestId.hashCode(), actionIntent(ACTION_ACCEPT, requestId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val decline = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) PendingIntent.getForegroundService(this, requestId.hashCode() + 1, actionIntent(ACTION_DECLINE, requestId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        else PendingIntent.getService(this, requestId.hashCode() + 1, actionIntent(ACTION_DECLINE, requestId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, requestId.hashCode() + 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("EXTRA_REQUEST_ID", requestId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("⚡ Connection Request")
            .setContentText("A friend wants to use your internet")
            .setStyle(Notification.BigTextStyle().bigText("A verified LINKO friend is requesting permission to share your internet connection."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "AUTHORIZE & SHARE", accept).build())
            .addAction(Notification.Action.Builder(null, "DECLINE", decline).build())
            .build()
        notificationManager().notify(requestId.hashCode(), notification)
    }

    private fun actionIntent(action: String, requestId: String) = Intent(this, LinkoProviderService::class.java).setAction(action).putExtra(EXTRA_REQUEST_ID, requestId)
    private fun serviceNotification(title: String, text: String): Notification = Notification.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(text).setContentIntent(PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).setOngoing(true).build()
    private fun createChannel() { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) notificationManager().createNotificationChannel(NotificationChannel(CHANNEL_ID, "LINKO Provider", NotificationManager.IMPORTANCE_HIGH)) }
    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun stopRunner(sessionId: String) { startingSessions.remove(sessionId); runners.remove(sessionId)?.stop() }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        auth = LinkoAuth(this)
        api = LinkoDeviceControlApi(this, auth)
        acquireLocks(); createChannel()
        startForeground(NOTIFICATION_ID, serviceNotification("Provider Ready", "LINKO is active and ready for connection requests"))
        registerNetworkCallback(); LinkoRealtimeManager.start(this)
        scope.launch { runCatching { api.ensureRegistered(); api.touchPresence() } }
        scope.launch { listenToRealtime() }
        scope.launch { heartbeat() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LinkoRealtimeManager.start(this)
        val dataCapMb = intent?.getLongExtra(EXTRA_DATA_CAP_MB, 0L) ?: 0L
        if (dataCapMb > 0) dataCapBytes = dataCapMb * 1024 * 1024
        when (intent?.action) {
            ACTION_ACCEPT -> intent.getStringExtra(EXTRA_REQUEST_ID)?.let(::accept)
            ACTION_DECLINE -> intent.getStringExtra(EXTRA_REQUEST_ID)?.let(::decline)
            ACTION_START_APPROVED -> intent.getStringExtra(EXTRA_REQUEST_ID)?.let(::startApproved)
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        runCatching { networkCallback?.let { (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.unregisterNetworkCallback(it) } }
        releaseLocks(); runners.values.toList().forEach { it.stop() }; runners.clear(); startingSessions.clear(); scope.cancel(); super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LINKO_PROVIDER_SERVICE"
        var isRunning: Boolean = false; private set
        var dataCapBytes: Long = 0L
        const val ACTION_ACCEPT = "com.linkshare.app.provider.ACCEPT"
        const val ACTION_DECLINE = "com.linkshare.app.provider.DECLINE"
        const val ACTION_START_APPROVED = "com.linkshare.app.provider.START_APPROVED"
        const val ACTION_STOP = "com.linkshare.app.provider.STOP"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_DATA_CAP_MB = "extra_data_cap_mb"
        private const val CHANNEL_ID = "linko_provider"
        private const val NOTIFICATION_ID = 7001
        private const val TUNNEL_CONFIG_RETRIES = 5
        private const val TUNNEL_CONFIG_RETRY_MS = 1_000L
        fun start(context: Context, dataCapMb: Long = 0L) { context.startForegroundService(Intent(context, LinkoProviderService::class.java).apply { if (dataCapMb > 0) putExtra(EXTRA_DATA_CAP_MB, dataCapMb) }) }
        fun stop(context: Context) { context.stopService(Intent(context, LinkoProviderService::class.java)) }
    }
}
