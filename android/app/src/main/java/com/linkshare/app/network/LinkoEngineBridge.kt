package com.linkshare.app.network

import android.content.Context
import android.content.Intent
import android.util.Log
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.tunnel.TunnelCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Coordinates the control plane and direct-P2P data plane. Supabase never carries Internet traffic. */
object LinkoEngineBridge {
    private const val TAG = "LinkoEngineBridge"
    private const val PRESENCE_POLL_MS = 10_000L
    private const val PRESENCE_STALE_MS = 180_000L

    private var api: LinkoDeviceControlApi? = null
    private var coordinator: TunnelCoordinator? = null
    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var connectionJob: Job? = null
    private var approvedProviderSessionId: String? = null
    private var lastFriendUserId: String? = null
    private val presenceJobs = ConcurrentHashMap<String, Job>()
    private val presenceFlows = ConcurrentHashMap<String, MutableStateFlow<LinkoFriendPresence>>()

    private val _connection = MutableStateFlow(LinkoEngineConnectionState())
    val connection: StateFlow<LinkoEngineConnectionState> = _connection.asStateFlow()

    fun configure(context: Context) {
        val app = context.applicationContext
        appContext = app
        LinkoAuth(app)
        api = LinkoDeviceControlApi(app)
        coordinator = TunnelCoordinator(app)
        scope = CoroutineScope(Dispatchers.IO)
        connectionJob?.cancel()
        connectionJob = null
        presenceJobs.values.forEach { it.cancel() }
        presenceJobs.clear()
        presenceFlows.clear()
        lastFriendUserId = null
        publish("idle", "Ready")
    }

    fun setPeerInfo(displayName: String?, linkoId: String?, isProvider: Boolean = false) {
        _connection.update {
            it.copy(
                peerDisplayName = displayName ?: it.peerDisplayName,
                peerLinkoId = linkoId ?: it.peerLinkoId,
                isProvider = isProvider
            )
        }
    }

    fun updateTrafficStats(bytesIn: Long, bytesOut: Long, latencyMs: Int = 0) {
        _connection.update {
            it.copy(bytesIn = bytesIn, bytesOut = bytesOut, latencyMs = if (latencyMs > 0) latencyMs else it.latencyMs)
        }
    }

    /** Called by the actual VPN/data service; UI must never infer connection success itself. */
    fun reportTunnelState(state: String, detail: String? = null) {
        publish(state, detail)
    }

    /**
     * Engine-level live friend presence watchdog.
     * Realtime remains the fast path; this authenticated backend probe is the authoritative
     * recovery path so a temporary Realtime gap does not make an actually-online provider
     * appear offline in the UI or block the existing connection flow.
     */
    fun watchFriendPresence(friendUserId: String): StateFlow<LinkoFriendPresence> {
        val userId = friendUserId.trim()
        if (userId.isBlank()) return MutableStateFlow(LinkoFriendPresence())

        val state = presenceFlows.getOrPut(userId) { MutableStateFlow(LinkoFriendPresence()) }
        if (presenceJobs[userId]?.isActive != true) {
            val engineScope = scope
            if (engineScope != null) {
                presenceJobs[userId] = engineScope.launch {
                    while (true) {
                        val control = api ?: break
                        try {
                            val provider = control.providerDeviceForUser(userId)
                            val now = System.currentTimeMillis()
                            val heartbeatAge = if (provider.lastSeenAt > 0L) (now - provider.lastSeenAt).coerceAtLeast(0L) else Long.MAX_VALUE
                            val online = provider.online && heartbeatAge <= PRESENCE_STALE_MS
                            state.update {
                                it.copy(
                                    online = online,
                                    deviceId = provider.deviceId,
                                    lastSeenAt = provider.lastSeenAt,
                                    checkedAt = now,
                                    source = PresenceSource.BackendHeartbeat,
                                    error = null,
                                )
                            }
                            _events.tryEmit(LinkoEngineEvent.FriendPresenceUpdated(userId, online, provider.lastSeenAt))
                        } catch (error: Exception) {
                            state.update {
                                it.copy(
                                    checkedAt = System.currentTimeMillis(),
                                    source = PresenceSource.Unavailable,
                                    error = error.message ?: "presence_check_failed",
                                )
                            }
                        }
                        delay(PRESENCE_POLL_MS)
                    }
                }
            }
        }
        return state.asStateFlow()
    }

    fun stopWatchingFriendPresence(friendUserId: String) {
        val userId = friendUserId.trim()
        presenceJobs.remove(userId)?.cancel()
        presenceFlows.remove(userId)
    }

