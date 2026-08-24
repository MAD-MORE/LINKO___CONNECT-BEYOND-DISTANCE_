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
import kotlinx.coroutines.launch

/** Single real engine boundary used by the production LINKO UI. */
object LinkoEngineBridge {
    private var api: LinkoControlPlaneApi? = null
    private var coordinator: TunnelCoordinator? = null
    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var connectionJob: Job? = null
    private var approvedProviderSessionId: String? = null

    fun configure(context: Context) {
        val app = context.applicationContext
        val auth = LinkoAuth(app)
        appContext = app
        api = LinkoControlPlaneApi(LinkoRuntimeConfig.controlPlaneUrl, { auth.currentLinkoToken() }, { auth.currentDeviceId() })
        coordinator = TunnelCoordinator(app)
        scope = CoroutineScope(Dispatchers.IO)
    }

    fun connectToFriend(friendUserId: String, onState: (String) -> Unit = {}) {
        val control = api ?: return onState("engine_not_initialized")
        if (friendUserId.isBlank()) return onState("friend_not_selected")
        scope?.launch {
            runCatching {
                onState("resolving_provider")
                val response = control.providerDeviceForUser(friendUserId)
                val deviceId = response.optJSONObject("device")?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: throw LinkoNetworkException("provider_not_available")
                onState("provider_ready")
                connect(deviceId, onState)
            }.onFailure { onState(it.message ?: "provider_resolution_failed") }
        } ?: onState("engine_scope_unavailable")
    }

    fun connect(providerDeviceId: String, onState: (String) -> Unit = {}) {
        val control = api ?: return onState("engine_not_initialized")
        val tunnel = coordinator ?: return onState("engine_not_initialized")
        if (providerDeviceId.isBlank()) return onState("provider_not_available")
        connectionJob?.cancel()
        connectionJob = scope?.launch {
            try {
                onState("requesting")
                val session = control.requestAccess(providerDeviceId)
                repeat(40) {
                    delay(1_500L)
                    val config = runCatching { control.tunnelConfig(session.sessionId) }.getOrNull() ?: return@repeat
                    val endpoint = config.optJSONObject("endpoint") ?: return@repeat
                    val host = endpoint.optString("host")
                    val port = endpoint.optInt("port", -1)
                    val key = runCatching { java.util.Base64.getUrlDecoder().decode(config.optString("key")) }.getOrNull()
                    if (host.isBlank() || port !in 1..65535 || key?.size != 32) return@repeat
                    onState("connecting")
                    tunnel.startVpnTunnel(host, port, session.sessionId, key)
                    onState("connected")
                    return@launch
                }
                onState("connection_timeout")
            } catch (e: Exception) { onState(e.message ?: "connection_failed") }
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

    fun disconnect() { connectionJob?.cancel(); coordinator?.stopVpnTunnel() }
}
