package com.linkshare.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linkshare.app.audio.ConnectionSoundManager
import com.linkshare.app.data.MockLinkShareRepository
import com.linkshare.app.model.AppMode
import com.linkshare.app.model.ConnectionPhase
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.Friend
import com.linkshare.app.model.UsageStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private var usageJob: Job? = null

    fun setMode(mode: AppMode) {
        _uiState.update { it.copy(mode = mode, eventMessage = null) }
    }

    fun toggleHostSharing() {
        val next = !_uiState.value.hostSharingEnabled
        _uiState.update {
            it.copy(
                hostSharingEnabled = next,
                usageStats = if (next) UsageStats(connectedClients = 1) else UsageStats(),
                eventMessage = if (next) "Your data is available to approved friends." else "Sharing stopped."
            )
        }
        if (next) startUsageTicker(hostMode = true) else stopUsageTicker()
    }

    fun approveIncomingRequest() {
        _uiState.update {
            it.copy(
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
                incomingRequest = null,
                eventMessage = "Request denied. Nothing was shared."
            )
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
                    eventMessage = "Asking ${friend.name} for access..."
                )
            }

            val accepted = repository.requestHostAccess(friend.id)
            if (!accepted) {
                updateConnectionPhase(ConnectionPhase.Failed) {
                    it.copy(
                        eventMessage = "${friend.name} did not approve this request."
                    )
                }
                return@launch
            }

            updateConnectionPhase(ConnectionPhase.Handshaking) {
                it.copy(
                    eventMessage = "Building an encrypted path..."
                )
            }

            val handshakeOk = repository.performWireGuardStyleHandshake()
            if (!handshakeOk) {
                updateConnectionPhase(ConnectionPhase.Retrying) {
                    it.copy(
                        retryAttempt = 1,
                        eventMessage = "Retrying on weak connection..."
                    )
                }
                repository.retryHandshakeOnWeakSignal()
            }

            updateConnectionPhase(ConnectionPhase.Connected) {
                it.copy(
                    usageStats = UsageStats(connectedClients = 1),
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
                eventMessage = "Disconnected."
            )
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasVpnPermission = granted,
                eventMessage = if (granted) "VPN permission granted. LinkShare can protect the tunnel." else "VPN permission is required before connecting."
            )
        }
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

    private fun updateConnectionPhase(
        phase: ConnectionPhase,
        update: (ConnectionUiState) -> ConnectionUiState
    ) {
        val previousPhase = _uiState.value.connectionPhase
        _uiState.update { current -> update(current).copy(connectionPhase = phase) }
        connectionSoundManager.onConnectionPhaseChanged(previousPhase, phase)
    }

    override fun onCleared() {
        stopUsageTicker()
        connectionSoundManager.release()
        super.onCleared()
    }
}
