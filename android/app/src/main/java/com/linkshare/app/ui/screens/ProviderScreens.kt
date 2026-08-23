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
import com.linkshare.app.auth.LinkoDeviceIdentity
import com.linkshare.app.auth.LinkoRequestKey
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*

private object ProviderReadyAlgorithm {
    fun requestKey(context: Context): String = LinkoRequestKey(context).current()

    fun requestNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && context is Activity && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7002)
        }
    }
}

@Composable
fun ProviderReadyScreen() {
    val context = LocalContext.current
    val requestKey = remember { ProviderReadyAlgorithm.requestKey(context) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ProviderReadyAlgorithm.requestNotificationPermission(context)
        LinkoProviderService.start(context)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text("READY TO SHARE", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text("Share this key with someone you trust", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(26.dp))
        Ring(Green, 190.dp, pulse = true, label = "SHARING")
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).border(1.dp, Border, RoundedCornerShape(12.dp)).padding(start = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("REQUEST KEY", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                Text(requestKey, color = Green, fontSize = 19.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text("Expires in 10 minutes", color = TextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono)
            }
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LINKO request key", requestKey))
                copied = true
            }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy request key", tint = Green) }
        }
        if (copied) Text("COPIED", color = Green, fontSize = 9.sp, fontFamily = JetBrainsMono, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        LinkoCard {
            InfoRow("STATUS", "READY", "Waiting for a connection request", Green, true)
            Spacer(Modifier.height(12.dp))
            Text("Your device is ready. A connection request will appear here when someone uses this key.", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
        }
        Spacer(Modifier.weight(1f))
        Text("You can stop sharing at any time.", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(24.dp))
    }
}
