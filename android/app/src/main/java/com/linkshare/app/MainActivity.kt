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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApi
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.network.LinkoRuntime
import com.linkshare.app.ui.components.LinkoRealtimeOverlay
import com.linkshare.app.ui.theme.LinkoTheme
import com.linkshare.app.ui.screens.LinkoApp

class MainActivity : ComponentActivity() {
    private lateinit var linkoRuntime: LinkoRuntime
    private lateinit var linkoAuth: LinkoAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Startup is a fault-isolated pipeline: failure of an optional subsystem
        // must never terminate the Android process or prevent the UI from loading.
        runCatching {
            linkoAuth = LinkoAuth(this)
            LinkoFriendsApiHolder.api = LinkoFriendsApi { linkoAuth.currentAccessToken() }
            LinkoEngineBridge.configure(this)
            linkoRuntime = LinkoRuntime(this)
        }.onFailure { Log.e(TAG, "Core LINKO setup failed", it) }

        setContent {
            LinkoTheme {
                Box(Modifier.fillMaxSize()) {
                    if (::linkoAuth.isInitialized && ::linkoRuntime.isInitialized) {
                        LinkoApp(linkoAuth, linkoRuntime)
                    }
                    LinkoRealtimeOverlay()
                }
            }
        }

        // Permission prompts happen after the first UI frame, so an Android
        // permission/activity transition cannot race Compose initialization.
        window.decorView.post { runCatching { requestEnginePermissions() }
            .onFailure { Log.e(TAG, "Permission setup failed", it) } }

        // Realtime is auxiliary. It may fail independently without making LINKO crash.
        runCatching { LinkoRealtimeManager.start(this) }
            .onFailure { Log.e(TAG, "Realtime startup failed", it) }
    }

    override fun onResume() {
        super.onResume()
        runCatching { LinkoRealtimeManager.setForeground(true) }
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

    private fun requestEnginePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        requestVpnConsentIfNeeded()
    }

    private fun requestVpnConsentIfNeeded() {
        val intent: Intent? = VpnService.prepare(this)
        if (intent != null && !isFinishing && !isDestroyed) {
            startActivityForResult(intent, REQUEST_VPN)
        }
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
