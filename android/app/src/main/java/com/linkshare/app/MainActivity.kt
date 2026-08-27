package com.linkshare.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
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
        linkoAuth = LinkoAuth(this)
        LinkoFriendsApiHolder.api = LinkoFriendsApi { linkoAuth.currentAccessToken() }
        LinkoEngineBridge.configure(this)
        linkoRuntime = LinkoRuntime(this)
        requestEnginePermissions()
        setContent {
            LinkoTheme {
                Box(Modifier.fillMaxSize()) {
                    LinkoApp(linkoAuth, linkoRuntime)
                    LinkoRealtimeOverlay()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LinkoRealtimeManager.setForeground(true)
    }

    override fun onPause() {
        LinkoRealtimeManager.setForeground(false)
        super.onPause()
    }

    override fun onDestroy() {
        LinkoRealtimeManager.stop()
        linkoRuntime.stop()
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
        if (intent != null) startActivityForResult(intent, REQUEST_VPN)
    }

    @Deprecated("Kept for Android compatibility; VPN consent is delivered through the activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN && resultCode != Activity.RESULT_OK) {
            // User declined. Do not block the app; the engine will request VPN consent again when needed.
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 7001
        private const val REQUEST_VPN = 7003
    }
}
