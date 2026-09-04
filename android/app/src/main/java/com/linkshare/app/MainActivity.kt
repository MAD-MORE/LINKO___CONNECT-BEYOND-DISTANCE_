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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApi
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoNotificationCenter
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.network.LinkoRuntime
import com.linkshare.app.ui.components.LinkoNetworkHealthBanner
import com.linkshare.app.ui.components.LinkoRealtimeOverlay
import com.linkshare.app.ui.components.LinkoUpdateStatusOverlay
import com.linkshare.app.ui.screens.LinkoApp
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.LinkoTheme
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.update.LinkoUpdateManager
import kotlinx.coroutines.delay

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

        setContent {
            LinkoTheme {
                Box(Modifier.fillMaxSize()) {
                    LinkoNetworkHealthBanner()
                    if (::updateManager.isInitialized) {
                        val updateState by updateManager.state.collectAsStateWithLifecycle()
                        if (!appUnlocked) {
                            StartupUpdateGate(updateManager, updateState)
                        } else if (::linkoAuth.isInitialized && ::linkoRuntime.isInitialized) {
                            LinkoApp(linkoAuth, linkoRuntime, updateManager)
                            LinkoRealtimeOverlay()
                            Column(Modifier.fillMaxWidth()) { LinkoUpdateStatusOverlay(updateManager) }
                        }
                    }
                }
            }
        }

        window.decorView.post { checkForStartupUpdate() }
    }

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized && !appUnlocked) {
            updateManager.onInstallerReturned()
            checkForStartupUpdate()
        }
        if (appUnlocked) runCatching {
            LinkoRealtimeManager.setForeground(true)
            LinkoNotificationCenter.start(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("EXTRA_REQUEST_ID")?.let { requestId ->
            Log.i(TAG, "Opened via connection notification with request ID: $requestId")
        }
        intent.getStringExtra("EXTRA_NOTIFICATION_REQUEST_ID")?.let { requestId ->
            Log.i(TAG, "Opened via friend-request notification with request ID: $requestId")
        }
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
        runCatching { linkoRuntime.start() }
            .onFailure { Log.e(TAG, "LINKO runtime startup failed", it) }
        runCatching { requestEnginePermissions() }
            .onFailure { Log.e(TAG, "Permission setup failed", it) }
        runCatching {
            LinkoRealtimeManager.start(this)
            LinkoNotificationCenter.start(this)
        }.onFailure { Log.e(TAG, "Realtime/notification startup failed", it) }
    }

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

    @Composable
    private fun StartupUpdateGate(manager: LinkoUpdateManager, state: LinkoUpdateManager.UpdateState) {
        LaunchedEffect(state.status, state.latestVersionCode, state.installedVersionCode) {
            when (state.status) {
                LinkoUpdateManager.UpdateStatus.UpdateAvailable -> manager.startUpdate()
                LinkoUpdateManager.UpdateStatus.UpToDate,
                LinkoUpdateManager.UpdateStatus.Installed -> { delay(350L); unlockApp() }
                LinkoUpdateManager.UpdateStatus.Error,
                LinkoUpdateManager.UpdateStatus.RateLimited -> {
                    if (state.latestVersionCode == null || state.latestVersionCode <= state.installedVersionCode) { delay(250L); unlockApp() }
                }
                else -> Unit
            }
        }

        val isDownloading = state.status == LinkoUpdateManager.UpdateStatus.Downloading
        val isWorking = state.status == LinkoUpdateManager.UpdateStatus.Checking || state.status == LinkoUpdateManager.UpdateStatus.Verifying || state.status == LinkoUpdateManager.UpdateStatus.Installing
        val progress = (state.progressPercent.coerceIn(0, 100) / 100f)
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("LINKO", color = Blue, fontFamily = JetBrainsMono, fontSize = 30.sp)
            Spacer(Modifier.height(12.dp))
            Text("Starting LINKO", color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            if (isDownloading) {
                Text("${state.progressPercent.coerceIn(0, 100)}%", color = Blue, fontFamily = JetBrainsMono, fontSize = 32.sp)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                if (state.totalBytes > 0) Text("${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}", color = TextMuted, fontFamily = JetBrainsMono, fontSize = 10.sp)
            } else if (isWorking) CircularProgressIndicator(color = Blue) else CircularProgressIndicator(color = Blue)
            Spacer(Modifier.height(18.dp))
            Text(state.statusMessage.ifBlank { "Checking for updates…" }, color = TextPrimary, fontFamily = JetBrainsMono, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            when (state.status) {
                LinkoUpdateManager.UpdateStatus.Downloading,
                LinkoUpdateManager.UpdateStatus.Verifying,
                LinkoUpdateManager.UpdateStatus.Installing,
                LinkoUpdateManager.UpdateStatus.UpdateAvailable -> Text("Updating LINKO…", color = Blue, fontFamily = JetBrainsMono, fontSize = 10.sp)
                LinkoUpdateManager.UpdateStatus.UpToDate,
                LinkoUpdateManager.UpdateStatus.Installed -> Text("Ready", color = Green, fontFamily = JetBrainsMono, fontSize = 10.sp)
                LinkoUpdateManager.UpdateStatus.Error,
                LinkoUpdateManager.UpdateStatus.RateLimited -> Text(state.errorMessage.orEmpty(), color = Red, fontFamily = JetBrainsMono, fontSize = 10.sp)
                else -> Text("Getting things ready…", color = TextMuted, fontFamily = JetBrainsMono, fontSize = 10.sp)
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024L
        if (kb < 1024L) return "$kb KB"
        return "${kb / 1024L} MB"
    }

    companion object {
        private const val TAG = "LINKO_MAIN"
        private const val REQUEST_NOTIFICATIONS = 7001
        private const val REQUEST_VPN = 7003
    }
}
