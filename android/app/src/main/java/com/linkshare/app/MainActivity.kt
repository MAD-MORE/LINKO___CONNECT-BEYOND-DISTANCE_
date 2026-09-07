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
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
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
                if (showHome) {
                    LinkoHomeScreen()
                } else {
                    StartupSplashScreen(
                        statusMessage,
                        startupFailed,
                        ::startInitialization,
                        {
                            startupFailed = false
                            statusMessage = "Offline startup mode"
                            showHome = true
                        },
                        {
                            startupScope.launch(Dispatchers.IO) {
                                runCatching { linkoAuth.signOut() }
                            }
                        },
                    )
                }
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
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    linkoRuntime.initialize { message ->
                        runOnUiThread { statusMessage = message }
                    }
                }.getOrDefault(false)
            }
            if (!ok) {
                startupFailed = true
                statusMessage = "Secure runtime initialization delayed"
                return@launch
            }
            statusMessage = "LINKO secure runtime ready"
            showHome = true
            if (::updateManager.isInitialized) {
                withContext(Dispatchers.IO) {
                    runCatching { updateManager.checkAndOfferUpdate() }
                        .onFailure { Log.e(TAG, "Startup update check failed", it) }
                }
            }
        }
    }

    override fun onDestroy() {
        startupScope.cancel()
        runCatching { linkoRuntime.stop() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LINKO_MAIN"
    }
}

private val LinkoBlue = Color(0xFF1769FF)
private val LinkoCyan = Color(0xFF10B9D8)
private val LinkoGreen = Color(0xFF12A96B)
private val LinkoRed = Color(0xFFE5484D)
private val LinkoText = Color(0xFF101828)
private val LinkoMuted = Color(0xFF667085)
private val LinkoLine = Color(0xFFE4E7EC)
private val LinkoSoft = Color(0xFFF5F8FC)

private enum class HomeMode { Home, Friends, Share }

private data class LocalFriend(
    val userId: String,
    val linkoId: String,
    val name: String,
    val online: Boolean,
    val sharing: Boolean,
)

