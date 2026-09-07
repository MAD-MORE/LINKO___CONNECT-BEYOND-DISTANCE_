package com.linkshare.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoConnectionPhase
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoFriendsApi
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoRuntime
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.screens.StartupSplashScreen
import com.linkshare.app.ui.theme.LinkoTheme
import com.linkshare.app.update.LinkoUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var linkoAuth: LinkoAuth
    private lateinit var linkoRuntime: LinkoRuntime
    private lateinit var updateManager: LinkoUpdateManager
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statusMessage by mutableStateOf("Initializing Cryptographic Keystore…")
    private var startupFailed by mutableStateOf(false)
    private var showHome by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            linkoAuth = LinkoAuth(this)
            LinkoFriendsApiHolder.api = LinkoFriendsApi { linkoAuth.currentAccessToken() }
            LinkoEngineBridge.configure(this)
            linkoRuntime = LinkoRuntime(this)
            updateManager = LinkoUpdateManager(this)
        }.onFailure {
            startupFailed = true
            statusMessage = "LINKO startup initialization failed"
            Log.e(TAG, "Core LINKO setup failed", it)
        }
        setContent {
            LinkoTheme {
                if (showHome) LinkoHomeScreen() else StartupSplashScreen(
                    statusMessage = statusMessage,
                    failed = startupFailed,
                    onRetry = ::startInitialization,
                    onContinueOffline = { startupFailed = false; statusMessage = "Offline startup mode"; showHome = true },
                    onSignOut = { startupScope.launch(Dispatchers.IO) { runCatching { linkoAuth.signOut() } } }
                )
            }
        }
        startInitialization()
    }

    private fun startInitialization() {
        if (!::linkoRuntime.isInitialized) return
        showHome = false
        startupFailed = false
        statusMessage = "Initializing Cryptographic Keystore…"
        startupScope.launch {
            val runtimeOk = withContext(Dispatchers.IO) {
                runCatching { linkoRuntime.initialize { message -> runOnUiThread { statusMessage = message } } }.getOrDefault(false)
            }
            if (!runtimeOk) {
                startupFailed = true
                statusMessage = "Secure runtime initialization delayed"
                return@launch
            }
            statusMessage = "LINKO secure runtime ready"
            showHome = true
            if (::updateManager.isInitialized) withContext(Dispatchers.IO) { runCatching { updateManager.checkAndOfferUpdate() }.onFailure { Log.e(TAG, "Startup update check failed", it) } }
        }
    }

    override fun onDestroy() { startupScope.cancel(); runCatching { linkoRuntime.stop() }; super.onDestroy() }
    companion object { private const val TAG = "LINKO_MAIN" }
}

private val LinkoBlue = Color(0xFF1769FF)
private val LinkoCyan = Color(0xFF10B9D8)
private val LinkoGreen = Color(0xFF12A96B)
private val LinkoRed = Color(0xFFE5484D)
private val LinkoText = Color(0xFF101828)
private val LinkoMuted = Color(0xFF667085)
private val LinkoLine = Color(0xFFE4E7EC)
private val LinkoSoft = Color(0xFFF5F8FC)

