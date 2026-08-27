package com.linkshare.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Presence is real control-plane reachability, not merely the device data toggle. */
class LinkoPresenceManager(
    context: Context,
    private val api: LinkoDeviceControlApi,
    private val scope: CoroutineScope,
) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(LinkoPresenceState())
    val state: StateFlow<LinkoPresenceState> = _state.asStateFlow()
    private var heartbeatJob: Job? = null
    private var callbackRegistered = false

    fun start() {
        if (!callbackRegistered) {
            connectivity.registerDefaultNetworkCallback(networkCallback)
            callbackRegistered = true
        }
        publish(LinkoPresencePhase.Initializing, "Starting LINKO presence…")
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (hasInternet()) {
                    runCatching {
                        api.ensureRegistered()
                        val result = api.touchPresence()
                        _state.update { it.copy(phase = LinkoPresencePhase.Online, detail = "LINKO is online", lastSeenAt = result.lastSeenAt) }
                    }.onFailure {
                        publish(LinkoPresencePhase.Offline, "LINKO is offline · presence unavailable")
                    }
                } else {
                    publish(LinkoPresencePhase.Offline, "Offline · no network available")
                }
                delay(15_000L)
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (callbackRegistered) {
            runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
            callbackRegistered = false
        }
        publish(LinkoPresencePhase.Offline, "LINKO is offline")
    }

    private fun hasInternet(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun publish(phase: LinkoPresencePhase, detail: String) {
        _state.update { it.copy(phase = phase, detail = detail) }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch(Dispatchers.IO) { if (hasInternet()) runCatching { api.touchPresence() } }
        }

        override fun onLost(network: Network) {
            if (!hasInternet()) publish(LinkoPresencePhase.Offline, "Offline · network lost")
        }
    }
}

enum class LinkoPresencePhase { Initializing, Online, Offline }

data class LinkoPresenceState(
    val phase: LinkoPresencePhase = LinkoPresencePhase.Initializing,
    val detail: String = "Initializing presence…",
    val lastSeenAt: Long = 0L,
)
