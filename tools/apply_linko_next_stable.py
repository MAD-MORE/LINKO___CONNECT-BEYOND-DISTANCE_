from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding='utf-8')


def write(rel, text):
    p = ROOT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')

# 1) Real network-health sensing shared by the UI.
write('android/app/src/main/java/com/linkshare/app/ui/components/LinkoNetworkHealth.kt', r'''package com.linkshare.app.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.ui.theme.Card
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


enum class LinkoNetworkHealthLevel { EXCELLENT, GOOD, WEAK, POOR, LOST }

data class LinkoNetworkHealth(
    val score: Int = 0,
    val level: LinkoNetworkHealthLevel = LinkoNetworkHealthLevel.LOST,
    val available: Boolean = false,
    val validated: Boolean = false,
    val downKbps: Int = 0,
    val upKbps: Int = 0,
    val latencyMs: Int = 0,
    val message: String = "No connection",
    val measuredAtElapsedMs: Long = 0L,
)

object LinkoNetworkHealthMonitor {
    private val _snapshot = MutableStateFlow(LinkoNetworkHealth())
    val snapshot: StateFlow<LinkoNetworkHealth> = _snapshot.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var connectivityManager: ConnectivityManager? = null

    fun start(context: Context) {
        if (monitorJob?.isActive == true) return
        val app = context.applicationContext
        connectivityManager = app.getSystemService(ConnectivityManager::class.java)
        val cm = connectivityManager ?: return
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { refresh(cm) }
            override fun onLost(network: Network) { refresh(cm) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { refresh(cm) }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback!!) }
        monitorJob = scope.launch {
            while (true) {
                refresh(cm)
                delay(2500L)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        callback?.let { cb -> runCatching { connectivityManager?.unregisterNetworkCallback(cb) } }
        callback = null
    }

    private fun refresh(cm: ConnectivityManager) {
        val network = cm.activeNetwork
        val capabilities = network?.let(cm::getNetworkCapabilities)
        val available = network != null && capabilities != null
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val down = capabilities?.linkDownstreamBandwidthKbps ?: 0
        val up = capabilities?.linkUpstreamBandwidthKbps ?: 0
        val engine = LinkoEngineBridge.connection.value
        val latency = engine.latencyMs.takeIf { it > 0 } ?: 0

        var score = when {
            !available -> 0
            !validated -> 25
            else -> 55
        }
        if (down >= 20_000) score += 18 else if (down >= 8_000) score += 12 else if (down >= 2_000) score += 6
        if (up >= 10_000) score += 12 else if (up >= 3_000) score += 8 else if (up >= 1_000) score += 4
        if (latency in 1..60) score += 15 else if (latency in 61..120) score += 9 else if (latency in 121..250) score += 3
        if (engine.phase == LinkoConnectionPhase.Failed) score = minOf(score, 20)
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
        _snapshot.value = LinkoNetworkHealth(score, level, available, validated, down, up, latency, message, SystemClock.elapsedRealtime())
    }
}

fun LinkoNetworkHealth.ringDurationMs(): Int = when {
    level == LinkoNetworkHealthLevel.EXCELLENT -> 520
    level == LinkoNetworkHealthLevel.GOOD -> 760
    level == LinkoNetworkHealthLevel.WEAK -> 1200
    level == LinkoNetworkHealthLevel.POOR -> 1900
    else -> 2600
}

@Composable
fun LinkoNetworkHealthBanner() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by LinkoNetworkHealthMonitor.snapshot.collectAsState()
    LaunchedEffect(Unit) { LinkoNetworkHealthMonitor.start(context) }
    if (state.available && state.score < 45) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .background(Card.copy(alpha = 0.98f))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text("•", color = Red, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "${state.message}. LINKO is trying to keep you connected.",
                color = TextPrimary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}
''')

