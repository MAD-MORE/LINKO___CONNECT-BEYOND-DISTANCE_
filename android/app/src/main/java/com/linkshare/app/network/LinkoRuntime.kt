package com.linkshare.app.network

import android.content.Context
import android.util.Log
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.auth.LinkoStartupCache
import com.linkshare.app.model.Friend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Offline-first startup state machine.
 * Restores cached local state immediately so the app is accessible with zero internet;
 * server state synchronizes asynchronously in the background when connected.
 */
class LinkoRuntime(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private val auth = LinkoAuth(appContext)
    private val cache = LinkoStartupCache(appContext)
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

    val presence by lazy { presenceManager.state }

    /**
     * Resilient startup initialization:
     * 1. If signed in and cached identity exists, enters app immediately without network wait.
     * 2. Background coroutine attempts to synchronize profile, presence, and devices.
     * 3. If offline, the app continues functioning in offline/cached mode.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        initializeMutex.withLock {
            if (!auth.isSignedIn()) return@withLock true

            val currentUserId = auth.currentUserId()
            if (!currentUserId.isNullOrBlank() && initializedUserId == currentUserId) return@withLock true

            // Fast path 1: Usable cached identity
            if (cache.hasUsableIdentity() && cache.userId() == currentUserId) {
                initializedUserId = currentUserId
                safeStartPresence()
                scope.launch(Dispatchers.IO) { synchronize(currentUserId!!) }
                return@withLock true
            }

            // Fast path 2: Stored user ID in Auth preferences (Offline resilient)
            val storedUserId = currentUserId?.takeIf { it.isNotBlank() }
                ?: cache.userId()?.takeIf { it.isNotBlank() }

            if (storedUserId != null) {
                initializedUserId = storedUserId
                safeStartPresence()
                scope.launch(Dispatchers.IO) { synchronize(storedUserId) }
                return@withLock true
            }

            // Network path: Try to refresh / verify session with Supabase
            val session = runCatching { auth.ensureSession() }.getOrNull()
            val resolvedUserId = session?.userId?.takeIf { it.isNotBlank() }
                ?: auth.currentUserId()?.takeIf { it.isNotBlank() }

            if (resolvedUserId != null) {
                initializedUserId = resolvedUserId
                safeStartPresence()
                scope.launch(Dispatchers.IO) { synchronize(resolvedUserId) }
                return@withLock true
            }

            // Fallback path: If still marked signed-in, allow entry in offline mode
            if (auth.isSignedIn()) {
                initializedUserId = "offline_user"
                return@withLock true
            }

            false
        }
    }

    private fun safeStartPresence() {
        runCatching { presenceManager.start() }
            .onFailure { error -> Log.w(TAG, "Presence startup deferred: ${error.message}", error) }
    }

    private suspend fun synchronize(userId: String) {
        runCatching {
            val profile = profileApi.load()
            if (profile.userId == userId) {
                cache.saveIdentity(profile.userId, profile.linkoId, profile.username, profile.displayName)
            }
        }.onFailure { Log.w(TAG, "Profile sync deferred (offline): ${it.message}") }

        runCatching { friendApi.getFriends() }
            .onFailure { Log.w(TAG, "Friends sync deferred (offline): ${it.message}") }

        runCatching {
            val registration = deviceApi.ensureRegistered()
            cache.saveDeviceId(registration.deviceId)
        }.onFailure { Log.w(TAG, "Device registration deferred (offline): ${it.message}") }

        Log.i(TAG, "LINKO synchronization attempted for user=$userId")
    }

    fun start() { scope.launch { initialize() } }
    suspend fun searchFriends(query: String): List<Friend> = friendApi.searchUsers(query)
    suspend fun sendFriendRequest(userId: String): Boolean = friendApi.sendFriendRequest(userId)
    suspend fun getFriends(): List<Friend> = friendApi.getFriends()
    fun connect(providerDeviceId: String, onState: (String) -> Unit = {}) { LinkoEngineBridge.connect(providerDeviceId, onState) }
    fun disconnect() { LinkoEngineBridge.disconnect() }

    fun stop() {
        runCatching { presenceManager.stop() }
        runCatching { disconnect() }
        scope.cancel()
    }

    companion object { private const val TAG = "LINKO_RUNTIME" }
}