@Composable
private fun LinkoHomeScreen() {
    val engine by LinkoEngineBridge.connection.collectAsState()
    val auth = remember { LinkoAuth.current() }
    val api = LinkoFriendsApiHolder.api
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<HomeMode>(HomeMode.Home) }
    var showFriends by remember { mutableStateOf(false) }
    var friends by remember { mutableStateOf<List<LocalFriend>>(emptyList()) }
    var loadingFriends by remember { mutableStateOf(false) }
    var shareWaiting by remember { mutableStateOf(false) }
    var pendingRequest by remember { mutableStateOf<com.linkshare.app.network.ProviderRequest?>(null) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(shareWaiting) {
        if (!shareWaiting) return@LaunchedEffect
        while (shareWaiting) {
            pendingRequest = LinkoEngineBridge.getPendingProviderRequests().firstOrNull()
            delay(1500)
        }
    }
    LaunchedEffect(engine.phase) { if (engine.phase == LinkoConnectionPhase.Connected) completed = false }

    val connected = engine.phase == LinkoConnectionPhase.Connected
    val working = engine.phase != LinkoConnectionPhase.Idle && engine.phase != LinkoConnectionPhase.Connected && engine.phase != LinkoConnectionPhase.Failed

    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LINKO", color = LinkoText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text("CONNECT BEYOND DISTANCE", color = LinkoMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (engine.phase == LinkoConnectionPhase.Failed) LinkoRed else LinkoGreen))
            }
            Spacer(Modifier.height(28.dp))
            when {
                completed -> DoneContent { completed = false; mode = HomeMode.Home }
                connected -> LiveContent(engine) { LinkoEngineBridge.disconnect(); completed = true; mode = HomeMode.Home }
                working -> ConnectingContent(engine) { LinkoEngineBridge.disconnect(); mode = HomeMode.Home }
                mode == HomeMode.Share -> ShareContent(pendingRequest, shareWaiting, { shareWaiting = true }, { LinkoEngineBridge.approvePendingProviderRequest { }; shareWaiting = true }, { LinkoEngineBridge.denyPendingProviderRequest { }; pendingRequest = null }, { shareWaiting = false; pendingRequest = null; mode = HomeMode.Home })
                else -> HomeContent(auth?.currentDisplayName(), {
                    mode = HomeMode.Connect; showFriends = true; loadingFriends = true
                    scope.launch {
                        friends = runCatching { withContext(Dispatchers.IO) {
                            val array = api.friends().optJSONArray("friends") ?: org.json.JSONArray()
                            buildList {
                                for (i in 0 until array.length()) {
                                    val item = array.optJSONObject(i) ?: continue
                                    add(LocalFriend(item.optString("user_id"), item.optString("linko_id"), item.optString("display_name").ifBlank { "LINKO Friend" }, item.optBoolean("is_online", false), item.optBoolean("is_sharing", false)))
                                }
                            }
                        } }.getOrDefault(emptyList())
                        loadingFriends = false
                    }
                }, { mode = HomeMode.Share })
            }
            Spacer(Modifier.height(30.dp))
            if (!connected && !working && !completed && mode == HomeMode.Home) {
                Text("Your connection stays under your control.", color = LinkoMuted, fontSize = 11.sp)
                Spacer(Modifier.height(18.dp))
                TextButton(onClick = { mode = HomeMode.Share }) { Text("Share instead", color = LinkoBlue) }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
    if (showFriends) FriendPicker(friends, loadingFriends, { showFriends = false; mode = HomeMode.Home }) { friend ->
        showFriends = false; mode = HomeMode.Home
        LinkoEngineBridge.connectToFriend(friend.userId, friend.name, friend.linkoId) { }
    }
}

private enum class HomeMode { Home, Connect, Share }
private data class LocalFriend(val userId: String, val linkoId: String, val name: String, val online: Boolean, val sharing: Boolean)

@Composable
private fun HomeContent(displayName: String?, onConnect: () -> Unit, onShare: () -> Unit) {
    Text(if (displayName.isNullOrBlank()) "Ready when you are." else "Hi, ${displayName.take(28)}.", color = LinkoText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp)); Text("Connect to a friend or share your Internet.", color = LinkoMuted, fontSize = 13.sp)
    Spacer(Modifier.height(25.dp)); Ring(color = LinkoBlue, size = 218.dp, idle = true, label = "LINKO")
    Spacer(Modifier.height(24.dp)); ActionButton("CONNECT TO A FRIEND", "Use a friend's available Internet", Icons.Filled.Link, onConnect, LinkoBlue)
    Spacer(Modifier.height(12.dp)); SecondaryAction("SHARE MY INTERNET", Icons.Filled.Wifi, onShare)
}

@Composable
private fun ShareContent(pendingRequest: com.linkshare.app.network.ProviderRequest?, waiting: Boolean, onStart: () -> Unit, onAccept: () -> Unit, onDecline: () -> Unit, onBack: () -> Unit) {
    HeaderBack("SHARE INTERNET", onBack); Spacer(Modifier.height(12.dp))
    Ring(color = if (pendingRequest != null) LinkoBlue else LinkoCyan, size = 205.dp, pulse = pendingRequest != null, label = if (pendingRequest != null) "REQUEST" else "READY")
    Spacer(Modifier.height(22.dp)); Text(if (pendingRequest == null) "Your phone can be the provider." else "A friend wants to connect.", color = LinkoText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp)); Text(if (pendingRequest == null) "LINKO waits for a real connection request. Nothing is shared until you approve it." else "Approve the request to start the real provider tunnel.", color = LinkoMuted, fontSize = 13.sp)
    Spacer(Modifier.height(18.dp))
    if (pendingRequest == null) ActionButton(if (waiting) "WAITING FOR A FRIEND…" else "START SHARING MODE", "Stay ready for an incoming request", Icons.Filled.Wifi, onStart, LinkoBlue, !waiting)
    else { ActionButton("ACCEPT CONNECTION", "Allow this friend to use your Internet", Icons.Filled.CheckCircle, onAccept, LinkoGreen); Spacer(Modifier.height(10.dp)); SecondaryAction("DECLINE", Icons.Filled.Close, onDecline) }
}

