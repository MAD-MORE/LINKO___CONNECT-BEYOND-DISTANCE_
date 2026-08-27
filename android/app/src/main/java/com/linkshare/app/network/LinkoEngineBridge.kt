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
    private var lastFriendUserId: String? = null

    private val _connection = MutableStateFlow(LinkoEngineConnectionState())
    val connection: StateFlow<LinkoEngineConnectionState> = _connection.asStateFlow()

    fun configure(context: Context) {
        val app = context.applicationContext
        val auth = LinkoAuth(app)
        appContext = app
        api = LinkoControlPlaneApi(LinkoRuntimeConfig.controlPlaneUrl, { auth.currentAccessToken() }, { auth.currentDeviceId() })
        coordinator = TunnelCoordinator(app)
        scope = CoroutineScope(Dispatchers.IO)
        connectionJob?.cancel()
        connectionJob = null
        lastFriendUserId = null
        publish("idle")
    }

    /** Linear path: friend -> provider -> session -> tunnel -> connected. */
    fun connectToFriend(friendUserId: String, onState: (String) -> Unit = {}) {
        val control = api ?: return publishAndNotify("engine_not_initialized", onState)
        if (friendUserId.isBlank()) return publishAndNotify("friend_not_selected", onState)
        lastFriendUserId = friendUserId
        connectionJob?.cancel()
        val engineScope = scope ?: return publishAndNotify("engine_scope_unavailable", onState)
        connectionJob = engineScope.launch {
            try {
                publishAndNotify("connecting", onState)
                publishAndNotify("resolving_provider", onState)
                val response = control.providerDeviceForUser(friendUserId)
                val deviceId = response.optJSONObject("device")?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: throw LinkoNetworkException("provider_not_available")
                publishAndNotify("provider_ready", onState)
                establish(deviceId, onState)
            } catch (e: Exception) {
                publishAndNotify(e.message ?: "connection_failed", onState)
            }
        }
    }

    /** Reconnect uses the same real connection algorithm; it never navigates directly to Connected. */
    fun reconnect(onState: (String) -> Unit = {}) {
        val friendUserId = lastFriendUserId
        if (friendUserId.isNullOrBlank()) {
            return publishAndNotify("friend_not_available_for_reconnect", onState)
        }
        connectToFriend(friendUserId, onState)
    }

    private suspend fun establish(providerDeviceId: String, onState: (String) -> Unit) {
        val control = api ?: throw LinkoNetworkException("engine_not_initialized")
        val tunnel = coordinator ?: throw LinkoNetworkException("engine_not_initialized")
        if (providerDeviceId.isBlank()) throw LinkoNetworkException("provider_not_available")

        publishAndNotify("authenticating", onState)
        val sessionResult = LinkoAuth.current()?.ensureSession()
        if (sessionResult != null && !sessionResult.success) {
            throw LinkoNetworkException(sessionResult.message)
        }
        publishAndNotify("requesting", onState)
        val session = control.requestAccess(providerDeviceId)
        publishAndNotify("signaling", onState)

        var tunnelStarted = false
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
            tunnelStarted = true
            publishAndNotify("securing", onState)
            delay(250L)
            publishAndNotify("routing", onState)
            delay(250L)
            if (tunnelStarted) {
                publishAndNotify("connected", onState)
            }
            return
        }
        if (!tunnelStarted) {
            publishAndNotify("signaling_timeout", onState)
        }
    }

    fun connect(providerDeviceId: String, onState: (String) -> Unit = {}) {
        connectionJob?.cancel()
        val engineScope = scope ?: return publishAndNotify("engine_scope_unavailable", onState)
        connectionJob = engineScope.launch {
            try {
                establish(providerDeviceId, onState)
            } catch (e: Exception) {
                publishAndNotify(e.message ?: "connection_failed", onState)
            }
        }
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
        val control = api ?: return onState("engine_not_initialized")
        scope?.launch {
            runCatching { control.getPendingProviderRequests().firstOrNull() }
                .onSuccess { request ->
                    if (request == null) onState("no_pending_request") else runCatching { control.denyRequest(request.id) }
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
        lastFriendUserId = null
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
            "provider_ready", "requesting", "signaling", "signaling_retry" -> LinkoConnectionPhase.Signaling
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
            "signaling_timeout" -> "Connection failed · signaling timed out"
            "friend_not_available_for_reconnect" -> "Reconnect failed · no friend session is available"
            else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        _connection.update {
            it.copy(
                phase = normalized,
                detail = message,
                error = if (normalized == LinkoConnectionPhase.Failed) message else null,
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
