package com.linkshare.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApi
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoRuntime
import com.linkshare.app.ui.screens.StartupSplashScreen
import com.linkshare.app.ui.theme.LinkoTheme
import com.linkshare.app.update.LinkoUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LINKO frontend reset point.
 *
 * The previous navigation/dashboard/auth frontend is intentionally no longer
 * mounted from the activity. The existing startup experience is the only UI
 * surface exposed while the new one-page frontend is rebuilt.
 * Core runtime/update initialization is preserved.
 */
class MainActivity : ComponentActivity() {
    private lateinit var linkoAuth: LinkoAuth
    private lateinit var linkoRuntime: LinkoRuntime
    private lateinit var updateManager: LinkoUpdateManager

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statusMessage by mutableStateOf("Initializing Cryptographic Keystore…")
    private var startupFailed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            linkoAuth = LinkoAuth(this)
            LinkoFriendsApiHolder.api = LinkoFriendsApi { linkoAuth.currentAccessToken() }
            LinkoEngineBridge.configure(this)
            linkoRuntime = LinkoRuntime(this)
            updateManager = LinkoUpdateManager(this)
        }.onFailure {
            startupFailed = true
            statusMessage = "LINKO startup initialization failed"
            Log.e(TAG, "Core LINKO setup failed", it)
        }

        setContent {
            LinkoTheme {
                Box(Modifier.fillMaxSize()) {
                    StartupSplashScreen(
                        statusMessage = statusMessage,
                        failed = startupFailed,
                        onRetry = ::startInitialization,
                        onContinueOffline = {
                            startupFailed = false
                            statusMessage = "Offline startup mode"
                        },
                        onSignOut = {
                            startupScope.launch(Dispatchers.IO) {
                                runCatching { linkoAuth.signOut() }
                            }
                        }
                    )
                }
            }
        }

        startInitialization()
    }

    private fun startInitialization() {
        if (!::linkoRuntime.isInitialized) return

        startupFailed = false
        statusMessage = "Initializing Cryptographic Keystore…"

        startupScope.launch {
            val runtimeOk = withContext(Dispatchers.IO) {
                runCatching {
                    linkoRuntime.initialize { message ->
                        runOnUiThread { statusMessage = message }
                    }
                }.getOrDefault(false)
            }

            if (!runtimeOk) {
                startupFailed = true
                statusMessage = "Secure runtime initialization delayed"
                return@launch
            }

            statusMessage = "LINKO secure runtime ready"

            if (::updateManager.isInitialized) {
                withContext(Dispatchers.IO) {
                    runCatching { updateManager.checkAndOfferUpdate() }
                        .onFailure { Log.e(TAG, "Startup update check failed", it) }
                }
            }
        }
    }

    override fun onDestroy() {
        startupScope.cancel()
        runCatching { linkoRuntime.stop() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LINKO_MAIN"
    }
}
