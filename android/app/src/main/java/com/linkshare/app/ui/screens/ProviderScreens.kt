package com.linkshare.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoProfileApi
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Border
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.Surface
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.Yellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ProviderReadyScreen(onIncomingRequest: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { LinkoAuth(context) }
    val api = remember { LinkoProfileApi(auth::currentAccessToken, auth::currentUserId) }
    var username by remember { mutableStateOf(auth.currentUsername() ?: auth.currentDisplayName().orEmpty()) }
    var linkoId by remember { mutableStateOf(auth.currentLinkoId().orEmpty()) }
    var providerState by remember { mutableStateOf(if (linkoId.isNotBlank()) "READY" else "LOADING") }
    var copied by remember { mutableStateOf(false) }

    fun copyId(idToCopy: String) {
        if (idToCopy.isBlank()) return
        val clean = idToCopy.removePrefix("@")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("LINKO ID", clean))
        copied = true
        Toast.makeText(context, "LINKO ID copied: $clean", Toast.LENGTH_SHORT).show()
    }

    val scope = rememberCoroutineScope()
    var pendingRequestId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        LinkoProviderService.start(context)

        // Asynchronously ensure and load canonical profile
        runCatching {
            val profile = withContext(Dispatchers.IO) { api.load() }
            username = profile.username ?: profile.displayName
            linkoId = profile.linkoId
            auth.saveProfile(profile.displayName, profile.linkoId, profile.username)
            if (providerState == "LOADING" || providerState == "ID_ERROR") {
                providerState = "READY"
            }
        }.onFailure {
            if (linkoId.isBlank()) {
                providerState = "ID_ERROR"
            }
        }

        // Realtime event listener
        launch {
            LinkoRealtimeManager.events.collect { event ->
                when (event) {
                    is LinkoRealtimeEvent.IncomingConnectionRequest -> {
                        pendingRequestId = event.sessionId
                        providerState = "REQUESTED"
                    }
                    is LinkoRealtimeEvent.FriendRequestReceived -> onIncomingRequest()
                    is LinkoRealtimeEvent.SessionStateChanged -> {
                        if (event.state == "requested") {
                            pendingRequestId = event.sessionId
                            providerState = "REQUESTED"
                        } else {
                            providerState = when (event.state) {
                                "approved" -> "APPROVED"
                                "signaling" -> "SIGNALING"
                                "connected" -> "SHARING"
                                "denied" -> "DECLINED"
                                "revoked", "expired" -> "ENDED"
                                else -> providerState
                            }
                            if (event.state != "requested") pendingRequestId = null
                        }
                    }
                    is LinkoRealtimeEvent.TransportError -> if (linkoId.isNotBlank() && providerState == "SHARING") providerState = "OFFLINE"
                    else -> Unit
                }
            }
        }

        // Polling fallback every 2 seconds for resilient request detection
        launch {
            while (true) {
                runCatching {
                    val requests = withContext(Dispatchers.IO) { LinkoEngineBridge.api?.pendingProviderRequests() }
                    val first = requests?.firstOrNull()
                    if (first != null) {
                        pendingRequestId = first.id
                        providerState = "REQUESTED"
                    } else if (providerState == "REQUESTED") {
                        pendingRequestId = null
                        providerState = "READY"
                    }
                }
                delay(2000)
            }
        }
    }

    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }

    val connectionBusy = providerState == "LOADING" || providerState == "REQUESTED" || providerState == "APPROVED" || providerState == "SIGNALING"
    val ringColor = when (providerState) {
        "SHARING", "CONNECTED" -> Green
        "LOADING", "READY", "REQUESTED", "APPROVED", "SIGNALING" -> Blue
        "DECLINED", "ENDED", "OFFLINE" -> Yellow
        "ID_ERROR" -> Red
        else -> Blue
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text("READY TO SHARE", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text("Share your connection with a friend", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))

        Ring(color = ringColor, size = 190.dp, pulse = connectionBusy || providerState == "SHARING", label = if (providerState == "SHARING") "SHARING" else "READY")

        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .clickable { copyId(linkoId) }
                .padding(14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("USERNAME", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                Text("@${username.removePrefix("@").ifBlank { "LINKO User" }}", color = Green, fontSize = 17.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("LINKO ID (TAP TO COPY)", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (linkoId.isBlank()) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (linkoId.isNotBlank()) linkoId else "Loading LINKO ID…",
                        color = if (linkoId.isBlank()) Blue else Green,
                        fontSize = 18.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(enabled = linkoId.isNotBlank(), onClick = { copyId(linkoId) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy LINKO ID", tint = if (linkoId.isNotBlank()) Green else TextMuted)
            }
        }

        if (copied) Text("✓ LINKO ID COPIED", color = Green, fontSize = 10.sp, fontFamily = JetBrainsMono, modifier = Modifier.padding(top = 6.dp))
        
        if (pendingRequestId != null || providerState == "REQUESTED") {
            Spacer(Modifier.height(14.dp))
            LinkoCard(modifier = Modifier.fillMaxWidth().border(1.dp, Green, RoundedCornerShape(12.dp))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Green, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("⚡ FRIEND WANTS TO CONNECT", color = Green, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text("A LINKO friend has requested to use your shared internet connection.", color = TextPrimary, fontSize = 11.sp, fontFamily = JetBrainsMono)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("APPROVE & SHARE", {
                        scope.launch {
                            LinkoEngineBridge.approvePendingProviderRequest { state ->
                                if (state == "approved") {
                                    LinkoEngineBridge.startApprovedProviderSession()
                                    pendingRequestId = null
                                    providerState = "SHARING"
                                    Toast.makeText(context, "Connection Approved! Sharing Internet.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }, color = Green)
                    PrimaryButton("DECLINE", {
                        scope.launch {
                            LinkoEngineBridge.denyPendingProviderRequest {
                                pendingRequestId = null
                                providerState = "READY"
                            }
                        }
                    }, color = Red, outline = true)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        LinkoCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("ROLE", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                    Text("PROVIDER", color = Green, fontSize = 17.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("STATUS", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connectionBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Blue, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            providerState,
                            color = when (providerState) {
                                "OFFLINE", "DECLINED", "ENDED" -> Red
                                "LOADING", "REQUESTED", "APPROVED", "SIGNALING" -> Blue
                                else -> Green
                            },
                            fontSize = 15.sp,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (providerState) {
                    "READY" -> "Waiting for a friend to connect… Your LINKO ID is ready."
                    "SHARING" -> "A friend is actively using your shared internet connection."
                    "REQUESTED" -> "A friend is requesting access to your connection."
                    "APPROVED" -> "Connection approved. Preparing the secure tunnel…"
                    "SIGNALING" -> "Establishing encrypted AES-256-GCM data plane…"
                    "LOADING" -> "Loading your account profile and LINKO ID…"
                    "OFFLINE" -> "The connection service is offline."
                    else -> "Preparing your LINKO provider…"
                },
                color = TextSub,
                fontSize = 11.sp,
                fontFamily = JetBrainsMono
            )
        }

        Spacer(Modifier.weight(1f))
        Text("Your connection is never shared without your approval.", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(12.dp))
    }
}
