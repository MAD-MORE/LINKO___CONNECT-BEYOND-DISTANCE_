package com.linkshare.app.network

import android.content.Context
import android.util.Log
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.model.Friend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LinkoRuntime(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val auth = LinkoAuth(context)
    private val api by lazy {
        LinkoControlPlaneApi(
            baseUrl = LinkoRuntimeConfig.controlPlaneUrl,
            accessTokenProvider = { auth.currentLinkoToken() },
            deviceIdProvider = { auth.currentDeviceId() },
        )
    }
    private val friendApi by lazy {
        LinkoControlPlaneApi(
            baseUrl = "https://pbnvssbtshvesqwhckfa.supabase.co/functions/v1/linko-friends",
            accessTokenProvider = { auth.currentAccessToken() },
        )
    }
    private val deviceRegistrar by lazy { LinkoDeviceRegistrar(LinkoRuntimeConfig.controlPlaneUrl, auth) }

    fun start() {
        if (!LinkoRuntimeConfig.isConfigured()) { Log.w(TAG, "LINKO control plane is not configured; APK is offline-only"); return }
        scope.launch {
            runCatching {
                if (auth.isSignedIn() && !auth.hasRegisteredDevice()) deviceRegistrar.ensureRegistered()
                api.health()
            }.onSuccess { health -> Log.i(TAG, "LINKO control plane reachable: ${health.optString("status")}; device=${auth.currentDeviceId() ?: "unregistered"}") }
             .onFailure { Log.e(TAG, "LINKO runtime bootstrap failed", it) }
        }
    }

    suspend fun searchFriends(query: String): List<Friend> = friendApi.searchUsers(query)
    suspend fun sendFriendRequest(userId: String): Boolean = friendApi.sendFriendRequest(userId)
    suspend fun getFriends(): List<Friend> = friendApi.getFriends()

    fun stop() = scope.cancel()
    companion object { private const val TAG = "LINKO_RUNTIME" }
}
