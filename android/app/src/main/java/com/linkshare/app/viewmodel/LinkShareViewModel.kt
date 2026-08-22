package com.linkshare.app.viewmodel

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linkshare.app.data.HttpSignalingRepository
import com.linkshare.app.data.ProductionLinkShareRepository
import com.linkshare.app.data.SignalingConfig
import com.linkshare.app.data.MockLinkShareRepository
import com.linkshare.app.model.AppMode
import com.linkshare.app.model.ConnectionPhase
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest
import com.linkshare.app.model.PrototypeScreen
import com.linkshare.app.model.UsageStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LinkShareViewModel(application: Application) : AndroidViewModel(application) {
    private val demoRepository = MockLinkShareRepository()
    private val productionRepository = ProductionLinkShareRepository(
        HttpSignalingRepository(SignalingConfig.BASE_URL) { SignalingConfig.API_TOKEN }
    )
    private val deviceId = "android:${Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()}"

    private val _uiState = MutableStateFlow(
        ConnectionUiState(friends = demoRepository.friends())
    )
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private var usageJob: Job? = null
    private var activeRequestId: String? = null
    private var activeSessionId: String? = null

    init {
        refreshIncomingRequests()
    }

    fun navigateTo(screen: PrototypeScreen) {
        _uiState.update { it.copy(screen = screen, eventMessage = null) }
    }

    fun setMode(mode: AppMode) {
        _uiState.update { it.copy(mode = mode, screen = PrototypeScreen.HomeEngine, eventMessage = null) }
        if (mode == AppMode.Host) refreshIncomingRequests()
    }

    fun openFriends() = navigateTo(PrototypeScreen.Friends)
    fun openSettings() = navigateTo(PrototypeScreen.Settings)
    fun openHistory() = navigateTo(PrototypeScreen.SessionHistory)
    fun openUsage() = navigateTo(PrototypeScreen.Usage)
    fun openNetworkQuality() = navigateTo(PrototypeScreen.NetworkQuality)

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
        refreshIncomingRequests()
    }

    fun approveIncomingRequest() {
        val requestId = _uiState.value.incomingRequest?.id ?: return
        viewModelScope.launch {
            try {
                productionRepository.approve(requestId)
                _uiState.update {
                    it.copy(
                        screen = PrototypeScreen.ProviderLiveUsage,
                        incomingRequest = null,
                        hostSharingEnabled = true,
                        usageStats = it.usageStats.copy(connectedClients = 1),
                        eventMessage = "Request approved. Waiting for the receiver to establish transport."
                    )
                }
                startUsageTicker(hostMode = true)
            } catch (error: Exception) {
                _uiState.update { it.copy(eventMessage = error.message ?: "Approval failed") }
            }
        }
    }

    fun denyIncomingRequest() {
        val requestId = _uiState.value.incomingRequest?.id ?: return
        viewModelScope.launch {
            try {
                productionRepository.deny(requestId)
                _uiState.update {
                    it.copy(
                        screen = PrototypeScreen.ProviderIncoming,
                        incomingRequest = null,
                        eventMessage = "Request denied. Nothing was shared."
                    )
                }
            } catch (error: Exception) {
                _uiState.update { it.copy(eventMessage = error.message ?: "Request denial failed") }
            }
        }
    }

    fun refreshIncomingRequests() {
        viewModelScope.launch {
            try {
                val pending = productionRepository.pending(deviceId)
                val item = pending.firstOrNull()
                val incoming = item?.let {
                    IncomingRequest(
                        id = it.getString("id"),
                        friendName = it.optString("receiver_id", "LINKO user"),
                        initials = "LK",
                        deviceName = "LINKO device",
                        distanceLabel = "Remote",
                        requestedAtLabel = it.optString("created_at", "Just now")
                    )
                }
                _uiState.update {
                    it.copy(
                        incomingRequest = incoming,
                        screen = if (incoming != null) PrototypeScreen.ProviderIncoming else it.screen
                    )
                }
            } catch (error: Exception) {
                _uiState.update { it.copy(eventMessage = error.message ?: "Unable to refresh requests") }
            }
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
                    eventMessage = "Requesting access from ${friend.name}..."
                )
            }

            try {
                activeRequestId = productionRepository.request(deviceId, friend)
                _uiState.update { it.copy(screen = PrototypeScreen.RxWaiting, eventMessage = "Waiting for ${friend.name} to approve the request...") }

                repeat(60) {
                    delay(1_000)
                    val requestId = activeRequestId ?: return@repeat
                    val status = productionRepository.status(requestId)
                    when (status.optString("status")) {
                        "approved" -> {
                            val session = productionRepository.createSession(requestId)
                            activeSessionId = session.getString("id")
                            _uiState.update {
                                it.copy(
                                    screen = PrototypeScreen.RxApproved,
                                    connectionPhase = ConnectionPhase.Handshaking,
                                    eventMessage = "Request approved. Negotiating the secure transport..."
                                )
                            }
                            _uiState.update { it.copy(screen = PrototypeScreen.RxConnecting) }
                            return@repeat
                        }
                        "denied" -> {
                            _uiState.update {
                                it.copy(
                                    screen = PrototypeScreen.SessionExpired,
                                    connectionPhase = ConnectionPhase.Failed,
                                    eventMessage = "${friend.name} denied the request."
                                )
                            }
                            return@repeat
                        }
                        "expired" -> {
                            _uiState.update {
                                it.copy(
                                    screen = PrototypeScreen.SessionExpired,
                                    connectionPhase = ConnectionPhase.Failed,
                                    eventMessage = "The connection request expired."
                                )
                            }
                            return@repeat
                        }
                    }
                }

                if (_uiState.value.connectionPhase == ConnectionPhase.Handshaking) {
                    _uiState.update {
                        it.copy(
                            screen = PrototypeScreen.RxConnecting,
                            eventMessage = "Control-plane session created. Peer transport is still required before traffic can flow."
                        )
                    }
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        screen = PrototypeScreen.ConnectionLost,
                        connectionPhase = ConnectionPhase.Failed,
                        eventMessage = error.message ?: "Unable to create a LINKO session."
                    )
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            activeSessionId?.let { sessionId ->
                runCatching { productionRepository.close(sessionId) }
            }
            activeSessionId = null
            activeRequestId = null
        }
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
                eventMessage = if (granted) "VPN permission granted." else "VPN permission is required before connecting."
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
}
