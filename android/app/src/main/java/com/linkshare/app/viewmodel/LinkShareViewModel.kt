package com.linkshare.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkshare.app.data.MockLinkShareRepository
import com.linkshare.app.model.AppMode
import com.linkshare.app.model.ConnectionPhase
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.Friend
import com.linkshare.app.model.PrototypeScreen
import com.linkshare.app.model.UsageStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LinkShareViewModel : ViewModel() {
    private val repository = MockLinkShareRepository()

    private val _uiState = MutableStateFlow(
        ConnectionUiState(
            friends = repository.friends(),
            incomingRequest = repository.incomingRequest()
        )
    )
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private var usageJob: Job? = null

    /** Navigate to a state defined by the frozen prototype contract. */
    fun navigateTo(screen: PrototypeScreen) {
        _uiState.update { it.copy(screen = screen, eventMessage = null) }
    }

    fun setMode(mode: AppMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                screen = PrototypeScreen.HomeEngine,
                eventMessage = null
            )
        }
    }

    fun openFriends() {
        navigateTo(PrototypeScreen.Friends)
    }

    fun openSettings() {
        navigateTo(PrototypeScreen.Settings)
    }

    fun openHistory() {
        navigateTo(PrototypeScreen.SessionHistory)
    }

    fun toggleHostSharing() {
        val next = !_uiState.value.hostSharingEnabled
        _uiState.update {
            it.copy(
                screen = if (next) PrototypeScreen.ProviderSharingActive else PrototypeScreen.HomeEngine,
                hostSharingEnabled = next,
                usageStats = if (next) UsageStats(connectedClients = 0) else UsageStats(),
                eventMessage = if (next) "Your data is available to approved friends." else "Sharing stopped."
            )
        }
        if (next) startUsageTicker(hostMode = true) else stopUsageTicker()
    }

    fun approveIncomingRequest() {
        _uiState.update {
            it.copy(
                screen = PrototypeScreen.ProviderLiveUsage,
                incomingRequest = null,
                hostSharingEnabled = true,
                usageStats = it.usageStats.copy(connectedClients = 1),
                eventMessage = "Kwesi is connected through your phone."
            )
        }
        startUsageTicker(hostMode = true)
    }

    fun denyIncomingRequest() {
        _uiState.update {
            it.copy(
                screen = PrototypeScreen.ProviderIncoming,
                incomingRequest = null,
                eventMessage = "Request denied. Nothing was shared."
            )
        }
    }

    fun connectToFriend(friend: Friend) {
        if (!friend.isSharing) {
            _uiState.update {
                it.copy(
                    screen = PrototypeScreen.RxRelayFallback,
                    connectionPhase = ConnectionPhase.Failed,
                    activeFriend = friend,
                    eventMessage = "${friend.name} is not sharing right now."
                )
            }
            return
        }

        viewModelScope.launch {
            stopUsageTicker()
            _uiState.update {
                it.copy(
                    screen = PrototypeScreen.RxRequest,
                    activeFriend = friend,
                    connectionPhase = ConnectionPhase.Requesting,
                    retryAttempt = 0,
                    eventMessage = "Asking ${friend.name} for access..."
                )
            }

            val accepted = repository.requestHostAccess(friend.id)
            if (!accepted) {
                _uiState.update {
                    it.copy(
                        screen = PrototypeScreen.SessionExpired,
                        connectionPhase = ConnectionPhase.Failed,
                        eventMessage = "${friend.name} did not approve this request."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    screen = PrototypeScreen.RxApproved,
                    connectionPhase = ConnectionPhase.Handshaking,
                    eventMessage = "Building an encrypted path..."
                )
            }

            delay(250)
            _uiState.update { it.copy(screen = PrototypeScreen.RxConnecting) }

            val handshakeOk = repository.performWireGuardStyleHandshake()
            if (!handshakeOk) {
                _uiState.update {
                    it.copy(
                        screen = PrototypeScreen.RxRelayFallback,
                        connectionPhase = ConnectionPhase.Retrying,
                        retryAttempt = 1,
                        eventMessage = "Direct path unavailable. Trying relay fallback..."
                    )
                }
                repository.retryHandshakeOnWeakSignal()
            } else {
                _uiState.update { it.copy(screen = PrototypeScreen.RxDirectPath) }
            }

            _uiState.update {
                it.copy(
                    screen = PrototypeScreen.Connected,
                    connectionPhase = ConnectionPhase.Connected,
                    usageStats = UsageStats(connectedClients = 1),
                    eventMessage = "Connected through ${friend.name}."
                )
            }
            startUsageTicker(hostMode = false)
        }
    }

    fun disconnect() {
        stopUsageTicker()
        _uiState.update {
            it.copy(
                screen = PrototypeScreen.HomeEngine,
                connectionPhase = ConnectionPhase.Idle,
                activeFriend = null,
                retryAttempt = 0,
                usageStats = UsageStats(),
                eventMessage = "Disconnected."
            )
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                screen = if (granted) PrototypeScreen.HomeEngine else PrototypeScreen.Permissions,
                hasVpnPermission = granted,
                eventMessage = if (granted) "VPN permission granted. LINKO can protect the tunnel." else "VPN permission is required before connecting."
            )
        }
    }

    fun openUsage() {
        navigateTo(PrototypeScreen.Usage)
    }

    fun openNetworkQuality() {
        navigateTo(PrototypeScreen.NetworkQuality)
    }

    private fun startUsageTicker(hostMode: Boolean) {
        usageJob?.cancel()
        usageJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _uiState.update {
                    val sentStep = if (hostMode) 184_320L else 42_240L
                    val receivedStep = if (hostMode) 51_200L else 208_896L
                    it.copy(
                        usageStats = it.usageStats.copy(
                            sessionSeconds = it.usageStats.sessionSeconds + 1,
                            bytesSent = it.usageStats.bytesSent + sentStep,
                            bytesReceived = it.usageStats.bytesReceived + receivedStep
                        )
                    )
                }
            }
        }
    }

    private fun stopUsageTicker() {
        usageJob?.cancel()
        usageJob = null
    }
}
