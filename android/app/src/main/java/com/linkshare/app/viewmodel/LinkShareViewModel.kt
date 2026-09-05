package com.linkshare.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linkshare.app.audio.ConnectionSoundManager
import com.linkshare.app.model.AppMode
import com.linkshare.app.model.ConnectionPhase
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.Friend
import com.linkshare.app.model.IncomingRequest
import com.linkshare.app.model.UsageStats
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoControlPlaneApi
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoEngineConnectionState
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

/** Live compatibility ViewModel. Connection state always comes from the real LINKO engine. */
class LinkShareViewModel(application: Application) : AndroidViewModel(application) {
    private val connectionSoundManager = ConnectionSoundManager(application)
    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    private var requestPollJob: Job? = null
    private var usageJob: Job? = null
    private var engineJob: Job? = null

    init {
        engineJob = viewModelScope.launch {
            LinkoEngineBridge.connection.collect { applyEngineSnapshot(it) }
        }
        startRequestPollingIfConfigured()
    }

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

    fun approveIncomingRequest() = LinkoEngineBridge.approvePendingProviderRequest(onState = ::handleEngineCallback)

    fun denyIncomingRequest() = LinkoEngineBridge.denyPendingProviderRequest(onState = ::handleEngineCallback)

    fun connectToFriend(friend: Friend) {
        if (friend.id.isBlank()) return fail("Friend identity is missing")
        _uiState.update { it.copy(activeFriend = friend, retryAttempt = 0, failureReason = null, eventMessage = "Connecting to ${friend.name}…") }
        LinkoEngineBridge.connectToFriend(
            friendUserId = friend.id,
            friendName = friend.name,
            friendId = friend.id,
            onState = ::handleEngineCallback,
        )
    }

    /** Retry the last selected friend using a fresh engine generation/session. */
    fun retryConnection() {
        val friend = _uiState.value.activeFriend
        if (friend == null || friend.id.isBlank()) {
            fail("Select a friend before retrying")
            return
        }
        _uiState.update {
            it.copy(
                connectionPhase = ConnectionPhase.Requesting,
                retryAttempt = it.retryAttempt + 1,
                failureReason = null,
                eventMessage = "Retrying ${friend.name}…",
            )
        }
        LinkoEngineBridge.reconnect(onState = ::handleEngineCallback)
    }

    /** Stop/cancel an in-flight attempt or disconnect an established tunnel. */
    fun stopConnection() {
        LinkoEngineBridge.disconnect()
        stopUsageTicker()
        _uiState.update {
            it.copy(
                activeFriend = null,
                retryAttempt = 0,
                usageStats = UsageStats(),
                failureReason = null,
                connectionPhase = ConnectionPhase.Idle,
                eventMessage = "Connection stopped.",
            )
        }
    }

    fun disconnect() = stopConnection()

    fun onVpnPermissionResult(granted: Boolean) = _uiState.update {
        it.copy(hasVpnPermission = granted, eventMessage = if (granted) "VPN permission granted." else "VPN permission is required before connecting.")
    }

    private fun handleEngineCallback(state: String) {
        if (state.contains("failed", true) || state.contains("error", true)) fail(state.replace('_', ' '))
        else _uiState.update { it.copy(eventMessage = state.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }) }
    }

    private fun applyEngineSnapshot(state: LinkoEngineConnectionState) {
        val phase = when (state.phase) {
            LinkoConnectionPhase.Idle -> ConnectionPhase.Idle
            LinkoConnectionPhase.Connecting,
            LinkoConnectionPhase.Authenticating,
            LinkoConnectionPhase.Signaling -> ConnectionPhase.Requesting
            LinkoConnectionPhase.Establishing,
            LinkoConnectionPhase.Securing,
            LinkoConnectionPhase.Routing -> ConnectionPhase.Handshaking
            LinkoConnectionPhase.Connected -> ConnectionPhase.Connected
            LinkoConnectionPhase.Failed -> ConnectionPhase.Failed
        }
        _uiState.update { current ->
            current.copy(
                connectionPhase = phase,
                eventMessage = state.detail,
                failureReason = state.error,
                usageStats = if (phase == ConnectionPhase.Connected) current.usageStats.copy(connectedClients = if (state.isProvider) maxOf(1, current.usageStats.connectedClients) else 1) else current.usageStats,
            )
        }
        if (phase == ConnectionPhase.Connected) startUsageTicker(state.isProvider) else if (phase != ConnectionPhase.Retrying) stopUsageTicker()
    }

    private fun startRequestPollingIfConfigured() {
        val api = controlPlaneApi ?: return
        if (requestPollJob?.isActive == true) return
        requestPollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { api.getPendingProviderRequests().firstOrNull() }
                    .onSuccess { request ->
                        _uiState.update { current -> current.copy(incomingRequest = request?.let { IncomingRequest(it.id, "LINKO friend", "L", it.receiverDeviceId, "REMOTE", "NOW") }) }
                    }
                delay(3_000L)
            }
        }
    }

    private fun startUsageTicker(hostMode: Boolean) {
        if (usageJob?.isActive == true) return
        usageJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                if (_uiState.value.connectionPhase != ConnectionPhase.Connected) break
                _uiState.update { current ->
                    val stats = current.usageStats
                    current.copy(usageStats = stats.copy(sessionSeconds = stats.sessionSeconds + 1, connectedClients = if (hostMode) maxOf(1, stats.connectedClients) else 1))
                }
            }
        }
    }

    private fun stopUsageTicker() { usageJob?.cancel(); usageJob = null }

    private fun fail(reason: String) {
        _uiState.update { it.copy(connectionPhase = ConnectionPhase.Failed, failureReason = reason, eventMessage = reason) }
    }

    companion object {
        private var engineContext: Context? = null
        private var controlPlaneApi: LinkoControlPlaneApi? = null
        private var tunnelCoordinator: TunnelCoordinator? = null

        fun configure(context: Context, api: LinkoControlPlaneApi, coordinator: TunnelCoordinator) {
            engineContext = context.applicationContext
            controlPlaneApi = api
            tunnelCoordinator = coordinator
        }

        fun startEnginePolling(viewModel: LinkShareViewModel) = viewModel.startRequestPollingIfConfigured()
    }

    override fun onCleared() {
        requestPollJob?.cancel()
        engineJob?.cancel()
        stopUsageTicker()
        connectionSoundManager.release()
        super.onCleared()
    }
}
