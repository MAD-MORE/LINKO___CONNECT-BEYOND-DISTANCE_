package com.linkshare.app.network

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.tunnel.TunnelCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Coordinates LINKO control plane, realtime session state, and direct-P2P data plane. */
object LinkoEngineBridge {
    private const val TAG = "LINKO_ENGINE"
    private const val PRESENCE_POLL_MS = 10_000L
    private const val PRESENCE_STALE_MS = 180_000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val operationGeneration = AtomicLong(0L)
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
        cancelActiveWork(stopServices = true)
        appContext = app
        LinkoAuth(app)
        api = LinkoDeviceControlApi(app)
        coordinator = TunnelCoordinator(app)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        presenceFlows.clear()
        providerStartsInFlight.clear()
        lastFriendUserId = null
        _connection.value = LinkoEngineConnectionState()
        publish("idle", "Ready")

        realtimeJob = scope?.launch {
            runCatching {
                LinkoRealtimeManager.start(app)
                LinkoRealtimeManager.events.collect { event -> handleRealtimeEvent(event) }
            }.onFailure { error ->
                Log.e(TAG, "REALTIME_ENGINE_FAILED", error)
                if (operationGeneration.get() >= 0L) publish("realtime_disconnected", error.message)
            }
        }
    }

    fun setPeerInfo(displayName: String?, linkoId: String?, isProvider: Boolean = false) {
        _connection.update {
            it.copy(
                peerDisplayName = displayName ?: it.peerDisplayName,
                peerLinkoId = linkoId ?: it.peerLinkoId,
                isProvider = isProvider,
            )
        }
    }

    fun updateTrafficStats(bytesIn: Long, bytesOut: Long, latencyMs: Int = 0) {
        _connection.update {
            it.copy(
                bytesIn = bytesIn.coerceAtLeast(0L),
                bytesOut = bytesOut.coerceAtLeast(0L),
                latencyMs = if (latencyMs > 0) latencyMs else it.latencyMs,
            )
        }
    }

    fun reportTunnelState(state: String, detail: String? = null) = publish(state, detail)

    private fun handleRealtimeEvent(event: LinkoRealtimeEvent) {
        when (event) {
            is LinkoRealtimeEvent.SessionStateChanged -> {
                val sessionId = event.sessionId?.takeIf { it.isNotBlank() } ?: return
                val activeSessionId = _connection.value.sessionId
                if (activeSessionId != sessionId) return

                // Once a session is terminal, late realtime packets must never revive it.
                if (_connection.value.phase == LinkoConnectionPhase.Failed || _connection.value.phase == LinkoConnectionPhase.Idle) return

                when (event.state?.trim()?.lowercase()) {
                    "approved" -> publish("requesting", "Request approved; preparing the direct tunnel")
                    "signaling" -> publish("signaling", "Exchanging direct connection information…")
                    "connected" -> publish(
                        "connected",
                        if (_connection.value.isProvider) "Direct connection established; Provider is sharing Internet" else "Internet sharing verified",
                    )
                    "denied" -> publish("connection_request_denied", "Connection request was declined")
                    "expired" -> publish("connection_request_expired", "Connection request expired")
                    "revoked" -> publish("connection_request_revoked", "Connection was revoked")
                    "failed" -> publish("provider_connection_failed", "Direct connection failed")
                    "disconnected" -> publish("stopped", "Direct peer disconnected")
                }
            }
            is LinkoRealtimeEvent.TransportError -> {
                val active = _connection.value.phase
                if (active != LinkoConnectionPhase.Connected && active != LinkoConnectionPhase.Idle && active != LinkoConnectionPhase.Failed) {
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
                    while (isActive) {
                        try {
                            val provider = api?.providerDeviceForUser(userId) ?: break
                            val now = System.currentTimeMillis()
                            val age = if (provider.lastSeenAt > 0L) (now - provider.lastSeenAt).coerceAtLeast(0L) else Long.MAX_VALUE
                            val online = provider.online && age <= PRESENCE_STALE_MS
                            state.value = state.value.copy(
                                online = online,
                                deviceId = provider.deviceId,
                                lastSeenAt = provider.lastSeenAt,
                                checkedAt = now,
                                source = PresenceSource.BackendHeartbeat,
                                error = null,
                            )
                            _events.tryEmit(LinkoEngineEvent.FriendPresenceUpdated(userId, online, provider.lastSeenAt))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            state.value = state.value.copy(
                                checkedAt = System.currentTimeMillis(),
                                source = PresenceSource.Unavailable,
                                error = error.message ?: "presence_check_failed",
                            )
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
        val userId = friendUserId.trim()
        val control = api ?: return publishAndNotify("engine_not_initialized", onState)
        if (userId.isBlank()) return publishAndNotify("friend_not_selected", onState)

        lastFriendUserId = userId
        setPeerInfo(friendName, friendId, false)
        watchFriendPresence(userId)
        val generation = beginNewConnection()
        val engineScope = scope ?: return publishAndNotify("engine_scope_unavailable", onState)

        connectionJob = engineScope.launch {
            try {
                publishAndNotify("connecting", onState)
                assertCurrent(generation)
                control.ensureRegistered()
                assertCurrent(generation)

                publishAndNotify("resolving_provider", onState)
                val provider = control.providerDeviceForUser(userId)
                assertCurrent(generation)
                if (!provider.online || provider.deviceId.isBlank()) throw LinkoNetworkException("provider_offline")

                publishAndNotify("provider_ready", onState)
                val session = control.requestSession(provider.deviceId)
                assertCurrent(generation)
                activateSession(generation, session.id)
                publishAndNotify("requesting", onState)

                awaitApprovedSession(control, session.id, generation, onState)
                establish(session.id, generation, onState)
            } catch (error: CancellationException) {
                Log.i(TAG, "Connection job cancelled generation=$generation")
                throw error
            } catch (error: Exception) {
                handleConnectionFailure(generation, _connection.value.sessionId, error, onState)
            }
        }
    }

    fun reconnect(onState: (String) -> Unit = {}) {
        val friendUserId = lastFriendUserId
        if (friendUserId.isNullOrBlank()) return publishAndNotify("friend_not_available_for_reconnect", onState)
        publishAndNotify("reconnecting", onState)
        connectToFriend(friendUserId, _connection.value.peerDisplayName, _connection.value.peerLinkoId, onState)
    }

    private suspend fun awaitApprovedSession(control: LinkoDeviceControlApi, sessionId: String, generation: Long, onState: (String) -> Unit) {
        repeat(60) { attempt ->
            assertCurrent(generation)
            when (val state = control.session(sessionId).state.trim().lowercase()) {
                "approved", "signaling", "connected" -> return
                "denied" -> throw LinkoNetworkException("connection_request_denied")
                "failed" -> throw LinkoNetworkException("provider_connection_failed")
                "expired" -> throw LinkoNetworkException("connection_request_expired")
                "revoked" -> throw LinkoNetworkException("connection_request_revoked")
                "disconnected" -> throw LinkoNetworkException("provider_disconnected")
                else -> {
                    if (attempt > 0 && attempt % 5 == 0) publishAndNotify("waiting_for_provider", onState)
                    delay(1_000L)
                }
            }
        }
        throw LinkoNetworkException("provider_approval_timeout")
    }

    private suspend fun establish(sessionId: String, generation: Long, onState: (String) -> Unit) {
        assertCurrent(generation, sessionId)
        val control = api ?: throw LinkoNetworkException("engine_not_initialized")
        val tunnel = coordinator ?: throw LinkoNetworkException("engine_not_initialized")

        publishAndNotify("authenticating", onState)
        val authState = LinkoAuth.current()?.ensureSession()
        if (authState != null && !authState.success) throw LinkoNetworkException(authState.message)
        assertCurrent(generation, sessionId)

        control.transition(sessionId, "signaling")
        publishAndNotify("signaling", onState)

        repeat(40) { attempt ->
            assertCurrent(generation, sessionId)
            val current = control.session(sessionId).state.trim().lowercase()
            when (current) {
                "failed" -> throw LinkoNetworkException("provider_connection_failed")
                "denied", "expired", "revoked", "disconnected" -> throw LinkoNetworkException("session_$current")
                else -> Unit
            }

            val config = runCatching { control.tunnelConfig(sessionId) }.getOrNull()
            if (config != null) {
                if (config.sessionId != sessionId || config.role != "receiver" || config.transport != "direct_udp") {
                    throw LinkoNetworkException("invalid_receiver_tunnel_config")
                }
                assertCurrent(generation, sessionId)
                publishAndNotify("establishing", onState)
                publishAndNotify("direct_connecting", onState)
                tunnel.startDirectVpnTunnel(config.sessionId, config.key)
                assertCurrent(generation, sessionId)
                publishAndNotify("routing", onState)
                return
            }
            if (attempt > 0) publishAndNotify("signaling_retry", onState)
            delay(750L)
        }
        throw LinkoNetworkException("tunnel_setup_timeout")
    }

    fun connect(providerDeviceId: String, onState: (String) -> Unit = {}) {
        val providerId = providerDeviceId.trim()
        if (providerId.isBlank()) return publishAndNotify("provider_not_selected", onState)
        val control = api ?: return publishAndNotify("engine_not_initialized", onState)
        val generation = beginNewConnection()
        val engineScope = scope ?: return publishAndNotify("engine_scope_unavailable", onState)

        connectionJob = engineScope.launch {
            try {
                publishAndNotify("connecting", onState)
                assertCurrent(generation)
                control.ensureRegistered()
                assertCurrent(generation)
                val session = control.requestSession(providerId)
                assertCurrent(generation)
                activateSession(generation, session.id)
                awaitApprovedSession(control, session.id, generation, onState)
                establish(session.id, generation, onState)
            } catch (error: CancellationException) {
                Log.i(TAG, "Connection job cancelled generation=$generation")
                throw error
            } catch (error: Exception) {
                handleConnectionFailure(generation, _connection.value.sessionId, error, onState)
            }
        }
    }

    suspend fun getPendingProviderRequests(): List<ProviderRequest> =
        runCatching { api?.pendingProviderRequests() }.getOrNull() ?: emptyList()

    fun approvePendingProviderRequest(peerName: String? = null, peerId: String? = null, onState: (String) -> Unit = {}) {
        val context = appContext ?: return notifyOnMain(onState, "engine_not_initialized")
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
                startApprovedProviderSession(context, request.id, onState)
            } catch (error: CancellationException) {
                requestId?.let(providerStartsInFlight::remove)
                throw error
            } catch (error: Throwable) {
                requestId?.let(providerStartsInFlight::remove)
                Log.e(TAG, "ACCEPT_FLOW_CRASH_CONTAINED session=${requestId ?: "unknown"}", error)
                notifyOnMain(onState, "accept_failed:${error.javaClass.simpleName}:${error.message ?: "unknown"}")
            }
        } ?: notifyOnMain(onState, "engine_scope_unavailable")
    }

    private fun startApprovedProviderSession(context: Context, sessionId: String, onState: (String) -> Unit) {
        if (sessionId.isBlank()) {
            providerStartsInFlight.remove(sessionId)
            notifyOnMain(onState, "invalid_session_id")
            return
        }
        runCatching {
            Log.i(TAG, "PROVIDER_START_REQUEST session=$sessionId")
            context.startForegroundService(
                Intent(context, LinkoProviderService::class.java)
                    .setAction(LinkoProviderService.ACTION_START_APPROVED)
                    .putExtra(LinkoProviderService.EXTRA_REQUEST_ID, sessionId),
            )
        }.onSuccess {
            notifyOnMain(onState, "starting")
            Log.i(TAG, "PROVIDER_START_ACCEPTED session=$sessionId")
        }.onFailure { error ->
            providerStartsInFlight.remove(sessionId)
            Log.e(TAG, "PROVIDER_START_REJECTED session=$sessionId", error)
            notifyOnMain(onState, "provider_service_start_failed:${error.javaClass.simpleName}:${error.message ?: "unknown"}")
            scope?.launch {
                runCatching { api?.transition(sessionId, "failed") }
            }
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
                    if (request == null) {
                        notifyOnMain(onState, "no_pending_request")
                    } else {
                        stopProviderSession(request.id)
                        runCatching { api?.transition(request.id, "denied") }
                            .onSuccess { providerStartsInFlight.remove(request.id); notifyOnMain(onState, "denied") }
                            .onFailure { notifyOnMain(onState, it.message ?: "decline_failed") }
                    }
                }
                .onFailure { notifyOnMain(onState, it.message ?: "request_lookup_failed") }
        } ?: notifyOnMain(onState, "engine_scope_unavailable")
    }

    fun disconnect() {
        val sessionId = _connection.value.sessionId
        beginNewConnection(stopServices = true)
        lastFriendUserId?.let(::stopWatchingFriendPresence)
        lastFriendUserId = null
        providerStartsInFlight.clear()
        if (!sessionId.isNullOrBlank()) {
            scope?.launch {
                runCatching {
                    val current = api?.session(sessionId)?.state?.trim()?.lowercase()
                    if (current !in TERMINAL_SESSION_STATES) api?.transition(sessionId, "disconnected")
                }.onFailure { Log.w(TAG, "Disconnect state update failed session=$sessionId: ${it.message}") }
            }
        }
        _connection.value = LinkoEngineConnectionState(
            phase = LinkoConnectionPhase.Idle,
            detail = "Disconnected · tunnel closed",
        )
    }

    private fun beginNewConnection(stopServices: Boolean = true): Long {
        val generation = operationGeneration.incrementAndGet()
        cancelActiveWork(stopServices = stopServices)
        if (stopServices) {
            runCatching { coordinator?.stopVpnTunnel() }
            appContext?.let { context -> runCatching { context.stopService(Intent(context, LinkoProviderService::class.java)) } }
        }
        _connection.update {
            it.copy(
                sessionId = null,
                bytesIn = 0L,
                bytesOut = 0L,
                latencyMs = 0,
                error = null,
                phase = LinkoConnectionPhase.Idle,
                detail = "Ready",
            )
        }
        return generation
    }

    private fun cancelActiveWork(stopServices: Boolean) {
        connectionJob?.cancel()
        connectionJob = null
        realtimeJob?.cancel()
        realtimeJob = null
        presenceJobs.values.forEach(Job::cancel)
        presenceJobs.clear()
        if (stopServices) {
            runCatching { coordinator?.stopVpnTunnel() }
            appContext?.let { context -> runCatching { context.stopService(Intent(context, LinkoProviderService::class.java)) } }
        }
    }

    private fun activateSession(generation: Long, sessionId: String) {
        require(sessionId.isNotBlank()) { "session_id_required" }
        assertCurrent(generation)
        _connection.update { it.copy(sessionId = sessionId) }
    }

    private fun assertCurrent(generation: Long, sessionId: String? = null) {
        if (operationGeneration.get() != generation) throw CancellationException("stale_engine_operation")
        if (sessionId != null && _connection.value.sessionId != sessionId) throw CancellationException("stale_engine_session")
    }

    private fun handleConnectionFailure(generation: Long, sessionId: String?, error: Exception, onState: (String) -> Unit) {
        if (operationGeneration.get() != generation) {
            Log.i(TAG, "Ignoring stale connection failure generation=$generation")
            return
        }
        val message = error.message?.takeIf { it.isNotBlank() } ?: "connection_failed"
        Log.e(TAG, "ENGINE_CONNECTION_FAILED generation=$generation session=${sessionId ?: "none"} reason=$message", error)
        terminateReceiverForFailure(sessionId, generation)
        publishAndNotify(normalizeFailureState(message), onState)
    }

    private fun normalizeFailureState(message: String): String = when {
        message.contains("denied", ignoreCase = true) -> "connection_request_denied"
        message.contains("expired", ignoreCase = true) -> "connection_request_expired"
        message.contains("revoked", ignoreCase = true) -> "connection_request_revoked"
        message.contains("offline", ignoreCase = true) -> "provider_connection_failed"
        message.contains("timeout", ignoreCase = true) -> "provider_connection_failed"
        else -> "provider_connection_failed"
    }

    private fun terminateReceiverForFailure(sessionId: String?, generation: Long) {
        if (operationGeneration.get() != generation || sessionId.isNullOrBlank()) return
        runCatching { coordinator?.stopVpnTunnel() }
        scope?.launch {
            runCatching {
                val state = api?.session(sessionId)?.state?.trim()?.lowercase()
                if (state !in TERMINAL_SESSION_STATES) api?.transition(sessionId, "failed")
            }.onFailure { Log.w(TAG, "Failed to publish receiver failure session=$sessionId: ${it.message}") }
        }
    }

    private fun stopProviderSession(sessionId: String) {
        providerStartsInFlight.remove(sessionId)
        appContext?.let { context ->
            runCatching {
                context.startService(
                    Intent(context, LinkoProviderService::class.java)
                        .setAction(LinkoProviderService.ACTION_STOP)
                        .putExtra(LinkoProviderService.EXTRA_REQUEST_ID, sessionId),
                )
            }
        }
    }

    private fun publishAndNotify(state: String, onState: (String) -> Unit) {
        publish(state)
        notifyOnMain(onState, state)
    }

    private fun notifyOnMain(onState: (String) -> Unit, state: String) {
        mainHandler.post {
            runCatching { onState(state) }
                .onFailure { error -> Log.e(TAG, "UI_STATE_CALLBACK_FAILED state=$state", error) }
        }
    }

    private fun publish(state: String, detail: String? = null) {
        val current = _connection.value
        // Do not allow non-terminal events to resurrect a failed or already stopped session.
        if (current.phase == LinkoConnectionPhase.Failed && state !in FAILURE_STATES) return

        val phase = when (state) {
            "connecting", "reconnecting", "waiting_for_provider" -> LinkoConnectionPhase.Connecting
            "authenticating" -> LinkoConnectionPhase.Authenticating
            "resolving_provider", "provider_ready", "requesting", "signaling", "signaling_retry" -> LinkoConnectionPhase.Signaling
            "establishing", "direct_connecting" -> LinkoConnectionPhase.Establishing
            "securing" -> LinkoConnectionPhase.Securing
            "routing" -> LinkoConnectionPhase.Routing
            "direct_established", "direct_verified", "connected" -> LinkoConnectionPhase.Connected
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
            "direct_verified" -> "Authenticated direct path verified"
            "connected" -> if (current.isProvider) "Direct connection established; Provider is sharing Internet" else "Internet sharing verified"
            "stopped" -> "Direct tunnel stopped"
            "connection_request_denied" -> "Connection request was declined"
            "connection_request_expired" -> "Connection request expired"
            "connection_request_revoked" -> "Connection was revoked"
            "provider_connection_failed" -> "Provider could not establish a direct connection"
            "provider_disconnected" -> "Provider disconnected"
            "realtime_disconnected" -> "Realtime control connection interrupted"
            else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        _connection.update {
            it.copy(
                phase = phase,
                detail = message,
                error = if (phase == LinkoConnectionPhase.Failed) message else null,
            )
        }
    }

    private val _events = MutableStateFlow<LinkoEngineEvent?>(null)
    val engineEvents: StateFlow<LinkoEngineEvent?> = _events.asStateFlow()

    private val TERMINAL_SESSION_STATES = setOf("failed", "denied", "expired", "revoked", "disconnected")
    private val FAILURE_STATES = setOf("connection_request_denied", "connection_request_expired", "connection_request_revoked", "provider_connection_failed", "realtime_disconnected")
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
