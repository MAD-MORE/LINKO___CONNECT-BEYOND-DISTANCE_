package com.linkshare.app.network

import android.content.Context
import android.util.Log
import com.linkshare.app.auth.LinkoAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Application-scoped connectivity bootstrap. It does not alter the UI. */
class LinkoRuntime(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val auth = LinkoAuth(context)
    private val api by lazy {
        LinkoControlPlaneApi(
            baseUrl = LinkoRuntimeConfig.controlPlaneUrl,
            accessTokenProvider = { auth.currentLinkoToken() },
        )
    }
    private val deviceRegistrar by lazy {
        LinkoDeviceRegistrar(LinkoRuntimeConfig.controlPlaneUrl, auth)
    }

    fun start() {
        if (!LinkoRuntimeConfig.isConfigured()) {
            Log.w(TAG, "LINKO control plane is not configured; APK is offline-only")
            return
        }
        scope.launch {
            runCatching {
                if (auth.isSignedIn() && !auth.hasRegisteredDevice()) {
                    deviceRegistrar.ensureRegistered()
                }
                api.health()
            }
                .onSuccess { health -> Log.i(TAG, "LINKO control plane reachable: ${health.optString("status")}; device=${auth.currentDeviceId() ?: "unregistered"}") }
                .onFailure { Log.e(TAG, "LINKO runtime bootstrap failed", it) }
        }
    }

    fun stop() = scope.cancel()

    companion object { private const val TAG = "LINKO_RUNTIME" }
}
