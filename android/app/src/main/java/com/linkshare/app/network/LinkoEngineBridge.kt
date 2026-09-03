package com.linkshare.app.network

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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

/** Coordinates LINKO control plane, realtime session state, and direct-P2P data plane. */
object LinkoEngineBridge {
    private const val TAG = "LINKO_ENGINE"
    private const val PRESENCE_POLL_MS = 10_000L
    private const val PRESENCE_STALE_MS = 180_000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var api: LinkoDeviceControlApi? = null
    private var coordinator: TunnelCoordinator? = null
    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var connectionJob: Job? = null
    private var realtimeJob: Job? = null
    private var lastFriendUserId: String? = null
    private val presenceJobs = ConcurrentHashMap<String, Job>()
    private val presenceFlows = ConcurrentHashMap<String, MutableStateFlow<LinkoFriendPresence>>()
    private val providerStartsInFlight = ConcurrentHashMap.newKeySet<String>()
    private val _connection = MutableStateFlow(LinkoEngineConnectionState())
    val connection: StateFlow<LinkoEngineConnectionState> = _connection.asStateFlow()

    fun configure(context: Context) {
        val app = context.applicationContext
        appContext = app
        LinkoAuth(app)
        api = LinkoDeviceControlApi(app)
        coordinator = TunnelCoordinator(app)
        scope = CoroutineScope(Dispatchers.IO)
        connectionJob?.cancel(); connectionJob = null
        realtimeJob?.cancel(); realtimeJob = null
        presenceJobs.values.forEach { it.cancel() }; presenceJobs.clear(); presenceFlows.clear()
        providerStartsInFlight.clear()
        lastFriendUserId = null
        publish("idle", "Ready")

        realtimeJob = scope?.launch {
            LinkoRealtimeManager.start(app)
            LinkoRealtimeManager.events.collect { event -> handleRealtimeEvent(event) }
        }
    }

    fun setPeerInfo(displayName: String?, linkoId: String?, isProvider: Boolean = false) {
        _connection.update { it.copy(peerDisplayName = displayName ?: it.peerDisplayName, peerLinkoId = linkoId ?: it.peerLinkoId, isProvider = isProvider) }
    }

    fun updateTrafficStats(bytesIn: Long, bytesOut: Long, latencyMs: Int = 0) {
        _connection.update { it.copy(bytesIn = bytesIn, bytesOut = bytesOut, latencyMs = if (latencyMs > 0) latencyMs else it.latencyMs) }
    }

    fun reportTunnelState(state: String, detail: String? = null) = publish(state, detail)

    private suspend fun handleRealtimeEvent(event: LinkoRealtimeEvent) {
        when (event) {
            is LinkoRealtimeEvent.SessionStateChanged -> {
                val sessionId = event.sessionId?.takeIf { it.isNotBlank() } ?: return
                val activeSessionId = _connection.value.sessionId
                if (activeSessionId != sessionId) return
                when (event.state?.trim()?.lowercase()) {
                    "approved" -> publish("requesting", "Request approved; preparing the direct tunnel")
                    "signaling" -> publish("signaling", "Exchanging direct connection information…")
                    "connected" -> publish("connected", if (_connection.value.isProvider) "Direct connection established; Provider is sharing Internet" else "Internet sharing verified")
                    "denied" -> publish("connection_request_denied", "Connection request was declined")
                    "expired" -> publish("connection_request_expired", "Connection request expired")
                    "revoked" -> publish("connection_request_revoked", "Connection was revoked")
                    "failed" -> publish("provider_connection_failed", "Direct connection failed")
                    "disconnected" -> publish("stopped", "Direct peer disconnected")
                }
            }
            is LinkoRealtimeEvent.TransportError -> {
                val active = _connection.value.phase
                if (active != LinkoConnectionPhase.Connected && active != LinkoConnectionPhase.Idle) {
                    publish("realtime_disconnected", event.message)
                }
            }
            is LinkoRealtimeEvent.IncomingConnectionRequest -> Unit
            is LinkoRealtimeEvent.FriendRequestReceived -> Unit
            is LinkoRealtimeEvent.FriendRequestSent -> Unit
            is LinkoRealtimeEvent.FriendRequestAccepted -> Unit
            is LinkoRealtimeEvent.FriendRequestDeclined -> Unit
            is LinkoRealtimeEvent.FriendRemoved -> Unit
            is LinkoRealtimeEvent.PresenceChanged -> Unit
        }
    }

