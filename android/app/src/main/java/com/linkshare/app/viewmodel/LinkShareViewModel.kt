package com.linkshare.app.viewmodel

import android.app.Application
import android.provider.Settings
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linkshare.app.data.HttpSignalingRepository
import com.linkshare.app.data.MockLinkShareRepository
import com.linkshare.app.data.ProductionLinkShareRepository
import com.linkshare.app.data.SignalingConfig
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
import org.json.JSONObject

class LinkShareViewModel(application: Application) : AndroidViewModel(application) {
    private val demoRepository = MockLinkShareRepository()
    private val productionRepository = ProductionLinkShareRepository(
        HttpSignalingRepository(SignalingConfig.BASE_URL) { SignalingConfig.API_TOKEN }
    )
    private val deviceId = "android:${Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()}"
    private val _uiState = MutableStateFlow(ConnectionUiState(friends = demoRepository.friends()))
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    private var usageJob: Job? = null
    private var activeRequestId: String? = null
    private var activeSessionId: String? = null

    init { refreshIncomingRequests() }
    fun navigateTo(screen: PrototypeScreen) = _uiState.update { it.copy(screen = screen, eventMessage = null) }
    fun setMode(mode: AppMode) { _uiState.update { it.copy(mode = mode, screen = PrototypeScreen.HomeEngine, eventMessage = null) }; if (mode == AppMode.Host) refreshIncomingRequests() }
    fun openFriends() = navigateTo(PrototypeScreen.Friends)
    fun openSettings() = navigateTo(PrototypeScreen.Settings)
    fun openHistory() = navigateTo(PrototypeScreen.SessionHistory)
    fun openUsage() = navigateTo(PrototypeScreen.Usage)
    fun openNetworkQuality() = navigateTo(PrototypeScreen.NetworkQuality)

    fun toggleHostSharing() {
        val next = !_uiState.value.hostSharingEnabled
        _uiState.update { it.copy(screen = if (next) PrototypeScreen.ProviderSharingActive else PrototypeScreen.HomeEngine, hostSharingEnabled = next, usageStats = if (next) UsageStats(connectedClients = 0) else UsageStats(), eventMessage = if (next) "Your data is available to approved friends." else "Sharing stopped.") }
        if (next) startUsageTicker(true) else stopUsageTicker()
        refreshIncomingRequests()
    }

    fun approveIncomingRequest() {
        val requestId = _uiState.value.incomingRequest?.id ?: return
        viewModelScope.launch {
            try {
                productionRepository.approve(requestId, deviceId)
                _uiState.update { it.copy(screen = PrototypeScreen.ProviderLiveUsage, incomingRequest = null, hostSharingEnabled = true, usageStats = it.usageStats.copy(connectedClients = 1), eventMessage = "Request approved. Waiting for the receiver to establish transport.") }
                startUsageTicker(true)
            } catch (error: Exception) { _uiState.update { it.copy(eventMessage = error.message ?: "Approval failed") } }
        }
    }

    fun denyIncomingRequest() {
        val requestId = _uiState.value.incomingRequest?.id ?: return
        viewModelScope.launch {
            try {
                productionRepository.deny(requestId, deviceId)
                _uiState.update { it.copy(screen = PrototypeScreen.ProviderIncoming, incomingRequest = null, eventMessage = "Request denied. Nothing was shared.") }
            } catch (error: Exception) { _uiState.update { it.copy(eventMessage = error.message ?: "Request denial failed") } }
        }
    }

    fun refreshIncomingRequests() {
        viewModelScope.launch {
            runCatching { productionRepository.pending(deviceId) }
                .onSuccess { pending ->
                    val item = pending.firstOrNull()
                    val incoming = item?.let { IncomingRequest(it.getString("id"), it.optString("receiver_id", "LINKO user"), "LK", "LINKO device", "Remote", it.optString("created_at", "Just now")) }
                    _uiState.update { it.copy(incomingRequest = incoming, screen = if (incoming != null) PrototypeScreen.ProviderIncoming else it.screen) }
                }
                .onFailure { error -> _uiState.update { it.copy(eventMessage = error.message ?: "Unable to refresh requests") } }
        }
    }

