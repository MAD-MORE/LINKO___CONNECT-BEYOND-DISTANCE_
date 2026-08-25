package com.linkshare.app.network

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Single real engine boundary used by the production LINKO UI. */
object LinkoEngineBridge {
    private var api: LinkoControlPlaneApi? = null
    private var coordinator: TunnelCoordinator? = null
    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var connectionJob: Job? = null
    private var approvedProviderSessionId: String? = null

    private val _connection = MutableStateFlow(LinkoEngineConnectionState())
    val connection: StateFlow<LinkoEngineConnectionState> = _connection.asStateFlow()

    fun configure(context: Context) {
        val app = context.applicationContext
        val auth = LinkoAuth(app)
        appContext = app
        api = LinkoControlPlaneApi(LinkoRuntimeConfig.controlPlaneUrl, { auth.currentLinkoToken() }, { auth.currentDeviceId() })
        coordinator = TunnelCoordinator(app)
        scope = CoroutineScope(Dispatchers.IO)
        publish("idle")
    }

    fun connectToFriend(friendUserId: String, onState: (String) -> Unit = {}) {
        val control = api ?: return publishAndNotify("engine_not_initialized", onState)
        if (friendUserId.isBlank()) return publishAndNotify("friend_not_selected", onState)
        connectionJob?.cancel()
        scope?.launch {
            runCatching {
                publishAndNotify("connecting", onState)
                publishAndNotify("resolving_provider", onState)
                val response = control.providerDeviceForUser(friendUserId)
                val deviceId = response.optJSONObject("device")?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: throw LinkoNetworkException("provider_not_available")
                publishAndNotify("provider_ready", onState)
                connect(deviceId, onState)
            }.onFailure { publishAndNotify(it.message ?: "provider_resolution_failed", onState) }
        } ?: publishAndNotify("engine_scope_unavailable", onState)
    }

    fun connect(providerDeviceId: String, onState: (String) -> Unit = {}) {
        val control = api ?: return publishAndNotify("engine_not_initialized", onState)
        val tunnel = coordinator ?: return publishAndNotify("engine_not_initialized", onState)
        if (providerDeviceId.isBlank()) return publishAndNotify("provider_not_available", onState)
        connectionJob?.cancel()
        connectionJob = scope?.launch {
            try {
                publishAndNotify("authenticating", onState)
                publishAndNotify("requesting", onState)
                val session = control.requestAccess(providerDeviceId)
                publishAndNotify("signaling", onState)
                repeat(40) { attempt ->
                    if (attempt > 0) publishAndNotify("signaling_retry", onState)
                    delay(1_500L)
                    val config = runCatching { control.tunnelConfig(session.sessionId) }.getOrNull() ?: return@repeat
                    val endpoint = config.optJSONObject("endpoint") ?: return@repeat
                    val host = endpoint.optString("host")
                    val port = endpoint.optInt("port", -1)
                    val key = runCatching { java.util.Base64.getUrlDecoder().decode(config.optString("key")) }.getOrNull()
                    if (host.isBlank() || port !in 1..65535 || key?.size != 32) return@repeat
                    publishAndNotify("establishing", onState)
                    tunnel.startVpnTunnel(host, port, session.sessionId, key)
                    publishAndNotify("securing", onState)
                    delay(250L)
                    publishAndNotify("routing", onState)
                    delay(250L)
                    publishAndNotify("connected", onState)
                    return@launch
                }
                publishAndNotify("signaling_timeout", onState)
            } catch (e: Exception) {
                publishAndNotify(e.message ?: "connection_failed", onState)
            }
        } ?: publishAndNotify("engine_scope_unavailable", onState)
    }

    fun approvePendingProviderRequest(onState: (String) -> Unit = {}) {
        val control = api ?: return onState("engine_not_initialized")
        scope?.launch {
            runCatching { control.getPendingProviderRequests().firstOrNull() }
                .onSuccess { request ->
                    if (request == null) onState("no_pending_request") else runCatching { control.approveRequest(request.id) }
                        .onSuccess { approvedProviderSessionId = request.id; onState("approved") }
                        .onFailure { onState(it.message ?: "approval_failed") }
                }
                .onFailure { onState(it.message ?: "request_lookup_failed") }
        }
    }

    fun startApprovedProviderSession(onState: (String) -> Unit = {}) {
        val context = appContext ?: return onState("engine_not_initialized")
        val sessionId = approvedProviderSessionId ?: return onState("no_approved_session")
        context.startForegroundService(Intent(context, LinkoProviderService::class.java).setAction(LinkoProviderService.ACTION_START_APPROVED).putExtra(LinkoProviderService.EXTRA_REQUEST_ID, sessionId))
        approvedProviderSessionId = null
        onState("starting")
    }

    fun denyPendingProviderRequest(onState: (String) -> Unit = {}) {
        val control = api ?: return onState("engine_not_initialized")
        scope?.launch { runCatching { control.getPendingProviderRequests().firstOrNull() }.onSuccess { request -> if (request == null) onState("no_pending_request") else runCatching { control.denyRequest(request.id) }.onSuccess { onState("denied") }.onFailure { onState(it.message ?: "decline_failed") } }.onFailure { onState(it.message ?: "request_lookup_failed") } }
    }

    fun disconnect() {
        connectionJob?.cancel()
        coordinator?.stopVpnTunnel()
        publish("idle", "Disconnected · tunnel closed")
    }

    private fun publishAndNotify(state: String, onState: (String) -> Unit) {
        publish(state)
        onState(state)
    }

    private fun publish(state: String, detail: String? = null) {
        val normalized = when (state) {
            "connecting" -> LinkoConnectionPhase.Connecting
            "authenticating" -> LinkoConnectionPhase.Authenticating
            "resolving_provider" -> LinkoConnectionPhase.Signaling
            "provider_ready", "requesting" -> LinkoConnectionPhase.Signaling
            "signaling", "signaling_retry" -> LinkoConnectionPhase.Signaling
            "establishing" -> LinkoConnectionPhase.Establishing
            "securing" -> LinkoConnectionPhase.Securing
            "routing" -> LinkoConnectionPhase.Routing
            "connected" -> LinkoConnectionPhase.Connected
            "idle" -> LinkoConnectionPhase.Idle
            else -> LinkoConnectionPhase.Failed
        }
        val message = detail ?: when (state) {
            "connecting" -> "Connecting…"
            "authenticating" -> "Authenticating your LINKO session…"
            "resolving_provider" -> "Finding your friend's available device…"
            "provider_ready" -> "Provider found. Preparing a secure request…"
            "requesting" -> "Sending connection request…"
            "signaling" -> "Signaling…"
            "signaling_retry" -> "Signaling… retrying"
            "establishing" -> "Establishing tunnel…"
            "securing" -> "Securing encrypted transport…"
            "routing" -> "Routing traffic…"
            "connected" -> "Connected · secure tunnel is active"
            "signaling_timeout" -> "Signaling timeout"
            else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        _connection.update {
            it.copy(
                phase = normalized,
                detail = message,
                error = if (normalized == LinkoConnectionPhase.Failed) message else null
            )
        }
    }
}

enum class LinkoConnectionPhase { Idle, Connecting, Authenticating, Signaling, Establishing, Securing, Routing, Connected, Failed }

data class LinkoEngineConnectionState(
    val phase: LinkoConnectionPhase = LinkoConnectionPhase.Idle,
    val detail: String = "Ready",
    val error: String? = null,
)
