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

class LinkoRuntime(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private val auth = LinkoAuth(appContext)
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

        val currentUserId = auth.currentUserId()
        if (!currentUserId.isNullOrBlank() && initializedUserId == currentUserId && _startupState.value == LinkoStartupState.READY) {
            return@withLock true
        }

        var lastError: Throwable? = null
        var attempt = 0
        var initialized = false

        while (attempt < MAX_BOOTSTRAP_ATTEMPTS && !initialized) {
            if (attempt > 0) {
                _startupState.value = LinkoStartupState.RETRYING
                delay(BACKOFF_MS[attempt - 1])
            }

            try {
                _startupState.value = LinkoStartupState.RESTORING_SESSION
                val session = auth.ensureSession()
                if (!session.success) {
                    val terminalAuthFailure = session.message in setOf(
                        "session_required",
                        "session_refresh_unavailable",
                        "invalid_refresh_token",
                        "refresh_token_not_found",
                    )
                    if (terminalAuthFailure) auth.signOut()
                    error(session.message)
                }

                val userId = auth.currentUserId()?.takeIf { it.isNotBlank() }
                    ?: session.userId?.takeIf { it.isNotBlank() }
                    ?: error("auth_user_missing")

                _startupState.value = LinkoStartupState.LOADING_PROFILE
                profileApi.load()

                _startupState.value = LinkoStartupState.LOADING_FRIENDS
                friendApi.getFriends()

                _startupState.value = LinkoStartupState.RESTORING_CONNECTION
                LinkoEngineBridge.configure(appContext)
                LinkoRealtimeManager.stop()
                LinkoRealtimeManager.start(appContext)

                initializedUserId = userId
                _startupState.value = LinkoStartupState.READY
                initialized = true
                Log.i(TAG, "LINKO bootstrap complete: user=$userId linkoId=${auth.currentLinkoId()}")
            } catch (error: Throwable) {
                lastError = error
                initializedUserId = null
                Log.w(TAG, "LINKO bootstrap attempt ${attempt + 1} failed", error)
            }

            attempt += 1
            if (!auth.isSignedIn()) {
                attempt = MAX_BOOTSTRAP_ATTEMPTS
            }
        }

        if (initialized) {
            true
        } else {
            _startupState.value = if (auth.isSignedIn()) {
                LinkoStartupState.INITIALIZATION_FAILED
            } else {
                LinkoStartupState.NOT_STARTED
            }
            Log.e(TAG, "LINKO required bootstrap failed", lastError)
            false
        }
    }

    fun reset() {
        initializedUserId = null
        LinkoRealtimeManager.stop()
        _startupState.value = LinkoStartupState.NOT_STARTED
    }

    fun start() { scope.launch { initialize() } }
    suspend fun searchFriends(query: String): List<Friend> = friendApi.searchUsers(query)
    suspend fun sendFriendRequest(userId: String): Boolean = friendApi.sendFriendRequest(userId)
    suspend fun getFriends(): List<Friend> = friendApi.getFriends()
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
