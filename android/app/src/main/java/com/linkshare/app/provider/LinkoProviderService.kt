package com.linkshare.app.provider

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.linkshare.app.MainActivity
import com.linkshare.app.R
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoControlPlaneApi
import com.linkshare.app.network.LinkoRuntimeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LinkoProviderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var auth: LinkoAuth
    private lateinit var api: LinkoControlPlaneApi
    private val seen = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        auth = LinkoAuth(this)
        api = LinkoControlPlaneApi(LinkoRuntimeConfig.controlPlaneUrl, { auth.currentLinkoToken() }, { auth.currentDeviceId() })
        createChannel()
        startForeground(NOTIFICATION_ID, serviceNotification("Provider Ready", "LINKO is listening for connection requests"))
        scope.launch { listen() }
    }

    private suspend fun listen() {
        while (scope.isActive) {
            try {
                if (auth.hasRegisteredDevice()) {
                    api.markPresence()
                    val requests = api.getPendingProviderRequests()
                    requests.forEach { request ->
                        if (seen.add(request.id)) postRequestNotification(request.id, request.receiverDeviceId)
                    }
                }
            } catch (_: Exception) { /* transient network failure; keep listening */ }
            delay(POLL_MS)
        }
    }

    private fun accept(requestId: String) {
        scope.launch {
            runCatching {
                api.transition(requestId, "approved")
                api.transition(requestId, "signaling")
                notificationManager().notify(NOTIFICATION_ID, serviceNotification("LINKO request accepted", "Negotiating a secure connection…"))
            }.onFailure {
                notificationManager().notify(NOTIFICATION_ID, serviceNotification("LINKO request failed", "The connection request could not be authorized"))
            }
        }
    }

    private fun decline(requestId: String) {
        scope.launch {
            runCatching { api.transition(requestId, "denied") }
            notificationManager().notify(NOTIFICATION_ID, serviceNotification("Provider Ready", "The request was declined; LINKO is still listening"))
        }
    }

    private fun postRequestNotification(requestId: String, receiverDeviceId: String) {
        val accept = PendingIntent.getService(this, requestId.hashCode(), actionIntent(ACTION_ACCEPT, requestId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val decline = PendingIntent.getService(this, requestId.hashCode() + 1, actionIntent(ACTION_DECLINE, requestId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, requestId.hashCode() + 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("LINKO connection request")
            .setContentText("A friend wants to use your connection")
            .setStyle(Notification.BigTextStyle().bigText("A device ($receiverDeviceId) wants to use your connection."))
            .setContentIntent(open)
            .setAutoCancel(false)
            .addAction(Notification.Action.Builder(null, "ACCEPT", accept).build())
            .addAction(Notification.Action.Builder(null, "DECLINE", decline).build())
            .build()
        notificationManager().notify(requestId.hashCode(), notification)
    }

    private fun actionIntent(action: String, requestId: String) = Intent(this, LinkoProviderService::class.java).setAction(action).putExtra(EXTRA_REQUEST_ID, requestId)

    private fun serviceNotification(title: String, text: String): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true)
        .build()

    private fun createChannel() { notificationManager().createNotificationChannel(NotificationChannel(CHANNEL_ID, "LINKO Provider", NotificationManager.IMPORTANCE_HIGH)) }
    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACCEPT -> intent.getStringExtra(EXTRA_REQUEST_ID)?.let(::accept)
            ACTION_DECLINE -> intent.getStringExtra(EXTRA_REQUEST_ID)?.let(::decline)
        }
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_ACCEPT = "com.linkshare.app.provider.ACCEPT"
        const val ACTION_DECLINE = "com.linkshare.app.provider.DECLINE"
        const val EXTRA_REQUEST_ID = "request_id"
        private const val CHANNEL_ID = "linko_provider"
        private const val NOTIFICATION_ID = 7001
        private const val POLL_MS = 3_000L

        fun start(context: Context) { context.startForegroundService(Intent(context, LinkoProviderService::class.java)) }
        fun stop(context: Context) { context.stopService(Intent(context, LinkoProviderService::class.java)) }
    }
}
