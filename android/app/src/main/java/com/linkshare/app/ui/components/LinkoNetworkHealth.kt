package com.linkshare.app.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.ui.theme.Card
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextPrimary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


enum class LinkoNetworkHealthLevel { EXCELLENT, GOOD, WEAK, POOR, LOST }

data class LinkoNetworkHealthSnapshot(
    val score: Int = 0,
    val level: LinkoNetworkHealthLevel = LinkoNetworkHealthLevel.LOST,
    val available: Boolean = false,
    val validated: Boolean = false,
    val downKbps: Int = 0,
    val upKbps: Int = 0,
    val latencyMs: Int = 0,
    val message: String = "Connection lost",
)

object LinkoNetworkHealthMonitor {
    private val _snapshot = MutableStateFlow(LinkoNetworkHealthSnapshot())
    val snapshot: StateFlow<LinkoNetworkHealthSnapshot> = _snapshot.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var cm: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        cm = app.getSystemService(ConnectivityManager::class.java)
        val manager = cm ?: return
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { refresh(manager) }
            override fun onLost(network: Network) { refresh(manager) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { refresh(manager) }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback!!) }
        job = scope.launch {
            while (true) {
                refresh(manager)
                delay(2500L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        callback?.let { runCatching { cm?.unregisterNetworkCallback(it) } }
        callback = null
    }

    private fun refresh(manager: ConnectivityManager) {
        val network = manager.activeNetwork
        val capabilities = network?.let(manager::getNetworkCapabilities)
        val available = network != null && capabilities != null
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val down = capabilities?.linkDownstreamBandwidthKbps ?: 0
        val up = capabilities?.linkUpstreamBandwidthKbps ?: 0
        val connection = LinkoEngineBridge.connection.value
        val latency = connection.latencyMs.takeIf { it > 0 } ?: 0

        var score = when {
            !available -> 0
            !validated -> 25
            else -> 55
        }
        if (down >= 20_000) score += 18 else if (down >= 8_000) score += 12 else if (down >= 2_000) score += 6
        if (up >= 10_000) score += 12 else if (up >= 3_000) score += 8 else if (up >= 1_000) score += 4
        if (latency in 1..60) score += 15 else if (latency in 61..120) score += 9 else if (latency in 121..250) score += 3
        if (connection.phase == LinkoConnectionPhase.Failed) score = minOf(score, 20)
        score = score.coerceIn(0, 100)

        val level = when {
            score >= 85 -> LinkoNetworkHealthLevel.EXCELLENT
            score >= 65 -> LinkoNetworkHealthLevel.GOOD
            score >= 45 -> LinkoNetworkHealthLevel.WEAK
            score > 0 -> LinkoNetworkHealthLevel.POOR
            else -> LinkoNetworkHealthLevel.LOST
        }
        val message = when (level) {
            LinkoNetworkHealthLevel.EXCELLENT -> "Connection is strong"
            LinkoNetworkHealthLevel.GOOD -> "Connection is good"
            LinkoNetworkHealthLevel.WEAK -> "Connection is slowing down"
            LinkoNetworkHealthLevel.POOR -> "Your connection is weak"
            LinkoNetworkHealthLevel.LOST -> "Connection lost"
        }
        _snapshot.value = LinkoNetworkHealthSnapshot(score, level, available, validated, down, up, latency, message)
    }
}

fun LinkoNetworkHealthSnapshot.ringDurationMs(): Int = when (level) {
    LinkoNetworkHealthLevel.EXCELLENT -> 500
    LinkoNetworkHealthLevel.GOOD -> 760
    LinkoNetworkHealthLevel.WEAK -> 1200
    LinkoNetworkHealthLevel.POOR -> 1900
    LinkoNetworkHealthLevel.LOST -> 2600
}

@Composable
fun LinkoNetworkHealthBanner() {
    val context = LocalContext.current
    val health by LinkoNetworkHealthMonitor.snapshot.collectAsState()
    LaunchedEffect(Unit) { LinkoNetworkHealthMonitor.start(context) }
    if (health.available && health.score < 45) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .background(Card)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("•", color = Red, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text("${health.message}. LINKO is trying to keep you connected.", color = TextPrimary, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
