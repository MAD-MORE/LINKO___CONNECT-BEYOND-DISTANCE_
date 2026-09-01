package com.linkshare.app.network

import android.content.Context
import android.content.Intent
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.diagnostics.LinkoDiagnosticTelemetry
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.tunnel.TunnelCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object LinkoEngineBridge {
    private var api: LinkoDeviceControlApi? = null
    private var coordinator: TunnelCoordinator? = null
    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var connectionJob: Job? = null
    private var approvedProviderSessionId: String? = null
    private var lastFriendUserId: String? = null
    private val _connection = MutableStateFlow(LinkoEngineConnectionState())
    val connection: StateFlow<LinkoEngineConnectionState> = _connection.asStateFlow()

    fun configure(context: Context) {
        val app = context.applicationContext; appContext = app; LinkoAuth(app); api = LinkoDeviceControlApi(app); coordinator = TunnelCoordinator(app)
        scope = CoroutineScope(Dispatchers.IO); connectionJob?.cancel(); connectionJob = null; lastFriendUserId = null; publish("idle", "Ready")
    }

    fun connectToFriend(friendUserId: String, onState: (String) -> Unit = {}) {
        val control = api ?: return publishAndNotify("engine_not_initialized", onState)
        if (friendUserId.isBlank()) return publishAndNotify("friend_not_selected", onState)
        lastFriendUserId = friendUserId; connectionJob?.cancel(); val engineScope = scope ?: return publishAndNotify("engine_scope_unavailable", onState)
        connectionJob = engineScope.launch {
            try {
                publishAndNotify("connecting", onState); control.ensureRegistered(); publishAndNotify("resolving_provider", onState)
                val provider = control.providerDeviceForUser(friendUserId); if (!provider.online) throw LinkoNetworkException("provider_offline")
                publishAndNotify("provider_ready", onState); val session = control.requestSession(provider.deviceId); publishAndNotify("requesting", onState)
                awaitApprovedSession(control, session.id, onState); establish(session.id, onState)
            } catch (e: Exception) { publishAndNotify(e.message ?: "connection_failed", onState) }
        }
    }

    fun reconnect(onState: (String) -> Unit = {}) { val friendUserId = lastFriendUserId; if (friendUserId.isNullOrBlank()) return publishAndNotify("friend_not_available_for_reconnect", onState); publishAndNotify("reconnecting", onState); connectToFriend(friendUserId, onState) }

    private suspend fun awaitApprovedSession(control: LinkoDeviceControlApi, sessionId: String, onState: (String) -> Unit) {
        repeat(60) { attempt ->
            when (val current = control.session(sessionId).state) {
                "approved", "signaling", "connected" -> return@repeat
                "denied" -> throw LinkoNetworkException("connection_request_denied")
                "expired" -> throw LinkoNetworkException("connection_request_expired")
                "revoked" -> throw LinkoNetworkException("connection_request_revoked")
                else -> { if (attempt > 0 && attempt % 5 == 0) publishAndNotify("waiting_for_provider", onState); delay(1_000L) }
            }
            if (control.session(sessionId).state in setOf("approved", "signaling", "connected")) return
        }
        throw LinkoNetworkException("provider_approval_timeout")
    }

    private suspend fun establish(sessionId: String, onState: (String) -> Unit) {
        val control = api ?: throw LinkoNetworkException("engine_not_initialized")
        val tunnel = coordinator ?: throw LinkoNetworkException("engine_not_initialized")
        publishAndNotify("authenticating", onState)
        val sessionAuth = LinkoAuth.current()?.ensureSession()
        if (sessionAuth != null && !sessionAuth.success) throw LinkoNetworkException(sessionAuth.message)
        control.transition(sessionId, "signaling"); publishAndNotify("signaling", onState)

        val accessToken = LinkoAuth.current()?.currentAccessToken()
        val ice = accessToken?.takeIf { it.isNotBlank() }?.let { LinkoIceCoordinator(LinkoSignalingClient(accessToken = it)) }
        var remoteCandidate: LinkoIceCoordinator.RemoteCandidate? = null

        for (attempt in 0 until 40) {
            val current = control.session(sessionId)
            if (current.state == "denied" || current.state == "expired" || current.state == "revoked") throw LinkoNetworkException("session_${current.state}")
            if (ice != null && remoteCandidate == null) remoteCandidate = runCatching { ice.awaitRemoteCandidate(sessionId, 250L) }.getOrNull()
            val configResult = runCatching { control.tunnelConfig(sessionId) }
            val config = configResult.getOrNull()
            if (config != null) {
                publishAndNotify("establishing", onState)
                val host = remoteCandidate?.host ?: config.host
                val port = remoteCandidate?.port ?: config.port
                val path = if (remoteCandidate != null) "direct" else "relay"
                publish("establishing", "Selecting $path UDP path…"); onState("establishing")
                tunnel.startVpnTunnel(host, port, config.sessionId, config.key)
                publishAndNotify("securing", onState); publishAndNotify("routing", onState)
                runCatching { control.transition(sessionId, "connected") }; publishAndNotify("connected", onState); return
            }

            val tunnelError = configResult.exceptionOrNull()
            val errorMessage = tunnelError?.message.orEmpty()
            if (errorMessage == "no_healthy_relay") {
                publish("establishing", "No healthy relay is currently available; retrying…")
                onState("relay_retry")
            } else if (tunnelError != null) {
                publish("establishing", "Tunnel configuration unavailable; retrying…")
                onState("tunnel_config_retry")
            } else if (attempt > 0) {
                publishAndNotify("signaling_retry", onState)
            }
            delay(if (errorMessage == "no_healthy_relay") 2_000L else 1_000L)
        }
        throw LinkoNetworkException("tunnel_setup_timeout")
    }

    fun connect(providerDeviceId: String, onState: (String) -> Unit = {}) {
        connectionJob?.cancel(); val engineScope = scope ?: return publishAndNotify("engine_scope_unavailable", onState)
        connectionJob = engineScope.launch { try { val control = api ?: throw LinkoNetworkException("engine_not_initialized"); control.ensureRegistered(); val session = control.requestSession(providerDeviceId); awaitApprovedSession(control, session.id, onState); establish(session.id, onState) } catch (e: Exception) { publishAndNotify(e.message ?: "connection_failed", onState) } }
    }

    suspend fun getPendingProviderRequests(): List<ProviderRequest> = runCatching { api?.pendingProviderRequests() }.getOrNull() ?: emptyList()

    fun approvePendingProviderRequest(onState: (String) -> Unit = {}) {
        val context = appContext ?: return onState("engine_not_initialized")
        context.startForegroundService(Intent(context, LinkoProviderService::class.java)); scope?.launch {
            runCatching { api?.pendingProviderRequests()?.firstOrNull() }.onSuccess { request ->
                if (request == null) onState("no_pending_request") else runCatching { api?.transition(request.id, "approved") }.onSuccess { approvedProviderSessionId = request.id; onState("approved") }.onFailure { onState(it.message ?: "approval_failed") }
            }.onFailure { onState(it.message ?: "request_lookup_failed") }
        } ?: onState("engine_scope_unavailable")
    }

    fun startApprovedProviderSession(onState: (String) -> Unit = {}) {
        val context = appContext ?: return onState("engine_not_initialized"); val sessionId = approvedProviderSessionId ?: return onState("no_approved_session")
        context.startForegroundService(Intent(context, LinkoProviderService::class.java).setAction(LinkoProviderService.ACTION_START_APPROVED).putExtra(LinkoProviderService.EXTRA_REQUEST_ID, sessionId)); approvedProviderSessionId = null; onState("starting")
    }

    fun denyPendingProviderRequest(onState: (String) -> Unit = {}) {
        val context = appContext ?: return onState("engine_not_initialized"); context.startForegroundService(Intent(context, LinkoProviderService::class.java)); scope?.launch {
            runCatching { api?.pendingProviderRequests()?.firstOrNull() }.onSuccess { request -> if (request == null) onState("no_pending_request") else runCatching { api?.transition(request.id, "denied") }.onSuccess { onState("denied") }.onFailure { onState(it.message ?: "decline_failed") } }.onFailure { onState(it.message ?: "request_lookup_failed") }
        } ?: onState("engine_scope_unavailable")
    }

    fun disconnect() { connectionJob?.cancel(); coordinator?.stopVpnTunnel(); connectionJob = null; lastFriendUserId = null; publish("idle", "Disconnected · tunnel closed") }

    private fun publishAndNotify(state: String, onState: (String) -> Unit) { publish(state); onState(state) }
    private fun publish(state: String, detail: String? = null) {
        val normalized = when (state) { "connecting", "reconnecting", "waiting_for_provider" -> LinkoConnectionPhase.Connecting; "authenticating" -> LinkoConnectionPhase.Authenticating; "resolving_provider", "provider_ready", "requesting", "signaling", "signaling_retry" -> LinkoConnectionPhase.Signaling; "establishing" -> LinkoConnectionPhase.Establishing; "securing" -> LinkoConnectionPhase.Securing; "routing" -> LinkoConnectionPhase.Routing; "connected" -> LinkoConnectionPhase.Connected; "idle" -> LinkoConnectionPhase.Idle; else -> LinkoConnectionPhase.Failed }
        val message = detail ?: when (state) { "connecting" -> "Connecting…"; "reconnecting" -> "Recovering the connection…"; "waiting_for_provider" -> "Waiting for provider approval…"; "authenticating" -> "Authenticating your LINKO session…"; "resolving_provider" -> "Finding your friend's available device…"; "provider_ready" -> "Provider found. Preparing a secure request…"; "requesting" -> "Waiting for provider approval…"; "signaling" -> "Negotiating the secure session…"; "signaling_retry" -> "Negotiating… retrying"; "establishing" -> "Establishing tunnel…"; "securing" -> "Securing encrypted transport…"; "routing" -> "Routing traffic…"; "connected" -> "Connected · provider confirmed the active session"; else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() } }
        _connection.update { it.copy(phase = normalized, detail = message, error = if (normalized == LinkoConnectionPhase.Failed) message else null) }
        LinkoDiagnosticTelemetry.recordEngine(normalized.name, message, if (normalized == LinkoConnectionPhase.Failed) message else null)
    }
}

enum class LinkoConnectionPhase { Idle, Connecting, Authenticating, Signaling, Establishing, Securing, Routing, Connected, Failed }
data class LinkoEngineConnectionState(val phase: LinkoConnectionPhase = LinkoConnectionPhase.Idle, val detail: String = "Ready", val error: String? = null)
