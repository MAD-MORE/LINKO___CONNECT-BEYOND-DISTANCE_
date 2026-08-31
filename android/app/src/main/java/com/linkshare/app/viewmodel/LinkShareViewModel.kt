package com.linkshare.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linkshare.app.audio.ConnectionSoundManager
import com.linkshare.app.data.MockLinkShareRepository
import com.linkshare.app.model.AppMode
import com.linkshare.app.model.ConnectionPhase
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest
import com.linkshare.app.model.UsageStats
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LinkShareViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MockLinkShareRepository()
    private val connectionSoundManager = ConnectionSoundManager(application)

    private val _uiState = MutableStateFlow(
        ConnectionUiState(
            friends = repository.friends(),
            incomingRequest = repository.incomingRequest()
        )
    )
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    private var connectionJob: Job? = null
    private var requestPollJob: Job? = null

    fun setMode(mode: AppMode) = _uiState.update { it.copy(mode = mode, eventMessage = null, failureReason = null) }

    fun toggleHostSharing() {
        val context = engineContext
            ?: return _uiState.update { it.copy(eventMessage = "Engine is still initializing.") }

        if (_uiState.value.hostSharingEnabled) {
            LinkoProviderService.stop(context)
            _uiState.update {
                it.copy(
                    hostSharingEnabled = false,
                    incomingRequest = null,
                    eventMessage = "Sharing stopped."
                )
            }
        } else {
            LinkoProviderService.start(context)
            _uiState.update {
                it.copy(
                    hostSharingEnabled = true,
                    eventMessage = "Sharing is live and waiting for a secure request."
                )
            }
        }
    }

    fun approveIncomingRequest() {
        val api = controlPlaneApi
            ?: return _uiState.update { it.copy(eventMessage = "Engine is still initializing.") }
        val request = _uiState.value.incomingRequest ?: return

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.approveRequest(request.id) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            incomingRequest = null,
                            eventMessage = "Request approved. Preparing the secure tunnel…"
                        )
                    }
                }
                .onFailure { error -> fail("Approval failed", readable(error)) }
        }
    }

    fun denyIncomingRequest() {
        val api = controlPlaneApi
            ?: return _uiState.update { it.copy(eventMessage = "Engine is still initializing.") }
        val request = _uiState.value.incomingRequest ?: return

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.denyRequest(request.id) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            incomingRequest = null,
                            eventMessage = "Request declined."
                        )
                    }
                }
                .onFailure { error -> fail("Decline failed", readable(error)) }
        }
    }

    fun connectToFriend(friend: Friend) {
        if (!friend.isSharing) {
            updateConnectionPhase(ConnectionPhase.Failed) {
                it.copy(
                    activeFriend = friend,
                    eventMessage = "${friend.name} is not sharing right now."
                )
            }
            return
        }

        viewModelScope.launch {
            stopUsageTicker()
            updateConnectionPhase(ConnectionPhase.Requesting) {
                it.copy(
                    activeFriend = friend,
                    retryAttempt = 0,
                    failureReason = null,
                    eventMessage = "Asking ${friend.name} for access..."
                )
            }

            val accepted = repository.requestHostAccess(friend.id)
            if (!accepted) {
                updateConnectionPhase(ConnectionPhase.Failed) {
                    it.copy(eventMessage = "${friend.name} did not approve this request.")
                }
                return@launch
            }

            updateConnectionPhase(ConnectionPhase.Handshaking) {
                it.copy(eventMessage = "Building an encrypted path...")
            }

            var handshakeOk = repository.performWireGuardStyleHandshake()
            if (!handshakeOk) {
                updateConnectionPhase(ConnectionPhase.Retrying) {
                    it.copy(
                        retryAttempt = 1,
                        eventMessage = "Retrying on weak connection..."
                    )
                }
                handshakeOk = repository.retryHandshakeOnWeakSignal()
            }

            if (!handshakeOk) {
                updateConnectionPhase(ConnectionPhase.Failed) {
                    it.copy(
                        failureReason = "Secure handshake failed.",
                        eventMessage = "Unable to establish a secure connection."
                    )
                }
                return@launch
            }

            updateConnectionPhase(ConnectionPhase.Connected) {
                it.copy(
                    usageStats = UsageStats(connectedClients = 1),
                    failureReason = null,
                    eventMessage = "Connected through ${friend.name}."
                )
            }
            startUsageTicker(hostMode = false)
        }
    }

    fun disconnect() {
        stopUsageTicker()
        updateConnectionPhase(ConnectionPhase.Idle) {
            it.copy(
                activeFriend = null,
                retryAttempt = 0,
                usageStats = UsageStats(),
                failureReason = null,
                eventMessage = "Disconnected."
            )
        }
    }

    fun onVpnPermissionResult(granted: Boolean) = _uiState.update {
        it.copy(
            hasVpnPermission = granted,
            eventMessage = if (granted) {
                "VPN permission granted."
            } else {
                "VPN permission is required before connecting."
            }
        )
    }

    private fun fail(label: String, reason: String) {
        _uiState.update {
            it.copy(
                connectionPhase = ConnectionPhase.Failed,
                failureReason = reason,
                eventMessage = "$label · $reason"
            )
        }
    }

    private fun readable(error: Throwable): String = when (error) {
        is LinkoNetworkException -> error.message ?: "Network request failed"
        else -> error.message ?: error.javaClass.simpleName
    }

    private fun startRequestPolling(api: LinkoControlPlaneApi) {
        if (requestPollJob?.isActive == true) return

        requestPollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { api.getPendingProviderRequests().firstOrNull() }
                    .onSuccess { request ->
                        if (request != null) {
                            _uiState.update {
                                it.copy(
                                    incomingRequest = IncomingRequest(
                                        request.id,
                                        "LINKO user",
                                        "L",
                                        request.receiverDeviceId,
                                        "REMOTE",
                                        "NOW"
                                    )
                                )
                            }
                        }
                    }
                delay(3_000L)
            }
        }
    }

    private fun startUsageTicker(hostMode: Boolean) {
        stopUsageTicker()
        connectionJob = viewModelScope.launch {
            while (isActive && _uiState.value.connectionPhase == ConnectionPhase.Connected) {
                delay(1_000L)
                _uiState.update { current ->
                    val stats = current.usageStats
                    current.copy(
                        usageStats = stats.copy(
                            sessionSeconds = stats.sessionSeconds + 1,
                            connectedClients = if (hostMode) {
                                maxOf(1, stats.connectedClients)
                            } else {
                                1
                            }
                        )
                    )
                }
            }
        }
    }

    private fun stopUsageTicker() {
        connectionJob?.cancel()
        connectionJob = null
    }

    companion object {
        private var engineContext: Context? = null
        private var controlPlaneApi: LinkoControlPlaneApi? = null
        private var tunnelCoordinator: TunnelCoordinator? = null

        fun configure(
            context: Context,
            api: LinkoControlPlaneApi,
            coordinator: TunnelCoordinator
        ) {
            engineContext = context.applicationContext
            controlPlaneApi = api
            tunnelCoordinator = coordinator
        }

        fun startEnginePolling(viewModel: LinkShareViewModel) {
            controlPlaneApi?.let(viewModel::startRequestPolling)
        }
    }

    private fun updateConnectionPhase(
        phase: ConnectionPhase,
        update: (ConnectionUiState) -> ConnectionUiState
    ) {
        val previousPhase = _uiState.value.connectionPhase
        _uiState.update { current -> update(current).copy(connectionPhase = phase) }
        connectionSoundManager.onConnectionPhaseChanged(previousPhase, phase)
    }

    override fun onCleared() {
        requestPollJob?.cancel()
        stopUsageTicker()
        connectionSoundManager.release()
        super.onCleared()
    }
}
