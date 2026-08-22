package com.linkshare.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
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
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.auth.LinkoDeviceIdentity
import com.linkshare.app.network.LinkoDeviceRegistrar
import com.linkshare.app.network.LinkoRuntimeConfig
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onProfile: () -> Unit, onDevices: () -> Unit, onFriends: () -> Unit, onBlocked: () -> Unit, onHistory: () -> Unit, onSecurity: () -> Unit, onPrivacy: () -> Unit, onDataRetention: () -> Unit, onDeleteAccount: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 20.dp)) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Blue.copy(alpha = 0.13f)).border(2.dp, Blue.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) { Text("L", color = Blue, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(16.dp)); Column { Text("LINKO ACCOUNT", color = TextPrimary, fontSize = 17.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Text("Authenticated account", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(5.dp)); StatusChip("SECURE", Green) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionLabel("ACCOUNT"); LinkoCard { SettingsRow({ Text("👤", fontSize = 18.sp) }, "Profile", "Manage your account identity", onClick = onProfile); RowDivider(); SettingsRow({ Text("📱", fontSize = 18.sp) }, "Devices", "Manage trusted LINKO devices", onClick = onDevices) }
            SectionLabel("CONNECTIONS"); LinkoCard { SettingsRow({ Text("👥", fontSize = 18.sp) }, "Friends", "Manage trusted peers", onClick = onFriends); RowDivider(); SettingsRow({ Text("🚫", fontSize = 18.sp) }, "Blocked & Removed", "Manage blocked peers", onClick = onBlocked); RowDivider(); SettingsRow({ Text("🕐", fontSize = 18.sp) }, "Session History", "View your real sessions", onClick = onHistory) }
            SectionLabel("SECURITY & PRIVACY"); LinkoCard { SettingsRow({ Text("🔒", fontSize = 18.sp) }, "Security Engine", "Device-bound session protection", onClick = onSecurity); RowDivider(); SettingsRow({ Text("🛡", fontSize = 18.sp) }, "Privacy", "Connection privacy controls", onClick = onPrivacy); RowDivider(); SettingsRow({ Text("🗑", fontSize = 18.sp) }, "Data Retention", "Control stored session information", onClick = onDataRetention) }
            Spacer(Modifier.height(8.dp)); PrimaryButton("DELETE ACCOUNT", onDeleteAccount, color = Red, outline = true); Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun DeviceIdentityScreen(onManage: () -> Unit) {
    val context = LocalContext.current
    val identity = remember { LinkoDeviceIdentity() }
    val auth = remember { LinkoAuth(context) }
    val scope = rememberCoroutineScope()
    var fingerprint by remember { mutableStateOf(identity.deviceFingerprint()) }
    var copied by remember { mutableStateOf(false) }
    var rotating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun copyId() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LINKO Device ID", fingerprint))
        copied = true
        status = "Device ID copied to clipboard."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Device Identity", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp)); Text("A unique cryptographic identity for this LINKO installation", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(20.dp))
        LinkoCard {
            Text("LINKO DEVICE ID", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LNK-$fingerprint", color = Blue, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { copyId() }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy device ID", tint = Blue) }
            }
            Spacer(Modifier.height(5.dp)); Text(if (copied) "COPIED" else "Use this ID when identifying this device", color = if (copied) Green else TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
        }
        Spacer(Modifier.height(10.dp))
        LinkoCard {
            InfoRow("KEY PROTECTION", "Android Keystore", "Private signing key never leaves this device", accent = Green)
            Spacer(Modifier.height(14.dp)); InfoRow("IDENTITY", "Cryptographic", "Derived from this installation's public key", accent = Blue)
        }
        Spacer(Modifier.height(10.dp))
        LinkoCard {
            Text("ROTATE DEVICE ID", color = Yellow, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp)); Text("Rotation creates a new key pair and a new identity. The previous identity will no longer match this installation.", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Yellow.copy(alpha = .08f)).padding(4.dp)) {
                IconButton(enabled = !rotating, onClick = {
                    rotating = true; copied = false; status = null
                    scope.launch {
                        val newId = withContext(Dispatchers.IO) {
                            identity.rotate()
                            auth.clearLinkoSession()
                            if (LinkoRuntimeConfig.isConfigured() && auth.isSignedIn()) {
                                runCatching { LinkoDeviceRegistrar(LinkoRuntimeConfig.controlPlaneUrl, auth).ensureRegistered() }
                            }
                            identity.deviceFingerprint()
                        }
                        fingerprint = newId
                        rotating = false
                        status = "New device identity generated."
                    }
                }) { Icon(Icons.Filled.Refresh, contentDescription = "Rotate device ID", tint = Yellow) }
                Text(if (rotating) "ROTATING…" else "ROTATE ID", color = Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            }
        }
        status?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Green, fontSize = 11.sp, fontFamily = JetBrainsMono) }
        Spacer(Modifier.height(24.dp)); PrimaryButton("DONE", onManage); Spacer(Modifier.height(24.dp))
    }
}