# 2) Ring now follows measured network health, with no developer labels.
write('android/app/src/main/java/com/linkshare/app/ui/components/Ring.kt', r'''package com.linkshare.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Ring(
    color: Color,
    size: Dp = 160.dp,
    pulse: Boolean = false,
    idle: Boolean = false,
    label: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val health by LinkoNetworkHealthMonitor.snapshot.collectAsState()
    val duration = health.ringDurationMs()
    val flowing = !idle && label != "LOST"
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(interactionSource, indication = null, onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        GlobeRadar(
            color = color,
            size = size,
            label = label,
            flowingOverride = flowing,
            ringDurationMs = duration,
            flowDurationMs = (duration * 0.72f).roundToIntSafe(),
        )
    }
}

private fun Float.roundToIntSafe(): Int = roundToInt().coerceIn(240, 2600)
''')

# 3) Make the radar speed health-driven while retaining its existing visual treatment.
globe = read('android/app/src/main/java/com/linkshare/app/ui/components/GlobeRadar.kt')
globe = globe.replace(
    'fun GlobeRadar(color: Color, size: Dp = 190.dp, label: String? = "ONLINE") {',
    'fun GlobeRadar(\n    color: Color, size: Dp = 190.dp, label: String? = "ONLINE",\n    flowingOverride: Boolean = false, ringDurationMs: Int = 620, flowDurationMs: Int = 760,\n) {'
)
globe = globe.replace('tween(760, easing = LinearEasing)', 'tween(flowDurationMs, easing = LinearEasing)')
globe = globe.replace('tween(620, easing = LinearEasing)', 'tween(ringDurationMs, easing = LinearEasing)')
globe = re.sub(r'val flowing = outgoing \|\| incoming \|\| packetFlow', 'val flowing = flowingOverride || outgoing || incoming || packetFlow', globe)
write('android/app/src/main/java/com/linkshare/app/ui/components/GlobeRadar.kt', globe)

# 4) Remove edge-to-edge opt-in and add the global health banner.
main = read('android/app/src/main/java/com/linkshare/app/MainActivity.kt')
main = main.replace('import com.linkshare.app.ui.components.LinkoRealtimeOverlay', 'import com.linkshare.app.ui.components.LinkoRealtimeOverlay\nimport com.linkshare.app.ui.components.LinkoNetworkHealthBanner')
main = main.replace('        enableEdgeToEdge()\n', '')
main = main.replace('                    if (::updateManager.isInitialized) {', '                    LinkoNetworkHealthBanner()\n                    if (::updateManager.isInitialized) {')
write('android/app/src/main/java/com/linkshare/app/MainActivity.kt', main)

