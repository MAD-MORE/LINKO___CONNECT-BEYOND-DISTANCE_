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
    private val appContext = context.applicationContext
    private val auth = LinkoAuth(appContext)
    private val api by lazy {
        LinkoControlPlaneApi(
            baseUrl = LinkoRuntimeConfig.controlPlaneUrl,
            accessTokenProvider = { auth.currentAccessToken() },
            deviceIdProvider = { auth.currentDeviceId() },
        )
    }
    private val friendApi by lazy {
        LinkoControlPlaneApi(
            baseUrl = "https://pbnvssbtshvesqwhckfa.supabase.co/functions/v1/linko-friends",
            accessTokenProvider = { auth.currentAccessToken() },
        )
    }
    private val profileApi by lazy {
        LinkoProfileApi(
            accessTokenProvider = { auth.currentAccessToken() },
            userIdProvider = { auth.currentUserId() },
            refreshProvider = { auth.refreshSession().success },
        )
    }
    private val deviceRegistrar by lazy { LinkoDeviceRegistrar(LinkoRuntimeConfig.controlPlaneUrl, auth) }

    /**
     * Complete the authenticated bootstrap before the main LINKO experience is opened.
     * Profile identity is loaded here so screens never have to guess which username/ID to show.
     */
    suspend fun initialize(): Boolean {
        if (!auth.isSignedIn()) return true
        if (!LinkoRuntimeConfig.isConfigured()) {
            Log.w(TAG, "LINKO control plane is not configured; APK is offline-only")
            return false
        }
        return runCatching {
            auth.ensureSession().also { result ->
                if (!result.success) error(result.message)
            }
            profileApi.load()
            if (!auth.hasRegisteredDevice()) {
                check(deviceRegistrar.ensureRegistered()) { "device_registration_failed" }
            }
            api.health()
        }.onSuccess { health ->
            Log.i(TAG, "LINKO bootstrap complete: ${health.optString("status")}; device=${auth.currentDeviceId()}")
        }.onFailure { error ->
            Log.e(TAG, "LINKO runtime bootstrap failed", error)
        }.isSuccess
    }

    /** Backward-compatible fire-and-forget bootstrap entry point. */
    fun start() {
        scope.launch { initialize() }
    }

    suspend fun searchFriends(query: String): List<Friend> = friendApi.searchUsers(query)
    suspend fun sendFriendRequest(userId: String): Boolean = friendApi.sendFriendRequest(userId)
    suspend fun getFriends(): List<Friend> = friendApi.getFriends()

    fun connect(providerDeviceId: String, onState: (String) -> Unit = {}) {
        LinkoEngineBridge.connect(providerDeviceId, onState)
    }

    fun disconnect() {
        LinkoEngineBridge.disconnect()
    }

    fun stop() {
        disconnect()
        scope.cancel()
    }

    companion object { private const val TAG = "LINKO_RUNTIME" }
}
