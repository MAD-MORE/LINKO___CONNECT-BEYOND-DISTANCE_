package com.linkshare.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkshare.app.model.AppMode
import com.linkshare.app.model.ConnectionPhase
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest
import com.linkshare.app.network.LinkoControlPlaneApi
import com.linkshare.app.network.LinkoNetworkException
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.tunnel.TunnelCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LinkShareViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    private var connectionJob: Job? = null
    private var requestPollJob: Job? = null

    fun setMode(mode: AppMode) = _uiState.update { it.copy(mode = mode, eventMessage = null, failureReason = null) }

    fun toggleHostSharing() {
        val context = engineContext ?: return _uiState.update { it.copy(eventMessage = "Engine is still initializing.") }
        if (_uiState.value.hostSharingEnabled) {
            LinkoProviderService.stop(context)
            _uiState.update { it.copy(hostSharingEnabled = false, incomingRequest = null, eventMessage = "Sharing stopped.") }
        } else {
            LinkoProviderService.start(context)
            _uiState.update { it.copy(hostSharingEnabled = true, eventMessage = "Sharing is live and waiting for a secure request.") }
        }
    }

    fun approveIncomingRequest() {
        val api = controlPlaneApi ?: return _uiState.update { it.copy(eventMessage = "Engine is still initializing.") }
        val request = _uiState.value.incomingRequest ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.approveRequest(request.id) }
                .onSuccess { _uiState.update { it.copy(incomingRequest = null, eventMessage = "Request approved. Preparing the secure tunnel…") } }
                .onFailure { error -> fail("Approval failed", error.message ?: "Unable to approve request") }
        }
    }

    fun denyIncomingRequest() {
        val api = controlPlaneApi ?: return _uiState.update { it.copy(eventMessage = "Engine is still initializing.") }
        val request = _uiState.value.incomingRequest ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.denyRequest(request.id) }
                .onSuccess { _uiState.update { it.copy(incomingRequest = null, eventMessage = "Request declined.") } }
                .onFailure { error -> fail("Decline failed", error.message ?: "Unable to decline request") }
        }
    }

    fun connectToFriend(friend: Friend) {
        val api = controlPlaneApi
        val coordinator = tunnelCoordinator
        if (api == null || coordinator == null) return fail("Engine unavailable", "LINKO connection engine is still initializing.")
        connectionJob?.cancel()
        _uiState.update { it.copy(activeFriend = friend, connectionPhase = ConnectionPhase.Connecting, retryAttempt = 0, failureReason = null, eventMessage = "Connecting to ${friend.name}…") }
        connectionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(connectionPhase = ConnectionPhase.Authenticating, eventMessage = "Authenticating your LINKO session…") }
                val session = try { api.requestAccess(friend.id) } catch (error: Exception) {
                    fail("Authentication failed", readable(error)); return@launch
                }
                _uiState.update { it.copy(connectionPhase = ConnectionPhase.Signaling, eventMessage = "Signaling ${friend.name}…") }
                var connected = false
                repeat(40) { attempt ->
                    if (connected) return@repeat
                    delay(1_500L)
                    _uiState.update { it.copy(retryAttempt = attempt + 1, eventMessage = "Signaling… waiting for tunnel credentials") }
                    try {
                        val config = api.tunnelConfig(session.sessionId)
                        val endpoint = config.optJSONObject("endpoint") ?: return@repeat
                        val host = endpoint.optString("host")
                        val port = endpoint.optInt("port", -1)
                        val keyText = config.optString("key")
                        if (host.isBlank() || port !in 1..65535 || keyText.isBlank()) return@repeat
                        val key = runCatching { java.util.Base64.getUrlDecoder().decode(keyText) }.getOrNull() ?: return@repeat
                        if (key.size != 32) return@repeat
                        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Establishing, eventMessage = "Establishing the tunnel…") }
                        coordinator.startVpnTunnel(host, port, session.sessionId, key)
                        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Securing, eventMessage = "Securing the encrypted tunnel…") }
                        delay(250L)
                        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Routing, eventMessage = "Routing traffic through ${friend.name}…") }
                        delay(250L)
                        connected = true
                        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Connected, retryAttempt = 0, eventMessage = "Connected · secure tunnel is active") }
                    } catch (error: LinkoNetworkException) {
                        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Retrying, eventMessage = "Signaling retry ${attempt + 1}…", failureReason = readable(error)) }
                    } catch (error: Exception) {
                        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Retrying, eventMessage = "Tunnel retry ${attempt + 1}…", failureReason = readable(error)) }
                    }
                }
                if (!connected) fail("Signaling timeout", "No usable tunnel credentials arrived before the timeout.")
            } catch (error: Exception) {
                fail("Connection failed", readable(error))
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        tunnelCoordinator?.stopVpnTunnel()
        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Idle, activeFriend = null, retryAttempt = 0, failureReason = null, eventMessage = "Disconnected · tunnel closed") }
    }

    fun onVpnPermissionResult(granted: Boolean) = _uiState.update { it.copy(hasVpnPermission = granted, eventMessage = if (granted) "VPN permission granted." else "VPN permission is required before connecting.") }

    private fun fail(label: String, reason: String) {
        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Failed, failureReason = reason, eventMessage = "$label · $reason") }
    }

    private fun readable(error: Throwable): String = when (error) {
        is LinkoNetworkException -> error.message ?: "Network request failed"
        else -> error.message ?: error.javaClass.simpleName
    }

    private fun startRequestPolling(api: LinkoControlPlaneApi) {
        if (requestPollJob?.isActive == true) return
        requestPollJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                runCatching { api.getPendingProviderRequests().firstOrNull() }.onSuccess { request ->
                    if (request != null) _uiState.update { it.copy(incomingRequest = IncomingRequest(request.id, "LINKO user", "L", request.receiverDeviceId, "REMOTE", "NOW")) }
                }
                delay(3_000L)
            }
        }
    }

    override fun onCleared() { requestPollJob?.cancel(); connectionJob?.cancel(); super.onCleared() }

    companion object {
        private var engineContext: Context? = null
        private var controlPlaneApi: LinkoControlPlaneApi? = null
        private var tunnelCoordinator: TunnelCoordinator? = null
        fun configure(context: Context, api: LinkoControlPlaneApi, coordinator: TunnelCoordinator) { engineContext = context.applicationContext; controlPlaneApi = api; tunnelCoordinator = coordinator }
        fun startEnginePolling(viewModel: LinkShareViewModel) { controlPlaneApi?.let(viewModel::startRequestPolling) }
    }
}
