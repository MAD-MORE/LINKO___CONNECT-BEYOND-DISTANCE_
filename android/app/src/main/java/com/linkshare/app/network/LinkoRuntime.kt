package com.linkshare.app.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Application-scoped connectivity bootstrap. It does not alter the UI. */
class LinkoRuntime(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val api by lazy {
        LinkoControlPlaneApi(
            baseUrl = LinkoRuntimeConfig.controlPlaneUrl,
            accessTokenProvider = { null },
        )
    }

    fun start() {
        if (!LinkoRuntimeConfig.isConfigured()) {
            Log.w(TAG, "LINKO control plane is not configured; APK is offline-only")
            return
        }
        scope.launch {
            runCatching { api.health() }
                .onSuccess { Log.i(TAG, "LINKO control plane reachable: ${it.optString("status")}") }
                .onFailure { Log.e(TAG, "LINKO control plane unreachable", it) }
        }
    }

    fun stop() = scope.cancel()

    companion object {
        private const val TAG = "LINKO_RUNTIME"
    }
}
