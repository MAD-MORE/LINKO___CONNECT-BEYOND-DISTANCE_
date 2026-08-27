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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns the deterministic LINKO startup pipeline and real device presence. */
class LinkoRuntime(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private val auth = LinkoAuth(appContext)
    private val initializeMutex = Mutex()
    private var initializedUserId: String? = null
    private val deviceApi by lazy { LinkoDeviceControlApi(appContext, auth) }
    private val presenceManager by lazy { LinkoPresenceManager(appContext, deviceApi, scope) }

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

    val presence = presenceManager.state

    suspend fun initialize(): Boolean = initializeMutex.withLock {
        if (!auth.isSignedIn()) return@withLock true

        val currentUserId = auth.currentUserId()
        if (!currentUserId.isNullOrBlank() && initializedUserId == currentUserId) return@withLock true

        runCatching {
            // 1. Authentication/session
            val session = auth.ensureSession()
            if (!session.success) error(session.message)
            val userId = auth.currentUserId()?.takeIf { it.isNotBlank() }
                ?: session.userId?.takeIf { it.isNotBlank() }
                ?: error("auth_user_missing")

            // 2. Canonical account identity
            profileApi.load()

            // 3. Warm the real authenticated friends path
            friendApi.getFriends()

            // 4. Register this installation with the real LINKO control plane.
            deviceApi.ensureRegistered()

            // 5. Presence is separate from initialization: only successful heartbeat registration makes ONLINE.
            initializedUserId = userId
            presenceManager.start()
        }.onSuccess {
            Log.i(TAG, "LINKO bootstrap complete: user=${auth.currentUserId()} linkoId=${auth.currentLinkoId()}")
        }.onFailure { error ->
            initializedUserId = null
            presenceManager.stop()
            Log.e(TAG, "LINKO required bootstrap failed", error)
        }.isSuccess
    }

    fun start() { scope.launch { initialize() } }
    suspend fun searchFriends(query: String): List<Friend> = friendApi.searchUsers(query)
    suspend fun sendFriendRequest(userId: String): Boolean = friendApi.sendFriendRequest(userId)
    suspend fun getFriends(): List<Friend> = friendApi.getFriends()
    fun connect(providerDeviceId: String, onState: (String) -> Unit = {}) { LinkoEngineBridge.connect(providerDeviceId, onState) }
    fun disconnect() { LinkoEngineBridge.disconnect() }

    fun stop() {
        presenceManager.stop()
        disconnect()
        scope.cancel()
    }

    companion object { private const val TAG = "LINKO_RUNTIME" }
}
