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
    private val connectivity: ConnectivityManager? = runCatching {
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    }.getOrNull()

    private val _state = MutableStateFlow(LinkoPresenceState())
    val state: StateFlow<LinkoPresenceState> = _state.asStateFlow()
    private var heartbeatJob: Job? = null
    private var callbackRegistered = false

    fun start() {
        publish(LinkoPresencePhase.Initializing, "Starting LINKO presence…")

        if (!callbackRegistered) {
            val registered = runCatching {
                val cm = connectivity ?: return@runCatching false
                cm.registerDefaultNetworkCallback(networkCallback)
                true
            }.getOrDefault(false)
            callbackRegistered = registered
            if (!registered) publish(LinkoPresencePhase.Offline, "LINKO is offline · connectivity monitor unavailable")
        }

        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching {
                    if (!hasInternet()) {
                        publish(LinkoPresencePhase.Offline, "Offline · no network available")
                    } else {
                        api.ensureRegistered()
                        val result = api.touchPresence()
                        _state.update {
                            it.copy(phase = LinkoPresencePhase.Online, detail = "LINKO is online", lastSeenAt = result.lastSeenAt)
                        }
                    }
                }.onFailure {
                    publish(LinkoPresencePhase.Offline, "LINKO is offline · presence unavailable")
                }
                delay(15_000L)
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (callbackRegistered) {
            runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
            callbackRegistered = false
        }
        publish(LinkoPresencePhase.Offline, "LINKO is offline")
    }

    private fun hasInternet(): Boolean {
        return runCatching {
            val cm = connectivity ?: return@runCatching false
            val network = cm.activeNetwork ?: return@runCatching false
            val capabilities = cm.getNetworkCapabilities(network) ?: return@runCatching false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(false)
    }

    private fun publish(phase: LinkoPresencePhase, detail: String) {
        _state.update { it.copy(phase = phase, detail = detail) }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runCatching {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        if (!hasInternet()) return@runCatching
                        api.ensureRegistered()
                        val result = api.touchPresence()
                        _state.update {
                            it.copy(phase = LinkoPresencePhase.Online, detail = "LINKO is online", lastSeenAt = result.lastSeenAt)
                        }
                    }.onFailure {
                        publish(LinkoPresencePhase.Offline, "LINKO is offline · presence unavailable")
                    }
                }
            }
        }

        override fun onLost(network: Network) {
            runCatching {
                if (!hasInternet()) publish(LinkoPresencePhase.Offline, "Offline · network lost")
            }
        }
    }
}

enum class LinkoPresencePhase { Initializing, Online, Offline }

data class LinkoPresenceState(
    val phase: LinkoPresencePhase = LinkoPresencePhase.Initializing,
    val detail: String = "Initializing presence…",
    val lastSeenAt: Long = 0L,
)
