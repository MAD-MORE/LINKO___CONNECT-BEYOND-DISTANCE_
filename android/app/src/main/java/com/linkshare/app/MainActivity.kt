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
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApi
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.network.LinkoRuntime
import com.linkshare.app.ui.components.LinkoRealtimeOverlay
import com.linkshare.app.ui.components.LinkoUpdateStatusOverlay
import com.linkshare.app.ui.theme.LinkoTheme
import com.linkshare.app.ui.screens.LinkoApp
import com.linkshare.app.update.LinkoUpdateManager

class MainActivity : ComponentActivity() {
    private lateinit var linkoRuntime: LinkoRuntime
    private lateinit var linkoAuth: LinkoAuth
    private lateinit var updateManager: LinkoUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runCatching {
            linkoAuth = LinkoAuth(this)
            LinkoFriendsApiHolder.api = LinkoFriendsApi { linkoAuth.currentAccessToken() }
            LinkoEngineBridge.configure(this)
            linkoRuntime = LinkoRuntime(this)
            updateManager = LinkoUpdateManager(this)
        }.onFailure { Log.e(TAG, "Core LINKO setup failed", it) }

        setContent {
            LinkoTheme {
                Box(Modifier.fillMaxSize()) {
                    if (::linkoAuth.isInitialized && ::linkoRuntime.isInitialized && ::updateManager.isInitialized) LinkoApp(linkoAuth, linkoRuntime, updateManager)
                    if (::updateManager.isInitialized) {
                        Column(Modifier.fillMaxWidth()) { LinkoUpdateStatusOverlay(updateManager) }
                    }
                    LinkoRealtimeOverlay()
                }
            }
        }

        window.decorView.post { runCatching { requestEnginePermissions() }.onFailure { Log.e(TAG, "Permission setup failed", it) } }
        window.decorView.postDelayed({ checkForUpdates() }, 2500L)
        runCatching { LinkoRealtimeManager.start(this) }.onFailure { Log.e(TAG, "Realtime startup failed", it) }
    }

    override fun onResume() {
        super.onResume()
        runCatching { LinkoRealtimeManager.setForeground(true) }
        if (::updateManager.isInitialized) {
            updateManager.onInstallerReturned()
        }
    }

    override fun onPause() {
        runCatching { LinkoRealtimeManager.setForeground(false) }
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { LinkoRealtimeManager.stop() }
        if (::linkoRuntime.isInitialized) runCatching { linkoRuntime.stop() }
        super.onDestroy()
    }

    private fun checkForUpdates() { runCatching { updateManager.checkAndOfferUpdate() }.onFailure { Log.e(TAG, "Update check failed", it) } }

    private fun requestEnginePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        requestVpnConsentIfNeeded()
        requestBatteryOptimizationExemptionIfNeeded()
    }

    private fun requestBatteryOptimizationExemptionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName) && !isFinishing && !isDestroyed) runCatching { startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = android.net.Uri.parse("package:$packageName") }) }
        }
    }

    private fun requestVpnConsentIfNeeded() {
        val intent: Intent? = VpnService.prepare(this)
        if (intent != null && !isFinishing && !isDestroyed) startActivityForResult(intent, REQUEST_VPN)
    }

    @Deprecated("Kept for Android compatibility; VPN consent is delivered through the activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN && resultCode != Activity.RESULT_OK) Log.i(TAG, "VPN consent declined; LINKO remains usable until a tunnel is requested")
    }

    companion object {
        private const val TAG = "LINKO_MAIN"
        private const val REQUEST_NOTIFICATIONS = 7001
        private const val REQUEST_VPN = 7003
    }
}
