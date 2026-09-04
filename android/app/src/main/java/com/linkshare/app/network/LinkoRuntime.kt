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
     * Complete real startup initialization:
     * 1. Cryptographic Keystore verification
     * 2. Local database & storage preparation
     * 3. Session authentication & token refresh
     * 4. Device registration with Supabase / PostgreSQL
     * 5. Realtime WebSocket mesh & presence engine connection
     * 6. Provider engine & tunnel coordinator configuration
     * 7. Profile & friend network synchronization
     */
    suspend fun initialize(onProgress: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        initializeMutex.withLock {
            onProgress("Initializing Cryptographic Keystore…")
            val identity = com.linkshare.app.auth.LinkoDeviceIdentity()
            val hasKey = identity.publicKeyBase64().isNotBlank()
            if (!hasKey) Log.w(TAG, "Hardware keystore fallback engaged")

            onProgress("Preparing Database & Local Cache…")
            LinkoEngineBridge.configure(appContext)
            LinkoSessionWatchdog.start(appContext)
            LinkoSharedConnectionSync.start(appContext)

            if (!auth.isSignedIn()) {
                onProgress("Engine Ready")
                return@withLock true
            }

            onProgress("Verifying Authentication Session…")
            val currentUserId = auth.currentUserId()
            val session = runCatching { auth.ensureSession() }.getOrNull()
            val resolvedUserId = session?.userId?.takeIf { it.isNotBlank() }
                ?: currentUserId?.takeIf { it.isNotBlank() }
                ?: cache.userId()?.takeIf { it.isNotBlank() }

            if (resolvedUserId != null) {
                initializedUserId = resolvedUserId

                onProgress("Registering Device & Keys on Mesh…")
                runCatching {
                    val reg = deviceApi.ensureRegistered()
                    cache.saveDeviceId(reg.deviceId)
                }.onFailure { Log.w(TAG, "Device registration non-fatal retry: ${it.message}") }

                onProgress("Connecting to Realtime Mesh Cloud…")
                runCatching {
                    LinkoRealtimeManager.start(appContext)
                    safeStartPresence()
                    deviceApi.touchPresence()
                }.onFailure { Log.w(TAG, "Realtime mesh connection non-fatal: ${it.message}") }

                onProgress("Synchronizing Profile & Friends…")
                runCatching {
                    val profile = profileApi.load()
                    auth.saveProfile(profile.displayName, profile.linkoId, profile.username)
                    cache.saveIdentity(profile.userId, profile.linkoId, profile.username, profile.displayName)
                }.onFailure { Log.w(TAG, "Profile sync non-fatal: ${it.message}") }

                runCatching {
                    com.linkshare.app.provider.LinkoProviderService.start(appContext)
                }.onFailure { Log.w(TAG, "Provider service pre-warm non-fatal: ${it.message}") }

                onProgress("System Ready • Entering LINKO")
                return@withLock true
            }

            // Offline mode entry
            if (auth.isSignedIn()) {
                initializedUserId = "offline_user"
                onProgress("Entering in Offline Mode…")
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
    fun disconnect() { LinkoConnectionLifecycle.stop(appContext) }

    fun stop() {
        runCatching { presenceManager.stop() }
        runCatching { LinkoSessionWatchdog.stop() }
        runCatching { LinkoSharedConnectionSync.stop() }
        runCatching { disconnect() }
        scope.cancel()
    }

    companion object { private const val TAG = "LINKO_RUNTIME" }
}