    fun watchFriendPresence(friendUserId: String): StateFlow<LinkoFriendPresence> {
        val userId = friendUserId.trim()
        if (userId.isBlank()) return MutableStateFlow(LinkoFriendPresence())
        val state = presenceFlows.getOrPut(userId) { MutableStateFlow(LinkoFriendPresence()) }
        if (presenceJobs[userId]?.isActive != true) {
            scope?.let { engineScope ->
                presenceJobs[userId] = engineScope.launch {
                    while (true) {
                        try {
                            val provider = (api ?: break).providerDeviceForUser(userId)
                            val now = System.currentTimeMillis()
                            val age = if (provider.lastSeenAt > 0L) (now - provider.lastSeenAt).coerceAtLeast(0L) else Long.MAX_VALUE
                            val online = provider.online && age <= PRESENCE_STALE_MS
                            state.update { it.copy(online = online, deviceId = provider.deviceId, lastSeenAt = provider.lastSeenAt, checkedAt = now, source = PresenceSource.BackendHeartbeat, error = null) }
                            _events.tryEmit(LinkoEngineEvent.FriendPresenceUpdated(userId, online, provider.lastSeenAt))
                        } catch (error: Exception) {
                            state.update { it.copy(checkedAt = System.currentTimeMillis(), source = PresenceSource.Unavailable, error = error.message ?: "presence_check_failed") }
                        }
                        delay(PRESENCE_POLL_MS)
                    }
                }
            }
        }
        return state.asStateFlow()
    }

    fun stopWatchingFriendPresence(friendUserId: String) {
        val userId = friendUserId.trim(); presenceJobs.remove(userId)?.cancel(); presenceFlows.remove(userId)
    }

    fun connectToFriend(friendUserId: String, friendName: String? = null, friendId: String? = null, onState: (String) -> Unit = {}) {
        val control = api ?: return publishAndNotify("engine_not_initialized", onState)
        if (friendUserId.isBlank()) return publishAndNotify("friend_not_selected", onState)
        lastFriendUserId = friendUserId
        setPeerInfo(friendName, friendId, false)
        watchFriendPresence(friendUserId)
        connectionJob?.cancel()
        val engineScope = scope ?: return publishAndNotify("engine_scope_unavailable", onState)
        connectionJob = engineScope.launch {
            try {
                publishAndNotify("connecting", onState)
                control.ensureRegistered()
                publishAndNotify("resolving_provider", onState)
                val provider = control.providerDeviceForUser(friendUserId)
                if (!provider.online || provider.deviceId.isBlank()) throw LinkoNetworkException("provider_offline")
                publishAndNotify("provider_ready", onState)
                val session = control.requestSession(provider.deviceId)
                _connection.update { it.copy(sessionId = session.id) }
                publishAndNotify("requesting", onState)
                awaitApprovedSession(control, session.id, onState)
                establish(session.id, onState)
            } catch (error: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "Connection job cancelled")
                throw error
            } catch (error: Exception) {
                terminateReceiverForFailure(_connection.value.sessionId)
                publishAndNotify(error.message ?: "connection_failed", onState)
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
            when (val state = control.session(sessionId).state) {
                "approved", "signaling", "connected" -> return
                "denied" -> throw LinkoNetworkException("connection_request_denied")
                "failed" -> throw LinkoNetworkException("provider_connection_failed")
                "expired" -> throw LinkoNetworkException("connection_request_expired")
                "revoked" -> throw LinkoNetworkException("connection_request_revoked")
                "disconnected" -> throw LinkoNetworkException("provider_disconnected")
                else -> { if (attempt > 0 && attempt % 5 == 0) publishAndNotify("waiting_for_provider", onState); delay(1_000L) }
            }
        }
        throw LinkoNetworkException("provider_approval_timeout")
    }

