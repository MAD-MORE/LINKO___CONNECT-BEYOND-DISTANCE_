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

/**
 * Owns the deterministic LINKO startup pipeline.
 *
 * Startup only blocks on data required to render the authenticated app:
 * session -> profile -> friends. Connection-control registration is deliberately
 * deferred until a connection is requested because it is not required to open Home.
 */
class LinkoRuntime(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private val auth = LinkoAuth(appContext)
    private val friendApi by lazy {
        LinkoControlPlaneApi(
            baseUrl = "${com.linkshare.app.BuildConfig.LINKO_SUPABASE_URL}/functions/v1/linko-friends",
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

    /**
     * Linear bootstrap:
     * 1) restore/refresh the Supabase session
     * 2) load and validate the canonical account profile
     * 3) preload the friends snapshot used immediately by the app
     *
     * No connection RPC, device registration, or control-plane health probe is used here.
     * Those are connection capabilities and must not prevent an authenticated user from
     * entering the app when the control-plane service is unavailable.
     */
    suspend fun initialize(): Boolean {
        if (!auth.isSignedIn()) return true

        return runCatching {
            val session = auth.ensureSession()
            if (!session.success) error(session.message)

            profileApi.load()

            // Warm the authenticated friend/request path before Home is shown.
            friendApi.getFriends()
        }.onSuccess {
            Log.i(
                TAG,
                "LINKO bootstrap complete: user=${auth.currentUserId()} linkoId=${auth.currentLinkoId()}",
            )
        }.onFailure { error ->
            Log.e(TAG, "LINKO required bootstrap failed", error)
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

    companion object {
        private const val TAG = "LINKO_RUNTIME"
    }
}
