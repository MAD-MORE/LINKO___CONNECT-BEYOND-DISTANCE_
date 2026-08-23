package com.linkshare.app.network

import android.content.Context
import com.linkshare.app.auth.LinkoAuth
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
    private var connectionJob: Job? = null

    fun configure(context: Context) {
        val app = context.applicationContext
        val auth = LinkoAuth(app)
        api = LinkoControlPlaneApi(LinkoRuntimeConfig.controlPlaneUrl, { auth.currentLinkoToken() }, { auth.currentDeviceId() })
        coordinator = TunnelCoordinator(app)
        scope = CoroutineScope(Dispatchers.IO)
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
                    val config = runCatching { control.tunnelConfig(session.id) }.getOrNull() ?: return@repeat
                    val endpoint = config.optJSONObject("endpoint") ?: return@repeat
                    val host = endpoint.optString("host")
                    val port = endpoint.optInt("port", -1)
                    val key = runCatching { java.util.Base64.getUrlDecoder().decode(config.optString("key")) }.getOrNull()
                    if (host.isBlank() || port !in 1..65535 || key?.size != 32) return@repeat
                    onState("connecting")
                    tunnel.startVpnTunnel(host, port, session.id, key)
                    onState("connected")
                    return@launch
                }
                onState("connection_timeout")
            } catch (e: Exception) { onState(e.message ?: "connection_failed") }
        }
    }

    fun approvePendingProviderRequest(onState: (String) -> Unit = {}) {
        val control = api ?: return onState("engine_not_initialized")
        scope?.launch { runCatching { control.getPendingProviderRequests().firstOrNull() }.onSuccess { request ->
            if (request == null) onState("no_pending_request") else runCatching { control.approveRequest(request.id) }.onSuccess { onState("approved") }.onFailure { onState(it.message ?: "approval_failed") }
        }.onFailure { onState(it.message ?: "request_lookup_failed") } }
    }

    fun denyPendingProviderRequest(onState: (String) -> Unit = {}) {
        val control = api ?: return onState("engine_not_initialized")
        scope?.launch { runCatching { control.getPendingProviderRequests().firstOrNull() }.onSuccess { request ->
            if (request == null) onState("no_pending_request") else runCatching { control.denyRequest(request.id) }.onSuccess { onState("denied") }.onFailure { onState(it.message ?: "decline_failed") }
        }.onFailure { onState(it.message ?: "request_lookup_failed") } }
    }

    fun disconnect() {
        connectionJob?.cancel()
        coordinator?.stopVpnTunnel()
    }
}
