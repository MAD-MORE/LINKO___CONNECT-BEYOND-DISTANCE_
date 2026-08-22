package com.linkshare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*

@Composable
fun SettingsScreen(onProfile: () -> Unit, onDevices: () -> Unit, onFriends: () -> Unit, onBlocked: () -> Unit, onHistory: () -> Unit, onSecurity: () -> Unit, onPrivacy: () -> Unit, onDataRetention: () -> Unit, onDeleteAccount: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 20.dp)) {
            Box(Alignment.Center, Modifier.size(56.dp).clip(CircleShape).background(Blue.copy(alpha = 0.13f)).border(2.dp, Blue.copy(alpha = 0.35f), CircleShape)) { Text("P", color = Blue, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(16.dp)); Column { Text("Padmore", color = TextPrimary, fontSize = 17.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Text("@padmore", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(5.dp)); StatusChip("VERIFIED", Green) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionLabel("ACCOUNT"); LinkoCard { SettingsRow({ Text("👤", fontSize = 18.sp) }, "Profile", "Padmore • @padmore", onClick = onProfile); RowDivider(); SettingsRow({ Text("📱", fontSize = 18.sp) }, "Devices", "1 trusted device", onClick = onDevices) }
            SectionLabel("CONNECTIONS"); LinkoCard { SettingsRow({ Text("👥", fontSize = 18.sp) }, "Friends", "4 trusted peers", onClick = onFriends); RowDivider(); SettingsRow({ Text("🚫", fontSize = 18.sp) }, "Blocked & Removed", "0 blocked", onClick = onBlocked); RowDivider(); SettingsRow({ Text("🕐", fontSize = 18.sp) }, "Session History", "3 recent sessions", onClick = onHistory) }
            SectionLabel("SECURITY & PRIVACY"); LinkoCard { SettingsRow({ Text("🔒", fontSize = 18.sp) }, "Security Engine", onClick = onSecurity); RowDivider(); SettingsRow({ Text("🛡", fontSize = 18.sp) }, "Privacy", onClick = onPrivacy); RowDivider(); SettingsRow({ Text("🗑", fontSize = 18.sp) }, "Data Retention", onClick = onDataRetention) }
            Spacer(Modifier.height(8.dp)); PrimaryButton("DELETE ACCOUNT", onDeleteAccount, color = Red, outline = true); Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable fun DeviceIdentityScreen(onManage: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(8.dp)); Text("Device Identity", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Trusted devices and keys", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(20.dp)); LinkoCard { InfoRow("THIS DEVICE", "Android • Trusted", "Registered identity", accent = Green) }; Spacer(Modifier.height(10.dp)); LinkoCard { InfoRow("KEYS", "Protected", "Local secure storage", accent = Blue) }; Spacer(Modifier.weight(1f)); PrimaryButton("MANAGE DEVICE", onManage); Spacer(Modifier.height(24.dp)) } }

@Composable fun SecurityEngineScreen(onHome: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Spacer(Modifier.height(8.dp)); Text("Security Engine", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(4.dp)); Text("How LINKO protects sessions", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(24.dp)); Ring(Green, 160.dp, label = "SECURE", onClick = onHome); Spacer(Modifier.height(24.dp)); LinkoCard { InfoRow("TUNNEL", "Encrypted", "Short-lived credentials", accent = Green) }; Spacer(Modifier.height(10.dp)); LinkoCard { InfoRow("KEYS", "Device-bound", "Never leave this device", accent = Blue) }; Spacer(Modifier.weight(1f)) } }

@Composable fun PrivacyScreen(onManageData: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(8.dp)); Text("Privacy", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Connection privacy controls", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(20.dp)); LinkoCard { InfoRow("BROWSING CONTENTS", "Not retained", "LINKO minimizes data collection", accent = Green) }; Spacer(Modifier.height(10.dp)); LinkoCard { InfoRow("SESSION METADATA", "Limited retention", "Used for security and usage", accent = Yellow) }; Spacer(Modifier.weight(1f)); PrimaryButton("MANAGE DATA", onManageData); Spacer(Modifier.height(24.dp)) } }

@Composable fun DataRetentionScreen(onDone: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(8.dp)); Text("Data Retention", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Control stored session information", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(20.dp)); LinkoCard { InfoRow("HISTORY", "Your sessions", "Review or delete session history") }; Spacer(Modifier.height(10.dp)); LinkoCard { InfoRow("ACCOUNT DATA", "Your identity", "Delete account permanently") }; Spacer(Modifier.weight(1f)); PrimaryButton("CLEAR SESSION HISTORY", {}, color = Red, outline = true); Spacer(Modifier.height(4.dp)); GhostButton("Done", onDone); Spacer(Modifier.height(24.dp)) } }

@Composable fun DeleteAccountScreen(onDelete: () -> Unit, onCancel: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(8.dp)); Text("Delete Account", color = Red, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("This action is permanent and irreversible", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(24.dp)); Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Red.copy(alpha = 0.05f)).border(1.dp, Red.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(16.dp)) { Text("⚠ WARNING", color = Red, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.15.sp); Spacer(Modifier.height(10.dp)); listOf("Your Linko identity will be removed", "All session history will be deleted", "Trusted connections will be lost", "This cannot be undone").forEach { item -> Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) { Box(Modifier.padding(top = 6.dp).size(5.dp).clip(CircleShape).background(Red)); Spacer(Modifier.width(10.dp)); Text(item, color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono) } } }; Spacer(Modifier.weight(1f)); PrimaryButton("PERMANENTLY DELETE ACCOUNT", onDelete, color = Red); Spacer(Modifier.height(4.dp)); GhostButton("Cancel, keep my account", onCancel); Spacer(Modifier.height(24.dp)) } }
