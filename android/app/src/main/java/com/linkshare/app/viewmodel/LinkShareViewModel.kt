package com.linkshare.app.viewmodel

import androidx.lifecycle.ViewModel
import com.linkshare.app.model.AppMode
import com.linkshare.app.model.ConnectionPhase
import com.linkshare.app.model.ConnectionUiState
import com.linkshare.app.model.Friend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Production state holder. No sample users or simulated connection/usage data. */
class LinkShareViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    fun setMode(mode: AppMode) {
        _uiState.update { it.copy(mode = mode, eventMessage = null) }
    }

    fun toggleHostSharing() {
        _uiState.update {
            it.copy(
                hostSharingEnabled = !it.hostSharingEnabled,
                eventMessage = if (!it.hostSharingEnabled) "Sharing is ready for a real approved session." else "Sharing stopped."
            )
        }
    }

    fun approveIncomingRequest() {
        _uiState.update { it.copy(eventMessage = "No incoming request is available.") }
    }

    fun denyIncomingRequest() {
        _uiState.update { it.copy(eventMessage = "No incoming request is available.") }
    }

    fun selectFriend(friend: Friend) = connectToFriend(friend)

    fun connectToFriend(friend: Friend) {
        _uiState.update {
            it.copy(
                activeFriend = friend,
                connectionPhase = ConnectionPhase.Failed,
                eventMessage = "Real connection service is not available for this friend yet."
            )
        }
    }

    fun disconnect() {
        _uiState.update {
            it.copy(
                connectionPhase = ConnectionPhase.Idle,
                activeFriend = null,
                retryAttempt = 0,
                eventMessage = "Disconnected."
            )
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasVpnPermission = granted,
                eventMessage = if (granted) "VPN permission granted." else "VPN permission is required before connecting."
            )
        }
    }
}