# 5) Human-friendly connection screen + real traffic totals.
write('android/app/src/main/java/com/linkshare/app/ui/screens/ConnectionStatusScreen.kt', r'''package com.linkshare.app.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.ui.components.InfoRow
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.components.LinkoNetworkHealthMonitor
import com.linkshare.app.ui.components.LinkoNetworkHealthLevel
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub

@Composable
fun ConnectionStatusScreen(onConnected: () -> Unit, onFailed: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by LinkoEngineBridge.connection.collectAsStateWithLifecycle()
    val health by LinkoNetworkHealthMonitor.snapshot.collectAsStateWithLifecycle()
    var vpnGranted by remember { mutableStateOf(VpnService.prepare(context) == null) }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vpnGranted = result.resultCode == Activity.RESULT_OK || VpnService.prepare(context) == null
    }

    LaunchedEffect(Unit) {
        LinkoNetworkHealthMonitor.start(context)
        VpnService.prepare(context)?.let(vpnLauncher::launch) ?: run { vpnGranted = true }
    }
    LaunchedEffect(state.phase) { if (state.phase == LinkoConnectionPhase.Connected) onConnected() }

    val color = when {
        state.phase == LinkoConnectionPhase.Failed -> Red
        state.phase == LinkoConnectionPhase.Connected && health.score >= 65 -> Green
        health.score < 45 && health.available -> Red
        else -> Blue
    }
    val label = when (state.phase) {
        LinkoConnectionPhase.Connected -> "CONNECTED"
        LinkoConnectionPhase.Failed -> "LOST"
        LinkoConnectionPhase.Signaling -> "WAITING"
        LinkoConnectionPhase.Idle -> "READY"
        else -> "CONNECTING"
    }
    val title = when (state.phase) {
        LinkoConnectionPhase.Connected -> if (state.peerDisplayName.isNullOrBlank()) "Connected" else "Connected to ${state.peerDisplayName}"
        LinkoConnectionPhase.Failed -> "We couldn't connect"
        LinkoConnectionPhase.Signaling -> "Waiting for your friend"
        LinkoConnectionPhase.Idle -> "Ready to connect"
        else -> "Connecting"
    }
    val message = when {
        state.phase == LinkoConnectionPhase.Failed -> friendlyFailure(state.error ?: state.detail)
        state.phase == LinkoConnectionPhase.Connected && health.level == LinkoNetworkHealthLevel.POOR -> "Your connection is weak. LINKO is trying to keep you connected."
        state.phase == LinkoConnectionPhase.Connected && health.level == LinkoNetworkHealthLevel.WEAK -> "Connection is slowing down."
        state.phase == LinkoConnectionPhase.Connected -> "Your friend's internet is available now."
        state.phase == LinkoConnectionPhase.Signaling -> "Your friend needs to accept the request."
        state.phase == LinkoConnectionPhase.Idle -> "Choose a friend and we'll handle the connection for you."
        else -> "We're working on the connection."
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Ring(color = color, size = 170.dp, idle = state.phase == LinkoConnectionPhase.Idle || state.phase == LinkoConnectionPhase.Failed, label = label)
        Spacer(Modifier.height(18.dp))
        Text(title, color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        Text(message, color = if (state.phase == LinkoConnectionPhase.Failed) Red else TextSub, fontSize = 12.sp, textAlign = TextAlign.Center)

        if (state.phase == LinkoConnectionPhase.Connected) {
            Spacer(Modifier.height(18.dp))
            LinkoCard {
                Text("Live usage", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                InfoRow("Downloaded", formatBytes(state.bytesIn))
                InfoRow("Uploaded", formatBytes(state.bytesOut))
                InfoRow("Total", formatBytes(state.bytesIn + state.bytesOut))
                if (state.latencyMs > 0) InfoRow("Response time", "${state.latencyMs} ms")
            }
        }

        if (!vpnGranted) {
            Spacer(Modifier.height(14.dp))
            LinkoCard {
                Text("One permission is needed", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Android needs permission before LINKO can share the connection with your apps.", color = TextSub, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("ALLOW", { VpnService.prepare(context)?.let(vpnLauncher::launch) }, color = Blue)
            }
        }

        Spacer(Modifier.height(22.dp))
        if (state.phase == LinkoConnectionPhase.Failed) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("TRY AGAIN", { LinkoEngineBridge.reconnect(); }, color = Blue)
                PrimaryButton("CANCEL", { LinkoEngineBridge.disconnect(); onFailed() }, color = Red, outline = true)
            }
        } else if (state.phase != LinkoConnectionPhase.Idle && state.phase != LinkoConnectionPhase.Connected) {
            PrimaryButton("STOP", { LinkoEngineBridge.disconnect(); onFailed() }, color = Red, outline = true)
        } else if (state.phase == LinkoConnectionPhase.Connected) {
            PrimaryButton("DISCONNECT", { LinkoEngineBridge.disconnect(); onFailed() }, color = Red, outline = true)
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun friendlyFailure(raw: String): String {
    val lower = raw.lowercase()
    return when {
        lower.contains("timeout") -> "The connection took too long. Check both phones' internet and try again."
        lower.contains("denied") || lower.contains("declined") -> "Your friend declined the connection request."
        lower.contains("offline") -> "Your friend is not available right now."
        lower.contains("permission") || lower.contains("vpn") -> "Android permission is needed before LINKO can connect."
        lower.contains("network") || lower.contains("socket") || lower.contains("unreachable") -> "The network is having trouble. Check your connection and try again."
        else -> "Something went wrong while connecting. Please try again."
    }
}

private fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)
    return when {
        value < 1024 -> "$value B"
        value < 1024 * 1024 -> "${value / 1024} KB"
        value < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", value / 1024.0 / 1024.0)
        else -> String.format(java.util.Locale.US, "%.2f GB", value / 1024.0 / 1024.0 / 1024.0)
    }
}
''')