@Composable
private fun ConnectingContent(engine: com.linkshare.app.network.LinkoEngineConnectionState, onCancel: () -> Unit) {
    HeaderBack("CONNECTING", onCancel); Spacer(Modifier.height(16.dp)); Ring(LinkoBlue, 218.dp, pulse = true, label = "LINKING")
    Spacer(Modifier.height(18.dp)); Text(engine.peerDisplayName ?: "LINKO Friend", color = LinkoText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp)); Text(engine.detail, color = LinkoMuted, fontSize = 12.sp); Spacer(Modifier.height(18.dp)); ProgressLine(engine.phase); Spacer(Modifier.height(22.dp)); SecondaryAction("CANCEL", Icons.Filled.Close, onCancel)
}

@Composable
private fun LiveContent(engine: com.linkshare.app.network.LinkoEngineConnectionState, onDone: () -> Unit) {
    HeaderBack(if (engine.isProvider) "SHARING LIVE" else "LIVE CONNECTION", onDone); Spacer(Modifier.height(12.dp)); Ring(LinkoGreen, 224.dp, pulse = true, label = if (engine.isProvider) "SHARING" else "ONLINE", fast = true)
    Spacer(Modifier.height(18.dp)); Text(engine.peerDisplayName ?: "LINKO Friend", color = LinkoText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(5.dp)); Text(if (engine.isProvider) "Your Internet is being shared securely." else "Internet sharing is verified.", color = LinkoGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(20.dp)); InfoRow("CONNECTION", "ACTIVE"); InfoRow("LATENCY", if (engine.latencyMs > 0) "${engine.latencyMs} ms" else "Measuring"); InfoRow("DATA RECEIVED", formatBytes(engine.bytesIn)); InfoRow("DATA SENT", formatBytes(engine.bytesOut))
    Spacer(Modifier.height(20.dp)); SecondaryAction("END CONNECTION", Icons.Filled.Close, onDone)
}

@Composable
private fun DoneContent(onAgain: () -> Unit) {
    Spacer(Modifier.height(55.dp)); Icon(Icons.Filled.CheckCircle, null, tint = LinkoGreen, modifier = Modifier.size(76.dp)); Spacer(Modifier.height(20.dp))
    Text("Done", color = LinkoText, fontSize = 30.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp)); Text("The LINKO connection has been closed.", color = LinkoMuted, fontSize = 13.sp)
    Spacer(Modifier.height(26.dp)); ActionButton("CONNECT AGAIN", "Start a new secure session", Icons.Filled.Link, onAgain, LinkoBlue)
}

@Composable
private fun FriendPicker(friends: List<LocalFriend>, loading: Boolean, onDismiss: () -> Unit, onSelect: (LocalFriend) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Choose a friend", color = LinkoText, fontWeight = FontWeight.Bold) },
        text = { Column {
            if (loading) Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LinkoBlue) }
            else if (friends.isEmpty()) Text("No friends are available yet.", color = LinkoMuted, fontSize = 13.sp)
            else friends.forEach { friend -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = friend.online || friend.sharing) { onSelect(friend) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(LinkoSoft), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Person, null, tint = LinkoBlue) }
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(friend.name, color = LinkoText, fontWeight = FontWeight.SemiBold); Text(if (friend.sharing) "Sharing now" else if (friend.online) "Online" else "Offline", color = if (friend.online || friend.sharing) LinkoGreen else LinkoMuted, fontSize = 11.sp) }
            } }
        }},
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = LinkoBlue) } })
}

@Composable
private fun HeaderBack(title: String, onBack: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = LinkoText) }; Text(title, color = LinkoText, fontSize = 18.sp, fontWeight = FontWeight.Bold) } }

@Composable
private fun ActionButton(label: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, color: Color, enabled: Boolean = true) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(62.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White, disabledContainerColor = LinkoLine, disabledContentColor = LinkoMuted)) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(11.dp)); Column(horizontalAlignment = Alignment.Start) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text(detail, fontSize = 9.sp, color = Color.White.copy(alpha = .82f)) }
    }
}

@Composable
private fun SecondaryAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, LinkoLine)) { Icon(icon, null, tint = LinkoText, Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text(label, color = LinkoText, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun InfoRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, color = LinkoMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, Modifier.weight(1f)); Text(value, color = LinkoText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) } }

@Composable
private fun ProgressLine(phase: LinkoConnectionPhase) { val phases = listOf(LinkoConnectionPhase.Connecting, LinkoConnectionPhase.Authenticating, LinkoConnectionPhase.Signaling, LinkoConnectionPhase.Establishing, LinkoConnectionPhase.Securing, LinkoConnectionPhase.Routing); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { phases.forEach { item -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(if (item.ordinal <= phase.ordinal) LinkoBlue else LinkoLine)) } } }

private fun formatBytes(value: Long): String = when { value >= 1_048_576L -> "%.1f MB".format(value / 1_048_576.0); value >= 1024L -> "%.0f KB".format(value / 1024.0); else -> "$value B" }
