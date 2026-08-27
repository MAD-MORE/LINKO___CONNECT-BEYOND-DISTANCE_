package com.linkshare.app.network

import android.content.Context
import android.util.Log
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.model.Friend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single owner of LINKO startup/runtime lifecycle.
 *
 * Startup is deliberately linear and cache-first:
 * authenticate -> restore local state -> start local runtime -> READY -> synchronize server state.
 * A network outage must not destroy a valid local session or prevent a previously initialized
 * account from reopening the app.
 */
class LinkoRuntime(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private val auth = LinkoAuth(appContext)
    private val localCache = LinkoLocalCache(appContext)
    private val initializeMutex = Mutex()
    private var initializedUserId: String? = null

    private val _startupState = MutableStateFlow(LinkoStartupState.NOT_STARTED)
    val startupState: StateFlow<LinkoStartupState> = _startupState.asStateFlow()

    private val friendApi by lazy {
        LinkoControlPlaneApi(
            baseUrl = "${com.linkshare.app.BuildConfig.LINKO_SUPABASE_URL}/functions/v1/linko-friends",
            accessTokenProvider = { auth.currentAccessToken() },
            deviceIdProvider = { auth.currentDeviceId() },
        )
    }

    private val profileApi by lazy {
        LinkoProfileApi(
            accessTokenProvider = { auth.currentAccessToken() },
            userIdProvider = { auth.currentUserId() },
            refreshProvider = { auth.refreshSession().success },
        )
    }

    suspend fun initialize(): Boolean = initializeMutex.withLock {
        if (!auth.isSignedIn()) {
            initializedUserId = null
            LinkoRealtimeManager.stop()
            _startupState.value = LinkoStartupState.NOT_STARTED
            return@withLock false
        }

        // The access token remains the authentication authority. The cached user id is only
        // restored as local account context so cached profile/friends can survive process death.
        var currentUserId = auth.currentUserId()?.takeIf { it.isNotBlank() }
            ?: localCache.restoreIdentity(auth)?.takeIf { it.isNotBlank() }
        var cachedStateAvailable = localCache.hasUsableCache(currentUserId)

        _startupState.value = LinkoStartupState.RESTORING_SESSION
        val session = auth.ensureSession()
        if (!session.success) {
            val terminalAuthFailure = session.message in setOf(
                "session_required",
                "invalid_refresh_token",
                "refresh_token_not_found",
            )
            if (terminalAuthFailure) {
                auth.signOut()
                initializedUserId = null
                LinkoRealtimeManager.stop()
                _startupState.value = LinkoStartupState.NOT_STARTED
                return@withLock false
            }

            // Temporary network/session-refresh failure is recoverable when a durable cache
            // exists. Do not log the user out or block reopening in this case.
            if (!cachedStateAvailable) {
                _startupState.value = LinkoStartupState.INITIALIZATION_FAILED
                Log.w(TAG, "Session refresh unavailable and no local cache: ${session.message}")
                return@withLock false
            }
        }

        currentUserId = auth.currentUserId()?.takeIf { it.isNotBlank() }
            ?: session.userId?.takeIf { it.isNotBlank() }
            ?: currentUserId
            ?: localCache.restoreIdentity(auth)?.takeIf { it.isNotBlank() }

        if (currentUserId.isNullOrBlank()) {
            _startupState.value = LinkoStartupState.INITIALIZATION_FAILED
            Log.e(TAG, "Authenticated session has no user id")
            return@withLock false
        }

        cachedStateAvailable = localCache.hasUsableCache(currentUserId)
        if (cachedStateAvailable) {
            // Durable restart boundary: restore local account context first and become READY.
            _startupState.value = LinkoStartupState.LOADING_PROFILE
            val profileRestored = localCache.restoreProfile(auth, currentUserId)
            val cachedFriends = localCache.readFriends(currentUserId)
            Log.i(TAG, "Restored local LINKO cache: profile=$profileRestored friends=${cachedFriends.size}")

            _startupState.value = LinkoStartupState.RESTORING_CONNECTION
            startLocalRuntime()
            initializedUserId = currentUserId
            _startupState.value = LinkoStartupState.READY

            // Stale-while-revalidate: server synchronization updates the cache in the background
            // and never takes READY away because the network is temporarily unavailable.
            scope.launch { synchronizeServerState(currentUserId) }
            return@withLock true
        }

        // First sign-in/device with no complete cache: build the durable cache from canonical data.
        var lastError: Throwable? = null
        var attempt = 0
        var initialized = false

        while (attempt < MAX_BOOTSTRAP_ATTEMPTS && !initialized) {
            if (attempt > 0) {
                _startupState.value = LinkoStartupState.RETRYING
                delay(BACKOFF_MS[attempt - 1])
            }

            try {
                if (!auth.isSignedIn()) error("session_required")

                _startupState.value = LinkoStartupState.LOADING_PROFILE
                val profile = profileApi.load()
                localCache.saveProfile(profile)

                _startupState.value = LinkoStartupState.LOADING_FRIENDS
                val friends = friendApi.getFriends()
                localCache.saveFriends(currentUserId, friends)

                _startupState.value = LinkoStartupState.RESTORING_CONNECTION
                startLocalRuntime()

                initializedUserId = currentUserId
                _startupState.value = LinkoStartupState.READY
                initialized = true
                Log.i(TAG, "LINKO first bootstrap complete: user=$currentUserId")
            } catch (error: Throwable) {
                lastError = error
                initializedUserId = null
                Log.w(TAG, "LINKO bootstrap attempt ${attempt + 1} failed", error)

                // A partial bootstrap may have created a usable cache. Switch immediately to
                // the same cache-first recovery path used after process death.
                if (auth.isSignedIn() && localCache.hasUsableCache(currentUserId)) {
                    localCache.restoreProfile(auth, currentUserId)
                    _startupState.value = LinkoStartupState.RESTORING_CONNECTION
                    startLocalRuntime()
                    initializedUserId = currentUserId
                    _startupState.value = LinkoStartupState.READY
                    initialized = true
                    scope.launch { synchronizeServerState(currentUserId) }
                }
            }

            attempt += 1
            if (!auth.isSignedIn()) attempt = MAX_BOOTSTRAP_ATTEMPTS
        }

        if (initialized) {
            true
        } else {
            _startupState.value = LinkoStartupState.INITIALIZATION_FAILED
            Log.e(TAG, "LINKO bootstrap failed", lastError)
            false
        }
    }

    /** Synchronize canonical server state after cached startup without blocking READY. */
    private suspend fun synchronizeServerState(userId: String) {
        try {
            if (!auth.isSignedIn() || auth.currentUserId() != userId) return

            val profile = profileApi.load()
            localCache.saveProfile(profile)

            val friends = friendApi.getFriends()
            localCache.saveFriends(userId, friends)

            Log.i(TAG, "LINKO local cache synchronized: user=$userId friends=${friends.size}")
        } catch (error: Throwable) {
            // Keep the last known good local state. The next foreground/reconnect cycle can retry.
            Log.w(TAG, "LINKO cache synchronization deferred", error)
        }
    }

    private fun startLocalRuntime() {
        LinkoEngineBridge.configure(appContext)
        LinkoRealtimeManager.stop()
        runCatching { LinkoRealtimeManager.start(appContext) }
            .onFailure { Log.w(TAG, "Realtime unavailable during local startup", it) }
    }

    fun reset() {
        initializedUserId = null
        LinkoRealtimeManager.stop()
        _startupState.value = LinkoStartupState.NOT_STARTED
    }

    fun start() { scope.launch { initialize() } }
    suspend fun searchFriends(query: String): List<Friend> = friendApi.searchUsers(query)
    suspend fun sendFriendRequest(userId: String): Boolean = friendApi.sendFriendRequest(userId)
    suspend fun getFriends(): List<Friend> = runCatching { friendApi.getFriends() }
        .getOrElse { localCache.readFriends(auth.currentUserId()) }
    fun connect(providerDeviceId: String, onState: (String) -> Unit = {}) = LinkoEngineBridge.connect(providerDeviceId, onState)
    fun disconnect() = LinkoEngineBridge.disconnect()
    fun stop() {
        disconnect()
        LinkoRealtimeManager.stop()
        scope.cancel()
    }

    companion object {
        private const val TAG = "LINKO_RUNTIME"
        private const val MAX_BOOTSTRAP_ATTEMPTS = 3
        private val BACKOFF_MS = longArrayOf(1_000L, 3_000L)
    }
}

enum class LinkoStartupState {
    NOT_STARTED,
    RESTORING_SESSION,
    LOADING_PROFILE,
    LOADING_FRIENDS,
    RESTORING_CONNECTION,
    RETRYING,
    READY,
    INITIALIZATION_FAILED,
}
