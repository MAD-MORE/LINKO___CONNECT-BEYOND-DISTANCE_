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

/** Production connection orchestrator: UI actions are backed by real control-plane and VPN calls. */
class LinkShareViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    private var connectionJob: Job? = null
    private var requestPollJob: Job? = null

    fun setMode(mode: AppMode) = _uiState.update { it.copy(mode = mode, eventMessage = null) }

    fun toggleHostSharing(context: Context) {
        if (_uiState.value.hostSharingEnabled) {
            LinkoProviderService.stop(context)
            _uiState.update { it.copy(hostSharingEnabled = false, incomingRequest = null, eventMessage = "Sharing stopped.") }
        } else {
            LinkoProviderService.start(context)
            _uiState.update { it.copy(hostSharingEnabled = true, eventMessage = "Provider mode is active and waiting for real requests.") }
        }
    }

    fun startIncomingRequestPolling(api: LinkoControlPlaneApi) {
        if (requestPollJob?.isActive == true) return
        requestPollJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                runCatching { api.getPendingProviderRequests().firstOrNull() }.onSuccess { request ->
                    if (request != null) _uiState.update {
                        it.copy(incomingRequest = IncomingRequest(request.id, "LINKO user", "L", request.receiverDeviceId, "REMOTE", "NOW"))
                    }
                }
                delay(3_000L)
            }
        }
    }

    fun approveIncomingRequest(api: LinkoControlPlaneApi) {
        val request = _uiState.value.incomingRequest ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                api.approveRequest(request.id)
                api.transition(request.id, "signaling")
            }.onSuccess { _uiState.update { it.copy(incomingRequest = null, eventMessage = "Request approved. Preparing the secure tunnel…") } }
             .onFailure { error -> _uiState.update { it.copy(eventMessage = error.message ?: "Approval failed") } }
        }
    }

    fun denyIncomingRequest(api: LinkoControlPlaneApi) {
        val request = _uiState.value.incomingRequest ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.denyRequest(request.id) }
                .onSuccess { _uiState.update { it.copy(incomingRequest = null, eventMessage = "Request declined.") } }
                .onFailure { error -> _uiState.update { it.copy(eventMessage = error.message ?: "Decline failed") } }
        }
    }

    fun connectToFriend(friend: Friend, api: LinkoControlPlaneApi, tunnelCoordinator: TunnelCoordinator) {
        connectionJob?.cancel()
        _uiState.update { it.copy(activeFriend = friend, connectionPhase = ConnectionPhase.Requesting, retryAttempt = 0, eventMessage = "Requesting access from ${friend.name}…") }
        connectionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = api.requestAccess(friend.id)
                _uiState.update { it.copy(eventMessage = "Waiting for ${friend.name} to approve the request…") }
                var connected = false
                repeat(40) { attempt ->
                    delay(1_500L)
                    if (connected) return@repeat
                    try {
                        val config = api.tunnelConfig(session.id)
                        val endpoint = config.optJSONObject("endpoint") ?: return@repeat
                        val host = endpoint.optString("host")
                        val port = endpoint.optInt("port", -1)
                        val keyText = config.optString("key")
                        if (host.isBlank() || port !in 1..65535 || keyText.isBlank()) return@repeat
                        val key = runCatching { java.util.Base64.getUrlDecoder().decode(keyText) }.getOrNull() ?: return@repeat
                        if (key.size != 32) return@repeat
                        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Handshaking, retryAttempt = attempt + 1, eventMessage = "Approved. Starting the encrypted VPN tunnel…") }
                        tunnelCoordinator.startVpnTunnel(host, port, session.id, key)
                        connected = true
                        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Connected, retryAttempt = 0, eventMessage = "Connected. Internet traffic is routed through ${friend.name}.") }
                    } catch (_: LinkoNetworkException) {
                        // The session is still waiting for provider approval/configuration.
                    }
                }
                if (!connected) throw IllegalStateException("connection_timeout")
            } catch (error: Exception) {
                _uiState.update { it.copy(connectionPhase = ConnectionPhase.Failed, eventMessage = error.message ?: "Connection failed") }
            }
        }
    }

    fun connectToFriend(friend: Friend) = _uiState.update { it.copy(activeFriend = friend, connectionPhase = ConnectionPhase.Failed, eventMessage = "Connection service is not initialized.") }

    fun disconnect(tunnelCoordinator: TunnelCoordinator) {
        connectionJob?.cancel()
        tunnelCoordinator.stopVpnTunnel()
        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Idle, activeFriend = null, retryAttempt = 0, eventMessage = "Disconnected. The VPN tunnel is closed.") }
    }

    fun disconnect() = _uiState.update { it.copy(connectionPhase = ConnectionPhase.Idle, activeFriend = null, retryAttempt = 0, eventMessage = "Disconnected.") }

    fun onVpnPermissionResult(granted: Boolean) = _uiState.update {
        it.copy(hasVpnPermission = granted, eventMessage = if (granted) "VPN permission granted." else "VPN permission is required before connecting.")
    }

    override fun onCleared() {
        requestPollJob?.cancel()
        connectionJob?.cancel()
        super.onCleared()
    }
}
