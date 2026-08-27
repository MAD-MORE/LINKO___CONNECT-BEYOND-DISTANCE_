package com.linkshare.app.network

import android.content.Context
import android.util.Log
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.data.LinkoAppCache
import com.linkshare.app.model.Friend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Owns deterministic LINKO startup. Authentication presence is the startup barrier; network readiness is retryable. */
class LinkoRuntime(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private val auth = LinkoAuth(appContext)
    private val cache = LinkoAppCache(appContext)
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
     * Cached account data can be used immediately; network operations refresh it independently.
     */
    suspend fun initialize(): Boolean = initializeMutex.withLock {
        if (!auth.isSignedIn()) return@withLock false

        val userId = auth.currentUserId()
        initializedUserId = userId
        // A valid user-scoped cache is local startup data, never a presence signal.
        if (userId != null) {
            cache.profileFor(userId)
            cache.friendsFor(userId)
        }
        launchNetworkSynchronization(userId)
        Log.i(TAG, "LINKO local initialization complete: user=${userId ?: "pending"}, cached=${userId != null}")
        true
    }

    /** Network work is independent, retryable, and never controls the startup screen. */
    private fun launchNetworkSynchronization(initialUserId: String?) {
        scope.launch(Dispatchers.IO) {
            val session = runCatching { auth.ensureSession() }.getOrNull()
            if (session?.success == false && session.requiresVerification) {
                Log.w(TAG, "Session could not be refreshed yet: ${session.message}")
            }

            val userId = auth.currentUserId() ?: initialUserId
            if (userId == null) {
                Log.w(TAG, "User identity is not available yet; network synchronization deferred")
                return@launch
            }

            runCatching { profileApi.load() }
                .onFailure { Log.w(TAG, "Profile sync deferred; cached profile remains available", it) }

            runCatching {
                val friends = friendApi.getFriends()
                cache.saveFriends(userId, friendsToJson(friends))
            }.onFailure { Log.w(TAG, "Friends sync deferred; cached friends remain available", it) }

            runCatching { deviceApi.ensureRegistered() }
                .onFailure { Log.w(TAG, "Device registration deferred", it) }

            // Presence decides ONLINE/OFFLINE independently of initialization.
            if (initializedUserId == auth.currentUserId() || initializedUserId == null) {
                presenceManager.start()
            }
        }
    }

    fun start() { scope.launch { initialize() } }

    suspend fun searchFriends(query: String): List<Friend> = friendApi.searchUsers(query)
    suspend fun sendFriendRequest(userId: String): Boolean = friendApi.sendFriendRequest(userId)

    /** Network-first, cache-fallback. Cache is never treated as evidence that a friend is online. */
    suspend fun getFriends(): List<Friend> {
        val userId = auth.currentUserId()
        return runCatching {
            friendApi.getFriends().also { if (userId != null) cache.saveFriends(userId, friendsToJson(it)) }
        }.getOrElse {
            if (userId == null) emptyList() else friendsFromJson(cache.friendsFor(userId))
        }
    }

    fun connect(providerDeviceId: String, onState: (String) -> Unit = {}) { LinkoEngineBridge.connect(providerDeviceId, onState) }
    fun disconnect() { LinkoEngineBridge.disconnect() }

    private fun friendsToJson(friends: List<Friend>): JSONArray = JSONArray().apply {
        friends.forEach { friend ->
            put(JSONObject()
                .put("user_id", friend.id)
                .put("display_name", friend.name)
                .put("initials", friend.initials)
                .put("linko_id", friend.cityHint)
                .put("trust_note", friend.trustNote)
                .put("is_sharing", friend.isSharing)
                .put("accent_hex", friend.accentHex))
        }
    }

    private fun friendsFromJson(array: JSONArray?): List<Friend> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(Friend(
                    id = item.optString("user_id"),
                    name = item.optString("display_name", "LINKO User"),
                    initials = item.optString("initials", "L"),
                    cityHint = item.optString("linko_id"),
                    trustNote = item.optString("trust_note", "CACHED LINKO USER"),
                    isSharing = item.optBoolean("is_sharing", false),
                    accentHex = item.optLong("accent_hex", 0xFF4C8DFF),
                ))
            }
        }
    }

    fun stop() {
        presenceManager.stop()
        disconnect()
        scope.cancel()
    }

    companion object { private const val TAG = "LINKO_RUNTIME" }
}
