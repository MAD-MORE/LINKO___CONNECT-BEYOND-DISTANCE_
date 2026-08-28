package com.linkshare.app.provider

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
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
 * Validates connectivity, manages session approval, and routes client traffic to the Internet.
 */
class LinkoProviderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var auth: LinkoAuth
    private lateinit var api: LinkoDeviceControlApi
    private val seen = mutableSetOf<String>()
    private val runners = mutableMapOf<String, ProviderTunnelRunner>()

    override fun onCreate() {
        super.onCreate()
        auth = LinkoAuth(this)
        api = LinkoDeviceControlApi(this, auth)
        createChannel()
        startForeground(NOTIFICATION_ID, serviceNotification("Provider Ready", "LINKO is ready for connection requests"))
        scope.launch { runCatching { api.ensureRegistered() } }
        scope.launch { listenToRealtime() }
        scope.launch { heartbeat() }
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
                notificationManager().notify(NOTIFICATION_ID, serviceNotification("Connection Failed", "The secure connection could not be started"))
            }
        }
    }

    private fun startApproved(requestId: String) {
        scope.launch {
            runCatching {
                api.transition(requestId, "signaling")
                establishEngine(requestId)
            }.onFailure {
                stopRunner(requestId)
                notificationManager().notify(NOTIFICATION_ID, serviceNotification("Connection Failed", "The secure connection could not be started"))
            }
        }
    }

    private suspend fun establishEngine(sessionId: String) {
        var lastError: Throwable? = null
        repeat(TUNNEL_CONFIG_RETRIES) { attempt ->
            try {
                val config = api.tunnelConfig(sessionId)
                require(config.role == "provider") { "provider_role_required" }
                stopRunner(sessionId)

                val runner = ProviderTunnelRunner(
                    socket = ProviderSocketFactory.openDatagramSocket(),
                    endpoint = InetSocketAddress(config.host, config.port),
                    sessionId = sessionId,
                    sessionKey = config.key,
                    scope = scope,
                    adapter = FullIpProviderTransportAdapter()
                )
                runners[sessionId] = runner
                runner.start()
                api.transition(sessionId, "connected")
                notificationManager().notify(NOTIFICATION_ID, serviceNotification("Sharing Active", "LINKO is sharing your connection securely"))
                Log.i(TAG, "Provider data plane established for session=$sessionId")
                return
            } catch (error: Exception) {
                lastError = error
                if (attempt + 1 < TUNNEL_CONFIG_RETRIES) delay(TUNNEL_CONFIG_RETRY_MS)
            }
        }
        throw lastError ?: IllegalStateException("tunnel_start_failed")
    }

    private fun decline(requestId: String) {
        scope.launch {
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