    private suspend fun establish(sessionId: String, onState: (String) -> Unit) {
        val control = api ?: throw LinkoNetworkException("engine_not_initialized")
        val tunnel = coordinator ?: throw LinkoNetworkException("engine_not_initialized")
        publishAndNotify("authenticating", onState)
        val authState = LinkoAuth.current()?.ensureSession()
        if (authState != null && !authState.success) throw LinkoNetworkException(authState.message)
        control.transition(sessionId, "signaling")
        publishAndNotify("signaling", onState)

        for (attempt in 0 until 40) {
            val current = control.session(sessionId)
            if (current.state == "failed") throw LinkoNetworkException("provider_connection_failed")
            if (current.state == "denied" || current.state == "expired" || current.state == "revoked" || current.state == "disconnected") throw LinkoNetworkException("session_${current.state}")
            val config = runCatching { control.tunnelConfig(sessionId) }.getOrNull()
            if (config != null) {
                if (config.sessionId != sessionId || config.role != "receiver" || config.transport != "direct_udp") throw LinkoNetworkException("invalid_receiver_tunnel_config")
                publishAndNotify("establishing", onState)
                publishAndNotify("direct_connecting", onState)
                tunnel.startDirectVpnTunnel(config.sessionId, config.key)
                publishAndNotify("routing", onState)
                return
            }
            if (attempt > 0) publishAndNotify("signaling_retry", onState)
            delay(750L)
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
            } catch (error: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "Connection job cancelled")
                throw error
            } catch (error: Exception) { publishAndNotify(error.message ?: "connection_failed", onState) }
        }
    }

    suspend fun getPendingProviderRequests(): List<ProviderRequest> = runCatching { api?.pendingProviderRequests() }.getOrNull() ?: emptyList()

    fun approvePendingProviderRequest(peerName: String? = null, peerId: String? = null, onState: (String) -> Unit = {}) {
        if (appContext == null) return notifyOnMain(onState, "engine_not_initialized")
        setPeerInfo(peerName ?: "LINKO Friend", peerId, true)
        scope?.launch {
            var requestId: String? = null
            try {
                val request = api?.pendingProviderRequests()?.firstOrNull()
                if (request == null) {
                    notifyOnMain(onState, "no_pending_request")
                    return@launch
                }
                requestId = request.id
                if (!providerStartsInFlight.add(request.id)) {
                    Log.i(TAG, "Provider start already in flight session=${request.id}")
                    notifyOnMain(onState, "starting")
                    return@launch
                }
                val transition = runCatching { api?.transition(request.id, "approved") }
                if (transition.isFailure) {
                    providerStartsInFlight.remove(request.id)
                    notifyOnMain(onState, transition.exceptionOrNull()?.message ?: "approval_failed")
                    return@launch
                }
                _connection.update { it.copy(sessionId = request.id, isProvider = true) }
                notifyOnMain(onState, "approved")
                startApprovedProviderSession(request.id, onState)
            } catch (error: kotlinx.coroutines.CancellationException) {
                requestId?.let(providerStartsInFlight::remove)
                throw error
            } catch (error: Throwable) {
                requestId?.let(providerStartsInFlight::remove)
                Log.e(TAG, "ACCEPT_FLOW_CRASH_CONTAINED session=${requestId ?: "unknown"}", error)
                notifyOnMain(onState, "accept_failed:${error.javaClass.simpleName}:${error.message ?: "unknown"}")
            }
        } ?: notifyOnMain(onState, "engine_scope_unavailable")
    }

    private fun startApprovedProviderSession(sessionId: String, onState: (String) -> Unit) {
        val context = appContext
        if (context == null) {
            providerStartsInFlight.remove(sessionId)
            notifyOnMain(onState, "engine_not_initialized")
            return
        }
        if (sessionId.isBlank()) {
            providerStartsInFlight.remove(sessionId)
            notifyOnMain(onState, "invalid_session_id")
            return
        }
        runCatching {
            Log.i(TAG, "PROVIDER_START_REQUEST session=$sessionId")
            context.startForegroundService(Intent(context, LinkoProviderService::class.java).setAction(LinkoProviderService.ACTION_START_APPROVED).putExtra(LinkoProviderService.EXTRA_REQUEST_ID, sessionId))
        }.onSuccess {
            notifyOnMain(onState, "starting")
            Log.i(TAG, "PROVIDER_START_ACCEPTED session=$sessionId")
        }.onFailure { error ->
            providerStartsInFlight.remove(sessionId)
            Log.e(TAG, "PROVIDER_START_REJECTED session=$sessionId", error)
            notifyOnMain(onState, "provider_service_start_failed:${error.javaClass.simpleName}:${error.message ?: "unknown"}")
            scope?.launch { runCatching { api?.transition(sessionId, "failed") } }
        }
    }

    fun markProviderStartFinished(sessionId: String) {
        providerStartsInFlight.remove(sessionId)
    }

    fun denyPendingProviderRequest(onState: (String) -> Unit = {}) {
        if (appContext == null) return notifyOnMain(onState, "engine_not_initialized")
        scope?.launch {
            runCatching { api?.pendingProviderRequests()?.firstOrNull() }
                .onSuccess { request ->
                    if (request == null) notifyOnMain(onState, "no_pending_request")
                    else runCatching { api?.transition(request.id, "denied") }
                        .onSuccess { providerStartsInFlight.remove(request.id); notifyOnMain(onState, "denied") }
                        .onFailure { notifyOnMain(onState, it.message ?: "decline_failed") }
                }
                .onFailure { notifyOnMain(onState, it.message ?: "request_lookup_failed") }
        } ?: notifyOnMain(onState, "engine_scope_unavailable")
    }

    fun disconnect() {
        connectionJob?.cancel()
        val sessionId = _connection.value.sessionId
        coordinator?.stopVpnTunnel()
        connectionJob = null
        lastFriendUserId?.let(::stopWatchingFriendPresence)
        lastFriendUserId = null
        providerStartsInFlight.clear()
        if (!sessionId.isNullOrBlank()) {
            scope?.launch {
                runCatching { api?.transition(sessionId, "disconnected") }
                    .onFailure { Log.w(TAG, "Disconnect state update failed session=$sessionId: ${it.message}") }
            }
        }
        _connection.update { it.copy(sessionId = null, isProvider = false, bytesIn = 0L, bytesOut = 0L, latencyMs = 0, phase = LinkoConnectionPhase.Idle, detail = "Disconnected · tunnel closed", error = null) }
    }

    private fun terminateReceiverForFailure(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        runCatching { coordinator?.stopVpnTunnel() }
        scope?.launch {
            runCatching {
                val state = api?.session(sessionId)?.state
                if (state !in setOf("failed", "denied", "expired", "revoked", "disconnected")) api?.transition(sessionId, "failed")
            }.onFailure { Log.w(TAG, "Failed to publish receiver failure session=$sessionId: ${it.message}") }
        }
    }

    private fun publishAndNotify(state: String, onState: (String) -> Unit) { publish(state); notifyOnMain(onState, state) }

    private fun notifyOnMain(onState: (String) -> Unit, state: String) {
        mainHandler.post {
            runCatching { onState(state) }
                .onFailure { error -> Log.e(TAG, "UI_STATE_CALLBACK_FAILED state=$state", error) }
        }
    }

    private fun publish(state: String, detail: String? = null) {
        val phase = when (state) {
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
            "reconnecting" -> "Recovering the direct connection…"
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
            "connection_request_denied" -> "Connection request was declined"
            "connection_request_expired" -> "Connection request expired"
            "connection_request_revoked" -> "Connection was revoked"
            "provider_connection_failed" -> "Provider could not establish a direct connection"
            "provider_disconnected" -> "Provider disconnected"
            "realtime_disconnected" -> "Realtime control connection interrupted"
            else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        _connection.update { it.copy(phase = phase, detail = message, error = if (phase == LinkoConnectionPhase.Failed) message else null) }
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
    data class FriendPresenceUpdated(val friendUserId: String, val online: Boolean, val lastSeenAt: Long) : LinkoEngineEvent
}
