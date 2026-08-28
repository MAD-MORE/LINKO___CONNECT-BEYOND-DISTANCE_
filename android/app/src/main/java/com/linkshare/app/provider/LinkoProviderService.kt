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
import com.linkshare.app.network.LinkoDeviceControlApi
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.tunnel.FullIpProviderTransportAdapter
import com.linkshare.app.tunnel.ProviderSocketFactory
import com.linkshare.app.tunnel.ProviderTunnelRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress

/**
 * Android Foreground Service managing Phone A's provider-side tunnel execution.
 * Hardened with WakeLock, WifiLock, network state listeners, and auto-recovery.
 */
class LinkoProviderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var auth: LinkoAuth
    private lateinit var api: LinkoDeviceControlApi
    private val seen = mutableSetOf<String>()
    private val runners = mutableMapOf<String, ProviderTunnelRunner>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        auth = LinkoAuth(this)
        api = LinkoDeviceControlApi(this, auth)
        acquireLocks()
        createChannel()
        startForeground(NOTIFICATION_ID, serviceNotification("Provider Ready", "LINKO is active and ready for connection requests"))
        registerNetworkCallback()
        scope.launch { runCatching { api.ensureRegistered() } }
        scope.launch { listenToRealtime() }
        scope.launch { heartbeat() }
    }

    private fun acquireLocks() {
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LINKO:ProviderWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24h safety timeout
            }
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LINKO:ProviderWifiLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.w(TAG, "Could not acquire locks: ${it.message}") }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
        }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val builder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Network became available for provider sharing")
                notificationManager().notify(NOTIFICATION_ID, serviceNotification("Provider Sharing Active", "Internet connection is active and stable"))
            }
            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost on provider device")
                notificationManager().notify(NOTIFICATION_ID, serviceNotification("Network Interrupted", "Waiting for internet connection to restore…"))
            }
        }
        runCatching { cm.registerNetworkCallback(builder.build(), networkCallback!!) }
    }

    private fun hasActiveInternet(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun listenToRealtime() {
        LinkoRealtimeManager.events.collect { event ->
            if (!currentCoroutineContext().isActive) return@collect
            when (event) {
                is LinkoRealtimeEvent.SessionStateChanged -> if (event.state == "requested") notifyPendingConnectionRequests()
                else -> Unit
            }
        }
    }

    private suspend fun notifyPendingConnectionRequests() {
        runCatching { api.pendingProviderRequests() }
            .getOrDefault(emptyList())
            .forEach { request -> postRequestNotification(request.id, request.receiverDeviceId) }
    }

    private suspend fun heartbeat() {
        while (scope.isActive) {
            try {
                api.ensureRegistered()
                api.touchPresence()
            } catch (_: Exception) { }
            delay(15_000L)
        }
    }

    private fun accept(requestId: String) {
        scope.launch {
            if (!hasActiveInternet()) {
                Log.w(TAG, "Provider cannot accept session: No active internet connection")
                notificationManager().notify(NOTIFICATION_ID, serviceNotification("Cannot Share", "No active internet connection on this device"))
                return@launch
            }
            runCatching {
                api.transition(requestId, "approved")
                startApproved(requestId)
            }.onFailure {
                stopRunner(requestId)
            }
        }
    }

    private fun startApproved(requestId: String) {
        scope.launch {
            if (!hasActiveInternet()) {
                Log.w(TAG, "Provider cannot start sharing: No active internet connection")
                notificationManager().notify(NOTIFICATION_ID, serviceNotification("Cannot Share", "No active internet connection on this device"))
                return@launch
            }
            var config = runCatching { api.tunnelConfig(requestId) }.getOrNull()
            if (config == null) {
                repeat(TUNNEL_CONFIG_RETRIES) { attempt ->
                    delay(TUNNEL_CONFIG_RETRY_MS)
                    config = runCatching { api.tunnelConfig(requestId) }.getOrNull()
                    if (config != null) return@repeat
                }
            }
            val activeConfig = config ?: run {
                Log.e(TAG, "Could not fetch tunnel config for approved session $requestId")
                stopRunner(requestId)
                return@launch
            }

            stopRunner(requestId)
            val socket = java.net.DatagramSocket(0)
            val runner = ProviderTunnelRunner(
                socket = socket,
                endpoint = InetSocketAddress(activeConfig.host, activeConfig.port),
                sessionId = activeConfig.sessionId,
                sessionKey = activeConfig.key,
                scope = scope,
                adapter = FullIpProviderTransportAdapter(),
            )
            runners[requestId] = runner
            runner.start()
            notificationManager().notify(NOTIFICATION_ID, serviceNotification("Sharing Active", "Encrypted LINKO tunnel is live and routing traffic"))
        }
    }

    private fun decline(requestId: String) {
        scope.launch {
            stopRunner(requestId)
            runCatching { api.transition(requestId, "denied") }
            notificationManager().notify(NOTIFICATION_ID, serviceNotification("Provider Ready", "The request was declined"))
        }
    }

    private fun postRequestNotification(requestId: String, receiverDeviceId: String) {
        if (!seen.add(requestId)) return
        val accept = PendingIntent.getService(this, requestId.hashCode(), actionIntent(ACTION_ACCEPT, requestId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val decline = PendingIntent.getService(this, requestId.hashCode() + 1, actionIntent(ACTION_DECLINE, requestId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, requestId.hashCode() + 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("LINKO connection request")
            .setContentText("A friend wants to use your connection")
            .setStyle(Notification.BigTextStyle().bigText("A friend device ($receiverDeviceId) wants to use your connection."))
            .setContentIntent(open)
            .setAutoCancel(false)
            .addAction(Notification.Action.Builder(null, "ACCEPT", accept).build())
            .addAction(Notification.Action.Builder(null, "DECLINE", decline).build())
            .build()
        notificationManager().notify(requestId.hashCode(), notification)
    }

    private fun actionIntent(action: String, requestId: String) = Intent(this, LinkoProviderService::class.java).setAction(action).putExtra(EXTRA_REQUEST_ID, requestId)
    private fun serviceNotification(title: String, text: String): Notification = Notification.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(text).setContentIntent(PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).setOngoing(true).build()
    private fun createChannel() { notificationManager().createNotificationChannel(NotificationChannel(CHANNEL_ID, "LINKO Provider", NotificationManager.IMPORTANCE_HIGH)) }
    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun stopRunner(sessionId: String) { runners.remove(sessionId)?.stop() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACCEPT -> intent.getStringExtra(EXTRA_REQUEST_ID)?.let(::accept)
            ACTION_DECLINE -> intent.getStringExtra(EXTRA_REQUEST_ID)?.let(::decline)
            ACTION_START_APPROVED -> intent.getStringExtra(EXTRA_REQUEST_ID)?.let(::startApproved)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { networkCallback?.let { (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.unregisterNetworkCallback(it) } }
        releaseLocks()
        runners.values.toList().forEach { it.stop() }
        runners.clear()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LINKO_PROVIDER_SERVICE"
        const val ACTION_ACCEPT = "com.linkshare.app.provider.ACCEPT"
        const val ACTION_DECLINE = "com.linkshare.app.provider.DECLINE"
        const val ACTION_START_APPROVED = "com.linkshare.app.provider.START_APPROVED"
        const val EXTRA_REQUEST_ID = "request_id"
        private const val CHANNEL_ID = "linko_provider"
        private const val NOTIFICATION_ID = 7001
        private const val TUNNEL_CONFIG_RETRIES = 5
        private const val TUNNEL_CONFIG_RETRY_MS = 1_000L
        fun start(context: Context) { context.startForegroundService(Intent(context, LinkoProviderService::class.java)) }
        fun stop(context: Context) { context.stopService(Intent(context, LinkoProviderService::class.java)) }
    }
}
