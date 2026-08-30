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

/**
 * LINKO Provider Service.
 *
 * Runs as a foreground service on the Provider device.
 * Maintains the encrypted tunnel to the Receiver and forwards Internet traffic
 * through the Provider's mobile network connection.
 *
 * Lifecycle:
 * 1. Receiver requests connection → Control Plane notifies Provider
 * 2. Provider approves → LinkoProviderService starts
 * 3. Service connects to Relay/Direct endpoint using session credentials
 * 4. Receives encrypted packets from Receiver
 * 5. Forwards packets through Provider's network (mobile data)
 * 6. Returns responses through tunnel back to Receiver
 * 7. User disconnects or timeout → service stops
 */
class LinkoProviderService : Service() {

    private val TAG = "LINKO-Provider"
    private val binder = ProviderServiceBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var tunnelRunner: ProviderTunnelRunner? = null
    private var runnerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LinkoProviderService starting")

        // Create notification channel (required for foreground service on Android 8+)
        createNotificationChannel()

        // Start as foreground service
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
        Log.d(TAG, "Starting provider tunnel for request: $requestId")

        runnerJob?.cancel()
        runnerJob = serviceScope.launch {
            try {
                val runner = ProviderTunnelRunner(applicationContext, requestId ?: "")
                this@LinkoProviderService.tunnelRunner = runner
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
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LINKO Sharing Active")
            .setContentText("Internet sharing is in progress...")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun stopProvider() {
        Log.d(TAG, "Stopping provider service")
        runnerJob?.cancel()
        tunnelRunner?.close()
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "LinkoProviderService destroyed")
        runnerJob?.cancel()
        tunnelRunner?.close()
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
    }
}