@Composable fun SecurityEngineScreen(onHome: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.height(8.dp)); Text("Security Engine", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(4.dp)); Text("How LINKO protects sessions", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(24.dp)); Ring(Green, 160.dp, label = "SECURE", onClick = onHome); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("TUNNEL", "Encrypted", "Short-lived credentials", accent = Green) }; Spacer(Modifier.height(10.dp)); LinkoCard { InfoRow("KEYS", "Device-bound", "Never leave this device", accent = Blue) }; Spacer(Modifier.weight(1f)) } }

@Composable fun PrivacyScreen(onManageData: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(8.dp)); Text("Privacy", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Connection privacy controls", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(20.dp)); LinkoCard { InfoRow("BROWSING CONTENTS", "Not retained", "LINKO minimizes data collection", accent = Green) }; Spacer(Modifier.height(10.dp)); LinkoCard { InfoRow("SESSION METADATA", "Limited retention", "Used for security and usage", accent = Yellow) }; Spacer(Modifier.weight(1f)); PrimaryButton("MANAGE DATA", onManageData); Spacer(Modifier.height(24.dp)) } }

@Composable fun DataRetentionScreen(onDone: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(8.dp)); Text("Data Retention", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Control stored session information", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(20.dp)); LinkoCard { InfoRow("HISTORY", "Your sessions", "Review or delete session history") }; Spacer(Modifier.height(10.dp)); LinkoCard { InfoRow("ACCOUNT DATA", "Your identity", "Delete account permanently") }; Spacer(Modifier.weight(1f)); PrimaryButton("CLEAR SESSION HISTORY", {}, color = Red, outline = true); Spacer(Modifier.height(4.dp)); GhostButton("Done", onDone); Spacer(Modifier.height(24.dp)) } }

@Composable fun DeleteAccountScreen(onDelete: () -> Unit, onCancel: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(8.dp)); Text("Delete Account", color = Red, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("This action is permanent and irreversible", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(24.dp)); Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Red.copy(alpha = 0.05f)).border(1.dp, Red.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(16.dp)) { Text("⚠ WARNING", color = Red, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.15.sp); Spacer(Modifier.height(10.dp)); listOf("Your Linko identity will be removed", "All session history will be deleted", "Trusted connections will be lost", "This cannot be undone").forEach { item -> Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) { Box(Modifier.padding(top = 6.dp).size(5.dp).clip(CircleShape).background(Red)); Spacer(Modifier.width(10.dp)); Text(item, color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono) } } }; Spacer(Modifier.weight(1f)); PrimaryButton("PERMANENTLY DELETE ACCOUNT", onDelete, color = Red); Spacer(Modifier.height(4.dp)); GhostButton("Cancel, keep my account", onCancel); Spacer(Modifier.height(24.dp)) } }
