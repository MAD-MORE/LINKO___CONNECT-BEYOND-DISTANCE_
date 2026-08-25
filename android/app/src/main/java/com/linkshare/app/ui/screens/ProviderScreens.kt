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
import androidx.compose.material3.CircularProgressIndicator
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
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.Ring
import com.linkshare.app.ui.theme.Border
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.Surface
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.JetBrainsMono
import kotlinx.coroutines.delay

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
    var username by remember { mutableStateOf(auth.currentUsername() ?: auth.currentDisplayName().orEmpty()) }
    var linkoId by remember { mutableStateOf(auth.currentLinkoId().orEmpty()) }
    var providerState by remember { mutableStateOf(if (linkoId.isNotBlank()) "READY" else "LOADING") }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        username = auth.currentUsername() ?: auth.currentDisplayName().orEmpty()
        linkoId = auth.currentLinkoId().orEmpty()
        providerState = if (linkoId.isNotBlank()) "READY" else "ID_ERROR"
        ProviderReadyAlgorithm.requestNotificationPermission(context)
        LinkoProviderService.start(context)

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
                is LinkoRealtimeEvent.TransportError -> if (linkoId.isNotBlank()) providerState = "OFFLINE"
                else -> Unit
            }
        }
    }

    LaunchedEffect(copied) {
        if (copied) { delay(1400); copied = false }
    }

    val connectionBusy = providerState == "LOADING" || providerState == "REQUESTED" || providerState == "APPROVED" || providerState == "SIGNALING"
    val shareVisualActive = linkoId.isNotBlank() && providerState != "OFFLINE" && providerState != "ID_ERROR"

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text("READY TO SHARE", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text("Share your connection with a friend", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))

        Ring(color = if (shareVisualActive) Green else Border, size = 190.dp, idle = true, label = "SHARE")

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).border(1.dp, Border, RoundedCornerShape(12.dp)).padding(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("USERNAME", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                Text("@${username.removePrefix("@").ifBlank { "LINKO User" }}", color = Green, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("LINKO ID", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (linkoId.isBlank()) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Green, strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp))
                    }
                    Text(if (linkoId.isNotBlank()) linkoId else "Loading LINKO ID…", color = if (linkoId.isBlank()) Red else Green, fontSize = 19.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(enabled = linkoId.isNotBlank(), onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LINKO ID", linkoId)); copied = true
            }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy LINKO ID", tint = if (linkoId.isNotBlank()) Green else TextMuted) }
        }

        if (copied) Text("COPIED", color = Green, fontSize = 9.sp, fontFamily = JetBrainsMono, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(14.dp))

        LinkoCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("ROLE", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono); Text("PROVIDER", color = Green, fontSize = 17.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
                Column(horizontalAlignment = Alignment.End) {
                    Text("STATUS", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connectionBusy) { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Green, strokeWidth = 2.dp); Spacer(Modifier.width(7.dp)) }
                        Text(providerState, color = when (providerState) { "OFFLINE", "ID_ERROR", "DECLINED", "ENDED" -> Red; "LOADING", "REQUESTED", "APPROVED", "SIGNALING" -> TextSub; else -> Green }, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(when (providerState) { "READY" -> "Waiting for a friend to connect..."; "SHARING" -> "A friend is using your authorized connection."; "REQUESTED" -> "A friend is requesting access to your connection."; "APPROVED" -> "Connection approved. Preparing the tunnel..."; "SIGNALING" -> "Establishing a secure connection..."; "OFFLINE" -> "The connection service is offline."; "ID_ERROR" -> "Your LINKO profile is not initialized."; else -> "Preparing your LINKO provider..." }, color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
            if (connectionBusy) {
                Spacer(Modifier.height(12.dp))
                Text(when (providerState) { "LOADING" -> "Preparing your sharing service…"; "REQUESTED" -> "Waiting for your approval…"; "APPROVED" -> "Preparing secure sharing…"; "SIGNALING" -> "Connecting the devices…"; else -> "Working…" }, color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Your connection is never shared without your approval.", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(12.dp))
    }
}
