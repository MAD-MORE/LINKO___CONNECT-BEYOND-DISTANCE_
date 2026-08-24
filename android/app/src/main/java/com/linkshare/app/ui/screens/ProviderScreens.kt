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
import com.linkshare.app.network.LinkoControlPlaneApi
import com.linkshare.app.network.LinkoRuntimeConfig
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.delay

private object ProviderReadyAlgorithm {
    fun linkoId(context: Context): String = LinkoAuth(context).currentLinkoId()?.takeIf { it.isNotBlank() } ?: "LINKO ID unavailable"

    fun requestNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && context is Activity && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7002)
        }
    }
}

@Composable
fun ProviderReadyScreen(onIncomingRequest: () -> Unit) {
    val context = LocalContext.current
    val linkoId = remember { ProviderReadyAlgorithm.linkoId(context) }
    val auth = remember { LinkoAuth(context) }
    val api = remember { LinkoControlPlaneApi(LinkoRuntimeConfig.controlPlaneUrl, { auth.currentLinkoToken() }, { auth.currentDeviceId() }) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ProviderReadyAlgorithm.requestNotificationPermission(context)
        LinkoProviderService.start(context)
        while (true) {
            runCatching { api.getPendingProviderRequests().firstOrNull() }.getOrNull()?.let {
                onIncomingRequest()
                return@LaunchedEffect
            }
            delay(3_000L)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text("READY TO SHARE", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text("Friends connect using your LINKO ID or username", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(26.dp))
        Ring(Green, 190.dp, pulse = true, label = "PROVIDER")
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
            InfoRow("STATUS", "READY", "Waiting for a connection request from a LINKO friend", Green)
            Spacer(Modifier.height(12.dp))
            Text("When a friend requests access, LINKO will show the request and let you ACCEPT or DECLINE it.", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
        }
        Spacer(Modifier.weight(1f))
        Text("Your connection is never shared without your approval.", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(24.dp))
    }
}