    fun connectToFriend(friendUserId: String, friendName: String? = null, friendId: String? = null, onState: (String) -> Unit = {}) {
        val control = api ?: return publishAndNotify("engine_not_initialized", onState)
        if (friendUserId.isBlank()) return publishAndNotify("friend_not_selected", onState)
        lastFriendUserId = friendUserId
        setPeerInfo(friendName, friendId, isProvider = false)
        watchFriendPresence(friendUserId)
        connectionJob?.cancel()
        val engineScope = scope ?: return publishAndNotify("engine_scope_unavailable", onState)
        connectionJob = engineScope.launch {
            try {
                publishAndNotify("connecting", onState)
                control.ensureRegistered()
                publishAndNotify("resolving_provider", onState)
                val provider = control.providerDeviceForUser(friendUserId)
                if (!provider.online) throw LinkoNetworkException("provider_offline")
                publishAndNotify("provider_ready", onState)
                val session = control.requestSession(provider.deviceId)
                _connection.update { it.copy(sessionId = session.id) }
                publishAndNotify("requesting", onState)
                awaitApprovedSession(control, session.id, onState)
                establish(session.id, onState)
            } catch (e: Exception) {
                publishAndNotify(e.message ?: "connection_failed", onState)
            }
        }
    }

    fun reconnect(onState: (String) -> Unit = {}) {
        val friendUserId = lastFriendUserId
        if (friendUserId.isNullOrBlank()) return publishAndNotify("friend_not_available_for_reconnect", onState)
        publishAndNotify("reconnecting", onState)
        connectToFriend(friendUserId, _connection.value.peerDisplayName, _connection.value.peerLinkoId, onState)
    }

    private suspend fun awaitApprovedSession(control: LinkoDeviceControlApi, sessionId: String, onState: (String) -> Unit) {
        repeat(60) { attempt ->
            when (val current = control.session(sessionId).state) {
                "approved", "signaling", "connected" -> return
                "denied" -> throw LinkoNetworkException("connection_request_denied")
                "expired" -> throw LinkoNetworkException("connection_request_expired")
                "revoked" -> throw LinkoNetworkException("connection_request_revoked")
                else -> {
                    if (attempt > 0 && attempt % 5 == 0) publishAndNotify("waiting_for_provider", onState)
                    delay(1_000L)
                }
            }
        }
        throw LinkoNetworkException("provider_approval_timeout")
    }

    private suspend fun establish(sessionId: String, onState: (String) -> Unit) {
        val control = api ?: throw LinkoNetworkException("engine_not_initialized")
        val tunnel = coordinator ?: throw LinkoNetworkException("engine_not_initialized")
        publishAndNotify("authenticating", onState)
        val sessionAuth = LinkoAuth.current()?.ensureSession()
        if (sessionAuth != null && !sessionAuth.success) throw LinkoNetworkException(sessionAuth.message)
        control.transition(sessionId, "signaling")
        publishAndNotify("signaling", onState)

        for (attempt in 0 until 40) {
            val current = control.session(sessionId)
            if (current.state == "denied" || current.state == "expired" || current.state == "revoked") {
                throw LinkoNetworkException("session_${current.state}")
            }
            val config = runCatching { control.tunnelConfig(sessionId) }.getOrNull()
            if (config != null) {
                if (config.relay) throw LinkoNetworkException("relay_disabled")
                publishAndNotify("establishing", onState)
                publishAndNotify("direct_connecting", onState)
                tunnel.startDirectVpnTunnel(config.sessionId, config.key)
                publishAndNotify("routing", onState)
                return
            }
            if (attempt > 0) publishAndNotify("signaling_retry", onState)
            delay(1_000L)
        }
        throw LinkoNetworkException("tunnel_setup_timeout")
    }

    fun connect(providerDeviceId: String, onState: (String) -> Unit = {}) {
        connectionJob?.cancel()
        val engineScope = scope ?: return publishAndNotify("engine_scope_unavailable", onState)
        connectionJob = engineScope.launch {
            try {
                val control = api ?: throw LinkoNetworkException("engine_not_initialized")
                control.ensureRegistered()
                val session = control.requestSession(providerDeviceId)
                _connection.update { it.copy(sessionId = session.id) }
                awaitApprovedSession(control, session.id, onState)
                establish(session.id, onState)
            } catch (e: Exception) {
                publishAndNotify(e.message ?: "connection_failed", onState)
            }
        }
    }

    suspend fun getPendingProviderRequests(): List<ProviderRequest> = runCatching { api?.pendingProviderRequests() }.getOrNull() ?: emptyList()

    fun approvePendingProviderRequest(peerName: String? = null, peerId: String? = null, onState: (String) -> Unit = {}) {
        val context = appContext ?: return onState("engine_not_initialized")
        context.startForegroundService(Intent(context, LinkoProviderService::class.java))
        setPeerInfo(peerName ?: "LINKO Friend", peerId, isProvider = true)
        scope?.launch {
            runCatching { api?.pendingProviderRequests()?.firstOrNull() }
                .onSuccess { request ->
                    if (request == null) onState("no_pending_request")
                    else runCatching { api?.transition(request.id, "approved") }
                        .onSuccess { approvedProviderSessionId = request.id; _connection.update { it.copy(sessionId = request.id) }; onState("approved") }
                        .onFailure { onState(it.message ?: "approval_failed") }
                }
                .onFailure { onState(it.message ?: "request_lookup_failed") }
        } ?: onState("engine_scope_unavailable")
    }