# 6) Friends: make the long list scroll and remove technical status wording.
friends = read('android/app/src/main/java/com/linkshare/app/ui/screens/FriendsScreens.kt')
if 'rememberScrollState' not in friends:
    friends = friends.replace('import androidx.compose.foundation.layout.*', 'import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll')
friends = friends.replace('Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){', 'Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp)){')
friends = friends.replace('Spacer(Modifier.weight(1f));PrimaryButton', 'Spacer(Modifier.height(16.dp));PrimaryButton')
friends = friends.replace('ONLINE • SHARING', 'ONLINE • SHARING INTERNET')
friends = friends.replace('ONLINE"', 'ONLINE"')
friends = friends.replace('FRIEND • READY TO CONNECT', 'FRIEND • AVAILABLE')
friends = friends.replace('FRIEND • INTERNET SHARING ACTIVE', 'FRIEND • SHARING INTERNET')
friends = friends.replace('PENDING • WAITING FOR ACCEPTANCE', 'Waiting for acceptance')
friends = friends.replace('ACCEPTED • YOU ARE NOW FRIENDS', 'You are now friends')
friends = friends.replace('DECLINED • REQUEST NOT ACCEPTED', 'Request declined')
write('android/app/src/main/java/com/linkshare/app/ui/screens/FriendsScreens.kt', friends)

# 7) Notifications: no technical LIVE wording in the normal UI.
notif = read('android/app/src/main/java/com/linkshare/app/ui/screens/NotificationsScreen.kt')
notif = notif.replace('Friend requests, responses and connection activity appear here in real time.', 'Friend requests, responses and connection updates appear here.')
notif = notif.replace('New LINKO activity will appear here automatically while realtime is connected.', 'New LINKO activity will appear here automatically.')
notif = notif.replace('Text("LIVE • LINKO", color = Blue, fontSize = 9.sp, fontFamily = JetBrainsMono)', 'Text("LINKO", color = Blue, fontSize = 9.sp, fontFamily = JetBrainsMono)')
write('android/app/src/main/java/com/linkshare/app/ui/screens/NotificationsScreen.kt', notif)

