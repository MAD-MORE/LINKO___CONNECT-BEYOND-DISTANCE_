package com.linkshare.app.ui.screens

// Account-scoped Settings/Profile UI; identity is resolved from the authenticated account.
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.auth.LinkoDeviceIdentity
import com.linkshare.app.network.LinkoDeviceRegistrar
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.network.LinkoProfileApi
import com.linkshare.app.network.LinkoRuntimeConfig
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onProfile: () -> Unit,
    onDevices: () -> Unit,
    onFriends: () -> Unit,
    onBlocked: () -> Unit,
    onHistory: () -> Unit,
    onSecurity: () -> Unit,
    onPrivacy: () -> Unit,
    onDataRetention: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val context = LocalContext.current
    val auth = remember { LinkoAuth(context) }
    var displayName by remember { mutableStateOf(auth.currentDisplayName().orEmpty()) }
    var linkoId by remember { mutableStateOf(auth.currentLinkoId().orEmpty()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { auth.refreshAccountIdentity() }
        runCatching {
            withContext(Dispatchers.IO) { LinkoProfileApi(auth::currentAccessToken, auth::currentUserId).load() }
        }.onSuccess { profile ->
            auth.saveProfile(profile.displayName, profile.linkoId, profile.username)
            displayName = profile.displayName
            linkoId = profile.linkoId
        }.onFailure {
            if (linkoId.isBlank()) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        LinkoFriendsApiHolder.api.ensureProfile(displayName.ifBlank { null })
                    }
                }.onSuccess { profile ->
                    val name = profile.optString("display_name").takeIf { it.isNotBlank() } ?: displayName
                    val id = profile.optString("linko_id").takeIf { it.isNotBlank() }
                    auth.saveProfile(name, id)
                    displayName = name
                    linkoId = id.orEmpty()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(Surface).padding(16.dp),
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Blue.copy(.13f)), contentAlignment = Alignment.Center) {
                Text(if (displayName.isNotBlank()) displayName.take(1).uppercase() else "L", color = Blue, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(if (displayName.isNotBlank()) displayName else "LINKO ACCOUNT", color = TextPrimary, fontSize = 17.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text(if (linkoId.isNotBlank()) "@$linkoId" else "Authenticated account", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
                Spacer(Modifier.height(5.dp))
                StatusChip("SECURE", Green)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionLabel("ACCOUNT")
            LinkoCard {
                SettingsRow({ Text("👤", fontSize = 18.sp) }, "Profile", if (linkoId.isNotBlank()) "$displayName • @$linkoId" else "Manage your account identity", onClick = onProfile)
                RowDivider()
                SettingsRow({ Text("📱", fontSize = 18.sp) }, "Devices", "Manage this LINKO device", onClick = onDevices)
            }

            SectionLabel("CONNECTIONS")
            LinkoCard {
                SettingsRow({ Text("👥", fontSize = 18.sp) }, "Friends", "Your real LINKO connections", onClick = onFriends)
                RowDivider()
                SettingsRow({ Text("🚫", fontSize = 18.sp) }, "Blocked & Removed", "Manage blocked peers", onClick = onBlocked)
                RowDivider()
                SettingsRow({ Text("🕐", fontSize = 18.sp) }, "Session History", "Your real sessions only", onClick = onHistory)
            }

            SectionLabel("SECURITY & PRIVACY")
            LinkoCard {
                SettingsRow({ Text("🔒", fontSize = 18.sp) }, "Security Engine", "Connection protection", onClick = onSecurity)
                RowDivider()
                SettingsRow({ Text("🛡", fontSize = 18.sp) }, "Privacy", "Connection privacy controls", onClick = onPrivacy)
                RowDivider()
                SettingsRow({ Text("🗑", fontSize = 18.sp) }, "Data Retention", "Control stored session information", onClick = onDataRetention)
            }

            Spacer(Modifier.height(8.dp))
            PrimaryButton("DELETE ACCOUNT", onDeleteAccount, color = Red, outline = true)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AccountProfileScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { LinkoAuth(context) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(auth.currentDisplayName().orEmpty()) }
    var username by remember { mutableStateOf(auth.currentUsername() ?: auth.currentDisplayName().orEmpty()) }
    var linkoId by remember { mutableStateOf(auth.currentLinkoId().orEmpty()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) { LinkoProfileApi(auth::currentAccessToken, auth::currentUserId).load() }
        }.onSuccess { profile ->
            name = profile.displayName
            username = profile.username ?: profile.displayName
            linkoId = profile.linkoId
            auth.saveProfile(profile.displayName, profile.linkoId, username)
        }.onFailure {
            message = "Could not load your account profile."
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Your Profile", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("This identity belongs to the signed-in account", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(20.dp))

        LinkoCard {
            Text("ACCOUNT IDENTITY", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            InfoRow("DISPLAY NAME", if (name.isBlank()) "Loading…" else name, "Used across LINKO", accent = Blue)
            Spacer(Modifier.height(10.dp))
            InfoRow("USERNAME", if (username.isBlank()) "Loading…" else "@${username.removePrefix("@")}", "The same username shown when sharing your connection", accent = Green)
            Spacer(Modifier.height(10.dp))
            InfoRow("LINKO ID", if (linkoId.isBlank()) "Loading…" else "@$linkoId", "Use this ID when adding friends", accent = Green)
        }

        Spacer(Modifier.height(12.dp))
        LinkoCard {
            Text("EDIT DISPLAY NAME", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LinkoInput("DISPLAY NAME", name, { name = it }, "Your display name", "2–40 characters")
        }

        Spacer(Modifier.height(10.dp))
        LinkoCard {
            Text("LINKO ID", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(if (linkoId.isBlank()) "Loading…" else "@$linkoId", color = Blue, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("This ID is generated for your account and is not a device-local default.", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                "COPY LINKO ID",
                {
                    val clean = linkoId.removePrefix("@")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("LINKO ID", clean)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "LINKO ID copied: $clean", Toast.LENGTH_SHORT).show()
                    message = "✓ LINKO ID copied: $clean"
                },
                color = Green,
                outline = true
            )
        }

        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = if (it.contains("Could") || it.contains("✕")) Red else Green, fontSize = 11.sp, fontFamily = JetBrainsMono)
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton(
            if (saving) "SAVING…" else "SAVE PROFILE",
            {
                if (!saving && !loading) {
                    saving = true
                    message = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val profile = LinkoProfileApi(auth::currentAccessToken, auth::currentUserId).updateDisplayName(name)
                                auth.saveProfile(profile.displayName, profile.linkoId, profile.username)
                            }
                        }.onSuccess {
                            message = "Profile updated on this account."
                        }.onFailure {
                            message = "Could not update profile. Check your connection."
                        }
                        saving = false
                    }
                }
            },
        )
        Spacer(Modifier.height(4.dp))
        GhostButton("Done", onDone)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DeviceIdentityScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val identity = remember { LinkoDeviceIdentity() }
    val auth = remember { LinkoAuth(context) }
    val scope = rememberCoroutineScope()
    fun currentId(): String = auth.currentDeviceId()?.takeIf { it.isNotBlank() } ?: "LNK-${identity.deviceFingerprint()}"
    var deviceId by remember { mutableStateOf(currentId()) }
    var copied by remember { mutableStateOf(false) }
    var rotating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun copyId() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LINKO Device ID", deviceId))
        copied = true
        status = "Device ID copied."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Device Identity", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Your unique LINKO device identifier", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(20.dp))

        LinkoCard {
            Text("LINKO DEVICE ID", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deviceId, color = Blue, fontSize = 18.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { copyId() }) {
                    Icon(Icons.Filled.ContentCopy, "Copy device ID", tint = Blue)
                }
            }
            Text(if (copied) "COPIED" else "This is also your LINKO sharing ID", color = if (copied) Green else TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
        }

        Spacer(Modifier.height(10.dp))
        LinkoCard {
            InfoRow(
                "STATUS",
                if (auth.hasRegisteredDevice()) "REGISTERED" else "LOCAL",
                "One ID is used for device and sharing",
                accent = if (auth.hasRegisteredDevice()) Green else Yellow,
            )
        }

        Spacer(Modifier.height(10.dp))
        LinkoCard {
            Text("ROTATE DEVICE ID", color = Yellow, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Replace the current LINKO ID with a new one.", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                if (rotating) "ROTATING…" else "ROTATE ID",
                {
                    if (!rotating) {
                        rotating = true
                        copied = false
                        status = null
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    identity.rotate()
                                    auth.clearLinkoSession()
                                    if (LinkoRuntimeConfig.isConfigured() && auth.isSignedIn()) {
                                        LinkoDeviceRegistrar(LinkoRuntimeConfig.controlPlaneUrl, auth).ensureRegistered()
                                    }
                                    currentId()
                                }
                            }
                            result.onSuccess {
                                deviceId = it
                                status = "Device ID changed successfully."
                            }.onFailure {
                                status = "Could not change the device ID."
                            }
                            rotating = false
                        }
                    }
                },
                color = Yellow,
                outline = true,
            )
        }

        status?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = if (it.startsWith("Could")) Red else Green, fontSize = 11.sp, fontFamily = JetBrainsMono)
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton("DONE", onDone, outline = true)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SecurityEngineScreen(onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text("Security Engine", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text("How LINKO protects sessions", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Ring(Green, 160.dp, label = "SECURE", onClick = onHome)
        Spacer(Modifier.height(24.dp))
        LinkoCard { InfoRow("TUNNEL", "Encrypted", "Short-lived credentials", accent = Green) }
        Spacer(Modifier.height(10.dp))
        LinkoCard { InfoRow("DEVICE", "Protected", "Session identity stays on this device", accent = Blue) }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun PrivacyScreen(onManageData: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Privacy", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Connection privacy controls", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(20.dp))
        LinkoCard { InfoRow("BROWSING CONTENTS", "Not retained", "LINKO minimizes data collection", accent = Green) }
        Spacer(Modifier.height(10.dp))
        LinkoCard { InfoRow("SESSION METADATA", "Limited retention", "Used for security and usage", accent = Yellow) }
        Spacer(Modifier.weight(1f))
        PrimaryButton("MANAGE DATA", onManageData)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DataRetentionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var cleared by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Data Retention", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Control stored session information", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(20.dp))
        LinkoCard { InfoRow("HISTORY", if (cleared) "Cleared" else "Your sessions", "Review or delete local session history") }
        Spacer(Modifier.height(10.dp))
        LinkoCard { InfoRow("ACCOUNT DATA", "Your identity", "Stored locally for fast offline access") }
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            if (cleared) "HISTORY CLEARED" else "CLEAR SESSION HISTORY",
            {
                cleared = true
                Toast.makeText(context, "Local session history cleared", Toast.LENGTH_SHORT).show()
            },
            color = if (cleared) Green else Red,
            outline = true
        )
        Spacer(Modifier.height(4.dp))
        GhostButton("Done", onDone)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DeleteAccountScreen(onDelete: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Delete Account", color = Red, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("This action is permanent and irreversible", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(24.dp))
        LinkoCard { Text("Before deleting, LINKO will clear your account session and local device data.", color = TextPrimary, fontSize = 13.sp, fontFamily = JetBrainsMono) }
        Spacer(Modifier.weight(1f))
        PrimaryButton("PERMANENTLY DELETE ACCOUNT", onDelete, color = Red)
        Spacer(Modifier.height(4.dp))
        GhostButton("Cancel, keep my account", onCancel)
        Spacer(Modifier.height(24.dp))
    }
}