    fun startApprovedProviderSession(onState: (String) -> Unit = {}) {
        val context = appContext ?: return onState("engine_not_initialized")
        val sessionId = approvedProviderSessionId ?: return onState("no_approved_session")
        context.startForegroundService(Intent(context, LinkoProviderService::class.java).setAction(LinkoProviderService.ACTION_START_APPROVED).putExtra(LinkoProviderService.EXTRA_REQUEST_ID, sessionId))
        approvedProviderSessionId = null
        onState("starting")
    }

    fun denyPendingProviderRequest(onState: (String) -> Unit = {}) {
        val context = appContext ?: return onState("engine_not_initialized")
        context.startForegroundService(Intent(context, LinkoProviderService::class.java))
        scope?.launch {
            runCatching { api?.pendingProviderRequests()?.firstOrNull() }
                .onSuccess { request ->
                    if (request == null) onState("no_pending_request")
                    else runCatching { api?.transition(request.id, "denied") }
                        .onSuccess { onState("denied") }
                        .onFailure { onState(it.message ?: "decline_failed") }
                }
                .onFailure { onState(it.message ?: "request_lookup_failed") }
        } ?: onState("engine_scope_unavailable")
    }

    fun disconnect() {
        connectionJob?.cancel()
        coordinator?.stopVpnTunnel()
        connectionJob = null
        lastFriendUserId?.let(::stopWatchingFriendPresence)
        lastFriendUserId = null
        publish("idle", "Disconnected · tunnel closed")
    }

    private fun publishAndNotify(state: String, onState: (String) -> Unit) { publish(state); onState(state) }

    private fun publish(state: String, detail: String? = null) {
        val normalized = when (state) {
            "connecting", "reconnecting", "waiting_for_provider" -> LinkoConnectionPhase.Connecting
            "authenticating" -> LinkoConnectionPhase.Authenticating
            "resolving_provider", "provider_ready", "requesting", "signaling", "signaling_retry" -> LinkoConnectionPhase.Signaling
            "establishing", "direct_connecting" -> LinkoConnectionPhase.Establishing
            "securing" -> LinkoConnectionPhase.Securing
            "routing" -> LinkoConnectionPhase.Routing
            "direct_established", "connected" -> LinkoConnectionPhase.Connected
            "idle", "stopped" -> LinkoConnectionPhase.Idle
            else -> LinkoConnectionPhase.Failed
        }
        val message = detail ?: when (state) {
            "connecting" -> "Connecting…"
            "reconnecting" -> "Recovering the connection…"
            "waiting_for_provider" -> "Waiting for provider approval…"
            "authenticating" -> "Authenticating your LINKO session…"
            "resolving_provider" -> "Finding your friend's available device…"
            "provider_ready" -> "Provider found. Preparing a secure request…"
            "requesting" -> "Waiting for provider approval…"
            "signaling" -> "Exchanging direct connection information…"
            "signaling_retry" -> "Negotiating… retrying"
            "establishing", "direct_connecting" -> "Establishing a direct peer connection…"
            "securing" -> "Securing encrypted transport…"
            "routing" -> "Routing traffic through the direct tunnel…"
            "direct_established" -> "Direct encrypted tunnel established"
            "connected" -> "Internet sharing verified"
            "stopped" -> "Direct tunnel stopped"
            else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        _connection.update { it.copy(phase = normalized, detail = message, error = if (normalized == LinkoConnectionPhase.Failed) message else null) }
    }

    private val _events = MutableStateFlow<LinkoEngineEvent?>(null)
    val engineEvents: StateFlow<LinkoEngineEvent?> = _events.asStateFlow()
}

enum class LinkoConnectionPhase { Idle, Connecting, Authenticating, Signaling, Establishing, Securing, Routing, Connected, Failed }

data class LinkoEngineConnectionState(
    val phase: LinkoConnectionPhase = LinkoConnectionPhase.Idle,
    val detail: String = "Ready",
    val error: String? = null,
    val peerDisplayName: String? = null,
    val peerLinkoId: String? = null,
    val sessionId: String? = null,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val latencyMs: Int = 0,
    val isProvider: Boolean = false,
)

enum class PresenceSource { Unknown, BackendHeartbeat, Unavailable }

data class LinkoFriendPresence(
    val online: Boolean = false,
    val deviceId: String? = null,
    val lastSeenAt: Long = 0L,
    val checkedAt: Long = 0L,
    val source: PresenceSource = PresenceSource.Unknown,
    val error: String? = null,
)

sealed interface LinkoEngineEvent {
    data class FriendPresenceUpdated(
        val friendUserId: String,
        val online: Boolean,
        val lastSeenAt: Long,
    ) : LinkoEngineEvent
}