    fun connectToFriend(friend: Friend) {
        if (!friend.isSharing) {
            _uiState.update { it.copy(screen = PrototypeScreen.RxRelayFallback, connectionPhase = ConnectionPhase.Failed, activeFriend = friend, eventMessage = "${friend.name} is not sharing right now.") }
            return
        }
        viewModelScope.launch {
            stopUsageTicker()
            _uiState.update { it.copy(screen = PrototypeScreen.RxRequest, activeFriend = friend, connectionPhase = ConnectionPhase.Requesting, retryAttempt = 0, eventMessage = "Requesting access from ${friend.name}...") }
            try {
                activeRequestId = productionRepository.request(deviceId, friend)
                _uiState.update { it.copy(screen = PrototypeScreen.RxWaiting, eventMessage = "Waiting for ${friend.name} to approve the request...") }
                repeat(60) {
                    delay(1_000)
                    val requestId = activeRequestId ?: return@repeat
                    when (productionRepository.status(requestId).optString("status")) {
                        "approved" -> {
                            val session = productionRepository.createSession(requestId)
                            val sessionId = session.getString("id")
                            activeSessionId = sessionId
                            val relayEndpoint = session.optString("relayEndpoint", session.optString("relay_endpoint", ""))
                            val relayToken = session.optString("relayToken", session.optString("relay_token", ""))
                            val peerId = session.optString("peerId", session.optString("peer_id", friend.id))
                            val encodedKey = session.optString("sessionKey", session.optString("session_key", ""))
                            val sessionKey = decodeSessionKey(encodedKey)
                            _uiState.update { it.copy(sessionId = sessionId, peerId = peerId, sessionKey = sessionKey, relayEndpoint = relayEndpoint, relayToken = relayToken, screen = PrototypeScreen.RxApproved, connectionPhase = ConnectionPhase.Handshaking, eventMessage = "Request approved. Negotiating the secure transport...") }
                            _uiState.update { it.copy(screen = PrototypeScreen.RxConnecting, eventMessage = "Control-plane session created. Transport credentials are ready when the relay is configured.") }
                            return@repeat
                        }
                        "denied" -> { _uiState.update { it.copy(screen = PrototypeScreen.SessionExpired, connectionPhase = ConnectionPhase.Failed, eventMessage = "${friend.name} denied the request.") }; return@repeat }
                        "expired" -> { _uiState.update { it.copy(screen = PrototypeScreen.SessionExpired, connectionPhase = ConnectionPhase.Failed, eventMessage = "The connection request expired.") }; return@repeat }
                    }
                }
            } catch (error: Exception) { _uiState.update { it.copy(screen = PrototypeScreen.ConnectionLost, connectionPhase = ConnectionPhase.Failed, eventMessage = error.message ?: "Unable to create a LINKO session.") } }
        }
    }

    fun reportTunnelUnavailable() {
        _uiState.update { it.copy(screen = PrototypeScreen.RxRelayFallback, connectionPhase = ConnectionPhase.Failed, eventMessage = "The session is missing valid relay credentials. Configure the LINKO signaling/relay service before starting the VPN tunnel.") }
    }

    fun disconnect() {
        viewModelScope.launch { activeSessionId?.let { runCatching { productionRepository.close(it) } }; activeSessionId = null; activeRequestId = null }
        stopUsageTicker()
        _uiState.update { it.copy(screen = PrototypeScreen.HomeEngine, connectionPhase = ConnectionPhase.Idle, activeFriend = null, retryAttempt = 0, usageStats = UsageStats(), sessionId = "", peerId = "", sessionKey = ByteArray(32), relayEndpoint = "", relayToken = "", eventMessage = "Disconnected.") }
    }

    fun onVpnPermissionResult(granted: Boolean) { _uiState.update { it.copy(screen = if (granted) PrototypeScreen.HomeEngine else PrototypeScreen.Permissions, hasVpnPermission = granted, eventMessage = if (granted) "VPN permission granted." else "VPN permission is required before connecting.") } }

    private fun decodeSessionKey(encoded: String): ByteArray {
        if (encoded.isBlank()) return ByteArray(32)
        return runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrDefault(ByteArray(32)).let { if (it.size == 32) it else ByteArray(32) }
    }

    private fun startUsageTicker(hostMode: Boolean) {
        usageJob?.cancel()
        usageJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _uiState.update { val sentStep = if (hostMode) 184_320L else 42_240L; val receivedStep = if (hostMode) 51_200L else 208_896L; it.copy(usageStats = it.usageStats.copy(sessionSeconds = it.usageStats.sessionSeconds + 1, bytesSent = it.usageStats.bytesSent + sentStep, bytesReceived = it.usageStats.bytesReceived + receivedStep)) }
            }
        }
    }
    private fun stopUsageTicker() { usageJob?.cancel(); usageJob = null }
}
