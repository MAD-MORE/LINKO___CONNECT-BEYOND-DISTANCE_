package com.linkshare.app.provider

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.linkshare.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** LINKO Provider foreground service for approved connection sharing sessions. */
class LinkoProviderService : Service() {
    private val TAG = "LINKO-Provider"
    private val binder = ProviderServiceBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var tunnelRunner: ProviderTunnelRunner? = null
    private var runnerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
        Log.d(TAG, "Starting provider tunnel for request: $requestId")
        runnerJob?.cancel()
        runnerJob = serviceScope.launch {
            try {
                val runner = ProviderTunnelRunner(applicationContext, requestId ?: "")
                tunnelRunner = runner
                runner.start()
            } catch (e: Exception) {
                Log.e(TAG, "Provider tunnel failed: ${e.message}", e)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LINKO Internet Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "LINKO is sharing internet with a friend"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("LINKO Sharing Active")
        .setContentText("Internet sharing is in progress...")
        .setSmallIcon(R.drawable.ic_launcher)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    fun stopProvider() {
        runnerJob?.cancel()
        tunnelRunner?.close()
        tunnelRunner = null
        stopSelf()
    }

    override fun onDestroy() {
        runnerJob?.cancel()
        tunnelRunner?.close()
        tunnelRunner = null
        super.onDestroy()
    }

    inner class ProviderServiceBinder : Binder() {
        fun getService(): LinkoProviderService = this@LinkoProviderService
    }

    companion object {
        const val ACTION_START = "com.linkshare.app.provider.START"
        const val ACTION_START_APPROVED = "com.linkshare.app.provider.START_APPROVED"
        const val ACTION_STOP = "com.linkshare.app.provider.STOP"
        const val EXTRA_REQUEST_ID = "request_id"
        const val CHANNEL_ID = "linko_provider"
        const val NOTIFICATION_ID = 1001

        /** Compatibility entry point for the provider UI/view-model. */
        fun start(context: Context, requestId: String? = null) {
            val intent = Intent(context, LinkoProviderService::class.java).apply {
                action = ACTION_START
                requestId?.let { putExtra(EXTRA_REQUEST_ID, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        /** Stops the provider foreground service. */
        fun stop(context: Context) {
            context.stopService(Intent(context, LinkoProviderService::class.java))
        }
    }
}
