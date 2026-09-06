package com.linkshare.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApi
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoNotificationCenter
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.network.LinkoRuntime
import com.linkshare.app.network.LinkoSessionHistoryStore
import com.linkshare.app.ui.components.LinkoNetworkHealthBanner
import com.linkshare.app.ui.components.LinkoRealtimeOverlay
import com.linkshare.app.ui.components.LinkoUpdateStatusOverlay
import com.linkshare.app.ui.screens.LinkoApp
import com.linkshare.app.ui.theme.LinkoTheme
import com.linkshare.app.update.LinkoUpdateManager

class MainActivity : ComponentActivity() {
    private lateinit var linkoRuntime: LinkoRuntime
    private lateinit var linkoAuth: LinkoAuth
    private lateinit var updateManager: LinkoUpdateManager
    private var appUnlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            linkoAuth = LinkoAuth(this)
            LinkoFriendsApiHolder.api = LinkoFriendsApi { linkoAuth.currentAccessToken() }
            LinkoEngineBridge.configure(this)
            linkoRuntime = LinkoRuntime(this)
            updateManager = LinkoUpdateManager(this)
        }.onFailure { Log.e(TAG, "Core LINKO setup failed", it) }

        if (::updateManager.isInitialized) unlockApp()

        setContent {
            LinkoTheme {
                Box(Modifier.fillMaxSize()) {
                    LinkoNetworkHealthBanner()
                    if (::updateManager.isInitialized && ::linkoAuth.isInitialized && ::linkoRuntime.isInitialized) {
                        LinkoApp(linkoAuth, linkoRuntime, updateManager)
                        LinkoRealtimeOverlay()
                        LinkoUpdateStatusOverlay(updateManager)
                    }
                }
            }
        }

        // Exactly one startup update check; discovery never blocks LINKO.
        window.decorView.post { checkForStartupUpdate() }
    }

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) updateManager.onInstallerReturned()
        if (appUnlocked) runCatching {
            LinkoRealtimeManager.setForeground(true)
            LinkoNotificationCenter.start(this)
            LinkoSessionHistoryStore.start(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("EXTRA_REQUEST_ID")?.let { Log.i(TAG, "Opened via connection notification with request ID: $it") }
        intent.getStringExtra("EXTRA_NOTIFICATION_REQUEST_ID")?.let { Log.i(TAG, "Opened via friend-request notification with request ID: $it") }
    }

    override fun onPause() {
        if (appUnlocked) runCatching { LinkoRealtimeManager.setForeground(false) }
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { LinkoRealtimeManager.stop() }
        runCatching { com.linkshare.app.ui.components.LinkoNetworkHealthMonitor.stop() }
        if (::linkoRuntime.isInitialized) runCatching { linkoRuntime.stop() }
        super.onDestroy()
    }

    private fun checkForStartupUpdate() {
        runCatching { updateManager.checkAndOfferUpdate() }
            .onFailure { Log.e(TAG, "Startup update check failed", it) }
    }

    private fun unlockApp() {
        if (appUnlocked) return
        appUnlocked = true
        runCatching { linkoRuntime.start() }.onFailure { Log.e(TAG, "LINKO runtime startup failed", it) }
        runCatching { requestEnginePermissions() }.onFailure { Log.e(TAG, "Permission setup failed", it) }
        runCatching {
            LinkoRealtimeManager.start(this)
            LinkoNotificationCenter.start(this)
            LinkoSessionHistoryStore.start(this)
        }.onFailure { Log.e(TAG, "Realtime/notification/history startup failed", it) }
    }

    private fun requestEnginePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        requestVpnConsentIfNeeded()
        requestBatteryOptimizationExemptionIfNeeded()
    }

    private fun requestBatteryOptimizationExemptionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName) && !isFinishing && !isDestroyed) {
                runCatching {
                    startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    })
                }
            }
        }
    }

    private fun requestVpnConsentIfNeeded() {
        val intent: Intent? = VpnService.prepare(this)
        if (intent != null && !isFinishing && !isDestroyed) startActivityForResult(intent, REQUEST_VPN)
    }

    @Deprecated("Kept for Android compatibility; VPN consent is delivered through the activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN && resultCode != Activity.RESULT_OK) {
            Log.i(TAG, "VPN consent declined; LINKO remains usable until a tunnel is requested")
        }
    }

    companion object {
        private const val TAG = "LINKO_MAIN"
        private const val REQUEST_NOTIFICATIONS = 7001
        private const val REQUEST_VPN = 7003
    }
}
