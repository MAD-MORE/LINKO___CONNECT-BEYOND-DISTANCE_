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

/** Cache-first startup state machine. Local state makes the UI resilient; server state remains canonical. */
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

    val presence = presenceManager.state

    suspend fun initialize(): Boolean = initializeMutex.withLock {
        if (!auth.isSignedIn()) return@withLock true

        val currentUserId = auth.currentUserId()
        if (!currentUserId.isNullOrBlank() && initializedUserId == currentUserId) return@withLock true

        // Cache hit: make the application ready immediately and synchronize in the background.
        if (cache.hasUsableIdentity() && cache.userId() == currentUserId) {
            initializedUserId = currentUserId
            presenceManager.start()
            scope.launch(Dispatchers.IO) { synchronize(currentUserId!!) }
            return@withLock true
        }

        // Session refresh is best-effort. A temporary network failure must not block the UI
        // while a still-valid access token exists.
        val session = runCatching { auth.ensureSession() }.getOrNull()
        if (session?.success == false && !auth.isSignedIn()) {
            Log.e(TAG, "LINKO authentication is no longer valid: ${session.message}")
            return@withLock false
        }

        val userId = auth.currentUserId()?.takeIf { it.isNotBlank() }
            ?: session?.userId?.takeIf { it.isNotBlank() }
            ?: return@withLock false

        // First run is also non-blocking: create/use the cache as soon as canonical data arrives.
        initializedUserId = userId
        presenceManager.start()
        scope.launch(Dispatchers.IO) { synchronize(userId) }
        true
    }

    private suspend fun synchronize(userId: String) {
        runCatching {
            val profile = profileApi.load()
            cache.saveIdentity(profile.userId, profile.linkoId, profile.username, profile.displayName)
        }.onFailure { Log.w(TAG, "Profile sync deferred: ${it.message}") }

        runCatching { friendApi.getFriends() }
            .onFailure { Log.w(TAG, "Friends sync deferred: ${it.message}") }

        runCatching {
            val registration = deviceApi.ensureRegistered()
            cache.saveDeviceId(registration.deviceId)
        }.onFailure { Log.w(TAG, "Device registration deferred: ${it.message}") }

        Log.i(TAG, "LINKO background synchronization complete for user=$userId")
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
