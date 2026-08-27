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

/** Owns deterministic LINKO startup. Authentication presence is the startup barrier; network readiness is retryable. */
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

    /**
     * Startup barrier = authenticated local session only.
     * Network operations must never make a successful sign-in look like initialization failure.
     */
    suspend fun initialize(): Boolean = initializeMutex.withLock {
        if (!auth.isSignedIn()) return@withLock false

        // The access token is already persisted by sign-in. Do not perform a network call here.
        // A fresh install can therefore enter the app even when the control plane is unavailable.
        val userId = auth.currentUserId()
        initializedUserId = userId
        launchNetworkSynchronization(userId)
        Log.i(TAG, "LINKO local initialization complete: user=${userId ?: "pending"}")
        true
    }

    /** Network work is independent, retryable, and never controls the startup screen. */
    private fun launchNetworkSynchronization(initialUserId: String?) {
        scope.launch(Dispatchers.IO) {
            // First validate/refresh the session in the background. Invalid credentials are an auth
            // concern; temporary network errors simply leave the device OFFLINE and are retried later.
            val session = runCatching { auth.ensureSession() }.getOrNull()
            if (session?.success == false && session.requiresVerification) {
                Log.w(TAG, "Session could not be refreshed yet: ${session.message}")
            }

            val userId = auth.currentUserId() ?: initialUserId
            if (userId == null) {
                Log.w(TAG, "User identity is not available yet; network synchronization deferred")
            }

            runCatching { profileApi.load() }
                .onFailure { Log.w(TAG, "Profile sync deferred", it) }

            runCatching { deviceApi.ensureRegistered() }
                .onFailure { Log.w(TAG, "Device registration deferred", it) }

            runCatching { friendApi.getFriends() }
                .onFailure { Log.w(TAG, "Friends sync deferred", it) }

            // Presence decides ONLINE/OFFLINE independently of initialization.
            if (initializedUserId == auth.currentUserId() || initializedUserId == null) {
                presenceManager.start()
            }
        }
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
