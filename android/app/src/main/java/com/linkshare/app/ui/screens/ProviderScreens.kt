package com.linkshare.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoFriendsApi
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*

private object ProviderReadyAlgorithm {
    fun requestNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && context is Activity && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7002)
        }
    }
}

@Composable
fun ProviderReadyScreen(onIncomingRequest: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { LinkoAuth(context) }
    val friendsApi = remember { LinkoFriendsApi { auth.currentAccessToken() } }
    var linkoId by remember { mutableStateOf("LINKO ID unavailable") }
    var providerState by remember { mutableStateOf("STARTING") }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ProviderReadyAlgorithm.requestNotificationPermission(context)
        LinkoProviderService.start(context)
        runCatching { friendsApi.ensureProfile("LINKO User") }
            .onSuccess { profile -> linkoId = profile.optString("linko_id").takeIf { it.isNotBlank() } ?: linkoId }
        LinkoRealtimeManager.events.collect { event ->
            when (event) {
                is LinkoRealtimeEvent.FriendRequestReceived -> onIncomingRequest()
                is LinkoRealtimeEvent.SessionStateChanged -> {
                    providerState = when (event.state) {
                        "requested" -> "REQUESTED"
                        "approved" -> "APPROVED"
                        "signaling" -> "SIGNALING"
                        "connected" -> "SHARING"
                        "denied" -> "DECLINED"
                        "revoked", "expired" -> "ENDED"
                        else -> providerState
                    }
                }
                is LinkoRealtimeEvent.TransportError -> if (providerState == "STARTING") providerState = "OFFLINE"
                else -> Unit
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text("READY TO SHARE", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text("Friends connect using your LINKO ID or username", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(26.dp))
        Ring(if (providerState == "SHARING") Green else if (providerState == "OFFLINE") Red else Yellow, 190.dp, pulse = providerState == "READY" || providerState == "SHARING", label = "PROVIDER")
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).border(1.dp, Border, RoundedCornerShape(12.dp)).padding(start = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("YOUR LINKO ID", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                Text(linkoId, color = Green, fontSize = 19.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text("Use this ID or your username to find this account", color = TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono)
            }
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LINKO ID", linkoId))
                copied = true
            }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy LINKO ID", tint = Green) }
        }
        if (copied) Text("COPIED", color = Green, fontSize = 9.sp, fontFamily = JetBrainsMono, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        LinkoCard {
            InfoRow("ROLE", "PROVIDER", "This device shares its internet only after you approve a request", Green, true)
            Spacer(Modifier.height(12.dp))
            InfoRow("STATUS", providerState, when (providerState) {
                "SHARING" -> "A receiver is currently using your authorized connection"
                "OFFLINE" -> "Realtime/control-plane connection is unavailable"
                "REQUESTED" -> "A receiver request is being processed"
                else -> "Waiting for a realtime connection request from a LINKO friend"
            }, if (providerState == "OFFLINE") Red else Green)
            Spacer(Modifier.height(12.dp))
            Text("When a friend requests access, LINKO will show the request and let you ACCEPT or DECLINE it.", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
        }
        Spacer(Modifier.weight(1f))
        Text("Your connection is never shared without your approval.", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(24.dp))
    }
}