@Composable
private fun LinkoHomeScreen() {
    val engine by LinkoEngineBridge.connection.collectAsState()
    val auth = remember { LinkoAuth.current() }
    val api = LinkoFriendsApiHolder.api
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(HomeMode.Home) }
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

    LaunchedEffect(Unit) {
        loadingFriends = true
        friends = loadFriends(api)
        loadingFriends = false
    }

    LaunchedEffect(engine.phase) {
        if (engine.phase == LinkoConnectionPhase.Connected) completed = false
    }

    val connected = engine.phase == LinkoConnectionPhase.Connected
    val working = engine.phase != LinkoConnectionPhase.Idle &&
        engine.phase != LinkoConnectionPhase.Connected &&
        engine.phase != LinkoConnectionPhase.Failed

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LINKO", color = LinkoText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text("CONNECT BEYOND DISTANCE", color = LinkoMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (engine.phase == LinkoConnectionPhase.Failed) LinkoRed else LinkoGreen),
                )
            }
            Spacer(Modifier.height(20.dp))

            when {
                completed -> DoneContent {
                    completed = false
                    mode = HomeMode.Home
                }
                connected -> LiveContent(engine) {
                    LinkoEngineBridge.disconnect()
                    completed = true
                    mode = HomeMode.Home
                }
                working -> ConnectingContent(engine) {
                    LinkoEngineBridge.disconnect()
                    mode = HomeMode.Home
                }
                mode == HomeMode.Friends -> FriendsDashboard(
                    friends = friends,
                    loading = loadingFriends,
                    onRefresh = {
                        scope.launch {
                            loadingFriends = true
                            friends = loadFriends(api)
                            loadingFriends = false
                        }
                    },
                    onBack = { mode = HomeMode.Home },
                    onSelect = { friend ->
                        mode = HomeMode.Home
                        LinkoEngineBridge.connectToFriend(friend.userId, friend.name, friend.linkoId) { }
                    },
                )
                mode == HomeMode.Share -> ShareContent(
                    pendingRequest = pendingRequest,
                    waiting = shareWaiting,
                    onStart = { shareWaiting = true },
                    onAccept = {
                        LinkoEngineBridge.approvePendingProviderRequest()
                        shareWaiting = true
                    },
                    onDecline = {
                        LinkoEngineBridge.denyPendingProviderRequest()
                        pendingRequest = null
                    },
                    onBack = {
                        shareWaiting = false
                        pendingRequest = null
                        mode = HomeMode.Home
                    },
                )
                else -> HomeContent(
                    displayName = auth?.currentDisplayName(),
                    onFriends = { mode = HomeMode.Friends },
                    onShare = { mode = HomeMode.Share },
                )
            }

            Spacer(Modifier.height(30.dp))
            if (!connected && !working && !completed && mode == HomeMode.Home) {
                Text("Your connection stays under your control.", color = LinkoMuted, fontSize = 11.sp)
                Spacer(Modifier.height(18.dp))
                TextButton(onClick = { mode = HomeMode.Share }) {
                    Text("Share instead", color = LinkoBlue)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

private suspend fun loadFriends(api: LinkoFriendsApi): List<LocalFriend> = withContext(Dispatchers.IO) {
    runCatching {
        val array = api.friends().optJSONArray("friends") ?: org.json.JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    LocalFriend(
                        userId = item.optString("user_id"),
                        linkoId = item.optString("linko_id"),
                        name = item.optString("display_name").ifBlank { "LINKO Friend" },
                        online = item.optBoolean("is_online", false),
                        sharing = item.optBoolean("is_sharing", false),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

@Composable
private fun HomeContent(displayName: String?, onFriends: () -> Unit, onShare: () -> Unit) {
    Text(
        if (displayName.isNullOrBlank()) "Ready when you are." else "Hi, ${displayName.take(28)}.",
        color = LinkoText,
        fontSize = 27.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text("Connect to a friend or share your Internet.", color = LinkoMuted, fontSize = 13.sp)
    Spacer(Modifier.height(25.dp))
    Ring(color = LinkoBlue, size = 218.dp, idle = true, label = "LINKO")
    Spacer(Modifier.height(24.dp))
    ActionButton("FRIENDS", "Open your friends dashboard", Icons.Filled.People, onFriends, LinkoBlue)
    Spacer(Modifier.height(12.dp))
    SecondaryAction("SHARE MY INTERNET", Icons.Filled.Wifi, onShare)
}

@Composable
private fun FriendsDashboard(
    friends: List<LocalFriend>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSelect: (LocalFriend) -> Unit,
) {
    HeaderBack("FRIENDS", onBack)
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Your friends", color = LinkoText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Choose a friend to start a LINKO connection.", color = LinkoMuted, fontSize = 12.sp)
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh friends", tint = LinkoBlue)
        }
    }
    Spacer(Modifier.height(18.dp))

    val online = friends.count { it.online }
    val sharing = friends.count { it.sharing }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("FRIENDS", friends.size.toString(), Modifier.weight(1f))
        StatCard("ONLINE", online.toString(), Modifier.weight(1f))
        StatCard("SHARING", sharing.toString(), Modifier.weight(1f))
    }
    Spacer(Modifier.height(18.dp))

    when {
        loading -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LinkoBlue)
        }
        friends.isEmpty() -> EmptyFriends()
        else -> friends.forEach { friend -> FriendCard(friend, onSelect) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LinkoSoft)
            .padding(14.dp),
    ) {
        Text(value, color = LinkoText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = LinkoMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun FriendCard(friend: LocalFriend, onSelect: (LocalFriend) -> Unit) {
    val available = friend.online || friend.sharing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(enabled = available) { onSelect(friend) }
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(LinkoSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = LinkoBlue)
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(friend.name, color = LinkoText, fontWeight = FontWeight.SemiBold)
            Text(
                if (friend.sharing) "Sharing Internet" else if (friend.online) "Online" else "Offline",
                color = if (available) LinkoGreen else LinkoMuted,
                fontSize = 11.sp,
            )
        }
        Text(
            if (friend.sharing) "CONNECT" else if (friend.online) "ONLINE" else "OFFLINE",
            color = if (available) LinkoBlue else LinkoMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyFriends() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.People, contentDescription = null, tint = LinkoMuted, modifier = Modifier.size(46.dp))
        Spacer(Modifier.height(12.dp))
        Text("No friends yet", color = LinkoText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text("Your real LINKO friends will appear here.", color = LinkoMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ShareContent(
    pendingRequest: com.linkshare.app.network.ProviderRequest?,
    waiting: Boolean,
    onStart: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onBack: () -> Unit,
) {
    HeaderBack("SHARE INTERNET", onBack)
    Spacer(Modifier.height(12.dp))
    Ring(
        color = if (pendingRequest != null) LinkoBlue else LinkoCyan,
        size = 205.dp,
        pulse = pendingRequest != null,
        label = if (pendingRequest != null) "REQUEST" else "READY",
    )
    Spacer(Modifier.height(22.dp))
    Text(
        if (pendingRequest == null) "Your phone can be the provider." else "A friend wants to connect.",
        color = LinkoText,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(7.dp))
    Text(
        if (pendingRequest == null) {
            "LINKO waits for a real connection request. Nothing is shared until you approve it."
        } else {
            "Approve the request to start the real provider tunnel."
        },
        color = LinkoMuted,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(18.dp))
    if (pendingRequest == null) {
        ActionButton(
            if (waiting) "WAITING FOR A FRIEND…" else "START SHARING MODE",
            "Stay ready for an incoming request",
            Icons.Filled.Wifi,
            onStart,
            LinkoBlue,
            enabled = !waiting,
        )
    } else {
        ActionButton(
            "ACCEPT CONNECTION",
            "Allow this friend to use your Internet",
            Icons.Filled.CheckCircle,
            onAccept,
            LinkoGreen,
        )
        Spacer(Modifier.height(10.dp))
        SecondaryAction("DECLINE", Icons.Filled.Close, onDecline)
    }
}

@Composable
private fun ConnectingContent(
    engine: com.linkshare.app.network.LinkoEngineConnectionState,
    onCancel: () -> Unit,
) {
    HeaderBack("CONNECTING", onCancel)
    Spacer(Modifier.height(16.dp))
    Ring(color = LinkoBlue, size = 218.dp, pulse = true, label = "LINKING")
    Spacer(Modifier.height(18.dp))
    Text(engine.peerDisplayName ?: "LINKO Friend", color = LinkoText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(engine.detail, color = LinkoMuted, fontSize = 12.sp)
    Spacer(Modifier.height(18.dp))
    ProgressLine(engine.phase)
    Spacer(Modifier.height(22.dp))
    SecondaryAction("CANCEL", Icons.Filled.Close, onCancel)
}

@Composable
private fun LiveContent(
    engine: com.linkshare.app.network.LinkoEngineConnectionState,
    onDone: () -> Unit,
) {
    HeaderBack(if (engine.isProvider) "SHARING LIVE" else "LIVE CONNECTION", onDone)
    Spacer(Modifier.height(12.dp))
    Ring(
        color = LinkoGreen,
        size = 224.dp,
        pulse = true,
        label = if (engine.isProvider) "SHARING" else "ONLINE",
        fast = true,
    )
    Spacer(Modifier.height(18.dp))
    Text(engine.peerDisplayName ?: "LINKO Friend", color = LinkoText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(5.dp))
    Text(
        if (engine.isProvider) "Your Internet is being shared securely." else "Internet sharing is verified.",
        color = LinkoGreen,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(20.dp))
    InfoRow("CONNECTION", "ACTIVE")
    InfoRow("LATENCY", if (engine.latencyMs > 0) "${engine.latencyMs} ms" else "Measuring")
    InfoRow("DATA RECEIVED", formatBytes(engine.bytesIn))
    InfoRow("DATA SENT", formatBytes(engine.bytesOut))
    Spacer(Modifier.height(20.dp))
    SecondaryAction("END CONNECTION", Icons.Filled.Close, onDone)
}

@Composable
private fun DoneContent(onAgain: () -> Unit) {
    Spacer(Modifier.height(55.dp))
    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LinkoGreen, modifier = Modifier.size(76.dp))
    Spacer(Modifier.height(20.dp))
    Text("Done", color = LinkoText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    Text("The LINKO connection has been closed.", color = LinkoMuted, fontSize = 13.sp)
    Spacer(Modifier.height(26.dp))
    ActionButton("CONNECT AGAIN", "Start a new secure session", Icons.Filled.Link, onAgain, LinkoBlue)
}

@Composable
private fun HeaderBack(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = LinkoText)
        }
        Text(title, color = LinkoText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionButton(
    label: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    color: Color,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = LinkoLine,
            disabledContentColor = LinkoMuted,
        ),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(detail, fontSize = 9.sp, color = Color.White.copy(alpha = 0.82f))
        }
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        border = ButtonDefaults.outlinedButtonBorder,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LinkoText),
    ) {
        Icon(icon, contentDescription = null, tint = LinkoBlue)
        Spacer(Modifier.width(9.dp))
        Text(label, color = LinkoText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressLine(phase: LinkoConnectionPhase) {
    val stages = listOf("CONNECTING", "SIGNALING", "TUNNEL", "ONLINE")
    val active = when (phase) {
        LinkoConnectionPhase.Connected -> 4
        LinkoConnectionPhase.Idle -> 0
        LinkoConnectionPhase.Failed -> 0
        else -> 2
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        stages.forEachIndexed { index, stage ->
            val selected = index < active
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (selected) LinkoBlue else LinkoLine),
                )
                Spacer(Modifier.height(5.dp))
                Text(stage, color = if (selected) LinkoBlue else LinkoMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LinkoMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(value, color = LinkoText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)
    return when {
        value < 1024L -> "$value B"
        value < 1024L * 1024L -> "${value / 1024L} KB"
        value < 1024L * 1024L * 1024L -> "${value / (1024L * 1024L)} MB"
        else -> "${value / (1024L * 1024L * 1024L)} GB"
    }
}
