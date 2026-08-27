package com.linkshare.app.network

import android.content.Context
import android.util.Log
import com.linkshare.app.model.Friend
import com.linkshare.app.auth.LinkoAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns deterministic LINKO startup. Authentication/local identity is required; network readiness is retryable. */
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
     * Initialization has one strict prerequisite: a valid authenticated account identity.
     * Everything requiring the network is retryable and must never turn a successful sign-in
     * into the fatal "LINKO COULD NOT INITIALIZE" screen.
     */
    suspend fun initialize(): Boolean = initializeMutex.withLock {
        if (!auth.isSignedIn()) return@withLock false

        val session = runCatching { auth.ensureSession() }.getOrElse {
            Log.e(TAG, "Unable to validate auth session", it)
            return@withLock false
        }
        if (!session.success) {
            Log.e(TAG, "Auth session invalid: ${session.message}")
            return@withLock false
        }

        val userId = auth.currentUserId()?.takeIf { it.isNotBlank() }
            ?: session.userId?.takeIf { it.isNotBlank() }
            ?: run {
                Log.e(TAG, "Authenticated session has no user id")
                return@withLock false
            }

        // This is the actual startup barrier. LINKO can now open even if the control plane is offline.
        initializedUserId = userId
        launchNetworkSynchronization(userId)
        Log.i(TAG, "LINKO initialization complete: user=$userId")
        true
    }

    /** Network work is independent, retryable, and never controls the startup screen. */
    private fun launchNetworkSynchronization(userId: String) {
        scope.launch(Dispatchers.IO) {
            runCatching { profileApi.load() }
                .onFailure { Log.w(TAG, "Profile sync deferred", it) }

            runCatching { deviceApi.ensureRegistered() }
                .onFailure { Log.w(TAG, "Device registration deferred", it) }

            runCatching { friendApi.getFriends() }
                .onFailure { Log.w(TAG, "Friends sync deferred", it) }

            // Presence decides ONLINE/OFFLINE independently of initialization.
            if (initializedUserId == userId) presenceManager.start()
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