# 8) Signup consent + plain-language legal dialogs.
sign = read('android/app/src/main/java/com/linkshare/app/ui/screens/SignUpScreen.kt')
sign = sign.replace('import androidx.compose.material3.Icon', 'import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.Checkbox\nimport androidx.compose.material3.Icon')
sign = sign.replace('    var message by remember { mutableStateOf<String?>(null) };', '    var message by remember { mutableStateOf<String?>(null) }; var agreed by remember { mutableStateOf(false) }; var showTerms by remember { mutableStateOf(false) }; var showPrivacy by remember { mutableStateOf(false) };')
needle = 'SecretField("CONFIRM PASSWORD", confirm, { confirm = it }, "Repeat your password")\n        message?.let'
insert = '''SecretField("CONFIRM PASSWORD", confirm, { confirm = it }, "Repeat your password")\n        Spacer(Modifier.height(12.dp))\n        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {\n            Checkbox(checked = agreed, onCheckedChange = { agreed = it })\n            Text("I agree to the ", color = TextSub, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))\n            Text("Terms", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp).clickable { showTerms = true })\n            Text(" and ", color = TextSub, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))\n            Text("Privacy Policy", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp).clickable { showPrivacy = true })\n        }\n        message?.let'''
sign = sign.replace(needle, insert)
sign = sign.replace('                password != confirm -> message = "Passwords do not match."', '                password != confirm -> message = "Passwords do not match."\n                !agreed -> message = "Please agree to the Terms and Privacy Policy before creating your account."')
# Add clickable import if absent.
sign = sign.replace('import androidx.compose.foundation.layout.*', 'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.layout.*')
# Put dialogs just before function closing after the final spacer.
sign = sign.replace('        PrimaryButton(when { busy -> "CREATING…";', '        PrimaryButton(when { busy -> "CREATING…";')
end_marker = '        PrimaryButton(when { busy -> "CREATING…"; cooldownActive -> "WAIT…"; else -> "CREATE ACCOUNT" }, {'
# No-op: the existing button code remains intact; inject dialogs before the Column closes.
needle2 = '        }); Spacer(Modifier.height(24.dp))\n    }\n}'
dialogs = '''        }); Spacer(Modifier.height(24.dp))\n    }\n    if (showTerms) AlertDialog(\n        onDismissRequest = { showTerms = false },\n        title = { Text("Terms of Service") },\n        text = { Text(LEGAL_TERMS, fontSize = 11.sp) },\n        confirmButton = { PrimaryButton("CLOSE", { showTerms = false }, color = Blue) }\n    )\n    if (showPrivacy) AlertDialog(\n        onDismissRequest = { showPrivacy = false },\n        title = { Text("Privacy Policy") },\n        text = { Text(LEGAL_PRIVACY, fontSize = 11.sp) },\n        confirmButton = { PrimaryButton("CLOSE", { showPrivacy = false }, color = Blue) }\n    )\n}'''
sign = sign.replace(needle2, dialogs)
sign += '''\n\nprivate const val LEGAL_TERMS = "LINKO lets you share an internet connection with people you choose. You are responsible for your own account, your device, your mobile-data costs, and the way you use another person's connection. Do not use LINKO for unlawful activity, harassment, abuse, or attempts to bypass security controls. Connections can be interrupted at any time. These terms are a product draft and should receive legal review before public release."\nprivate const val LEGAL_PRIVACY = "LINKO uses account, profile, device, connection-session, usage, and diagnostic information needed to provide and protect the service. LINKO should not collect passwords, private keys, or the contents of internet traffic in diagnostics. Data may be stored with service providers used by LINKO, such as Supabase. You can request account deletion and applicable data rights through LINKO support. This privacy text is a product draft and should receive legal review before public release."\n'''
write('android/app/src/main/java/com/linkshare/app/ui/screens/SignUpScreen.kt', sign)

# 9) Legal source document.
write('docs/legal/terms-of-service.md', '''# LINKO Terms of Service — Draft\n\n**Status:** Product draft; legal review required before public release.\n\nLINKO lets users share an available internet connection with trusted friends. By using LINKO, you agree to use it lawfully and responsibly. You are responsible for your account, your device, your mobile-data or broadband charges, and the content or activity performed through your own connection.\n\nLINKO connections may be interrupted because of device state, network conditions, Android restrictions, or a friend's decision to stop sharing. LINKO does not guarantee continuous availability or a specific speed.\n\nDo not use LINKO to abuse, attack, defraud, harass, or unlawfully access systems or data. Do not attempt to bypass LINKO security controls or another person's permissions.\n\nLINKO may suspend or end access when necessary for security, abuse prevention, legal compliance, or service integrity.\n\nThese terms are a product draft, not legal advice. They should be reviewed by qualified counsel before publication.\n''')

# 10) Update the existing task tracker on the candidate branch.
tasks = read('SDLC/TASKS.md')
if 'Stable candidate UI/UX hardening' not in tasks:
    tasks += '''\n\n## Stable Candidate — UX, Reliability, Security & Legal Review\n\n- [x] Human-friendly connection status and failure messages\n- [x] Scrollable friends/notifications experience\n- [x] Health-driven connection ring\n- [x] Global poor-network warning banner\n- [x] Android status-bar-safe layout\n- [x] Signup Terms + Privacy consent\n- [x] Terms of Service draft\n- [x] Existing security/usage/diagnostics layers retained\n- [ ] Real-device Android 10–16+ compatibility matrix\n- [ ] Final security/legal review before release\n'''
write('SDLC/TASKS.md', tasks)

print('LINKO stable-candidate transformations prepared.')
