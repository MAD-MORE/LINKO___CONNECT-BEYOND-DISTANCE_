package com.linkshare.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Accent.copy(alpha = 0.10f), GradientMid),
                    radius = 1100f
                )
            )
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.8f))

            // Wordmark
            Text("LINKO", color = Blue, fontSize = 32.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(4.dp))
            Text("CONNECT BEYOND DISTANCE", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.20.sp)

            Spacer(Modifier.height(36.dp))

            // Hero Ring in GlassCard
            GlassCard(accentColor = Accent) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(10.dp))
                    Ring(Blue, 190.dp, idle = true, label = "READY", onClick = onCreateAccount)
                    Spacer(Modifier.height(16.dp))
                    Text("Secure connection engine", color = TextPrimary, fontSize = 16.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Share internet securely with trusted friends", color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono)
                    Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            PrimaryButton("CREATE ACCOUNT", onCreateAccount)
            Spacer(Modifier.height(4.dp))
            GhostButton("Already registered? SIGN IN", onSignIn)
            Spacer(Modifier.height(12.dp))
        }
    }
}


@Composable fun CreateAccountScreen(onContinue: () -> Unit) { var identity by remember { mutableStateOf("") }; var displayName by remember { mutableStateOf("") }; Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) { Spacer(Modifier.height(8.dp)); Text("Create Account", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Create your Linko identity", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(24.dp)); LinkoInput("IDENTITY", identity, { identity = it }, "Phone or email", "Used for account verification"); Spacer(Modifier.height(12.dp)); LinkoInput("DISPLAY NAME", displayName, { displayName = it }, "Your display name", "Visible to trusted friends"); Spacer(Modifier.weight(1f)); PrimaryButton("CONTINUE", onContinue); Spacer(Modifier.height(24.dp)) } }

@Composable fun VerifyScreen(onVerify: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(8.dp)); Text("Verify Account", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Confirm your identity securely", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(24.dp)); LinkoCard { Text("VERIFICATION CODE", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) { repeat(6) { Box(Modifier.size(40.dp, 50.dp).clip(RoundedCornerShape(10.dp)).background(Card2).border(1.dp, Border, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text("·", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) } } }; Spacer(Modifier.height(14.dp)); Text("Short-lived verification code", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono) }; GhostButton("Resend code") {}; Spacer(Modifier.weight(1f)); PrimaryButton("VERIFY", onVerify); Spacer(Modifier.height(24.dp)) } }

@Composable
fun ProfileScreen(onSave: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { LinkoAuth(context) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(auth.currentDisplayName().orEmpty()) }
    var linkoId by remember { mutableStateOf(auth.currentLinkoId().orEmpty()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { auth.refreshAccountIdentity() }
        name = auth.currentDisplayName().orEmpty()
        if (linkoId.isBlank()) {
            runCatching { withContext(Dispatchers.IO) { LinkoFriendsApiHolder.api.ensureProfile(name.ifBlank { null }) } }
                .onSuccess { profile ->
                    val resolvedName = profile.optString("display_name").takeIf { it.isNotBlank() } ?: name
                    val resolvedId = profile.optString("linko_id").takeIf { it.isNotBlank() }
                    auth.saveProfile(resolvedName, resolvedId); name = resolvedName; linkoId = resolvedId.orEmpty()
                }
        }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp)); Text("Your Profile", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(4.dp)); Text("Identity from your authenticated LINKO account", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(28.dp)); Avatar(if (name.isNotBlank()) name.take(1).uppercase() else "L", Blue, 80.dp); Spacer(Modifier.height(28.dp)); LinkoCard { InfoRow("DISPLAY NAME", if (loading) "LOADING…" else name.ifBlank { "Account name unavailable" }, accent = Blue); Spacer(Modifier.height(12.dp)); InfoRow("LINKO ID", if (loading) "LOADING…" else linkoId.ifBlank { "Not registered yet" }, accent = Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("SAVE PROFILE", { auth.saveProfile(name, linkoId); onSave() }); Spacer(Modifier.height(24.dp))
    }
}

@Composable fun RegisterDeviceScreen(onRegister: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(8.dp)); Text("Register Device", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Bind this device to your identity", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(20.dp)); LinkoCard { SettingsRow({ Text("📱", fontSize = 18.sp) }, "Android Device", "Trusted device identity", Blue) }; Spacer(Modifier.height(10.dp)); LinkoCard { SettingsRow({ Text("🔐", fontSize = 18.sp) }, "Protected Keys", "Stored securely on device", Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("REGISTER DEVICE", onRegister); Spacer(Modifier.height(24.dp)) } }

@Composable fun PermissionsScreen(onAllow: () -> Unit) {
    val context = LocalContext.current
    var notificationGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var vpnGranted by remember { mutableStateOf(VpnService.prepare(context) == null) }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> vpnGranted = result.resultCode == Activity.RESULT_OK || VpnService.prepare(context) == null; if (vpnGranted) onAllow() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> notificationGranted = granted; val prepareIntent = VpnService.prepare(context); if (prepareIntent == null) { vpnGranted = true; onAllow() } else vpnLauncher.launch(prepareIntent) }
    fun continueWithPermissions() { if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) { notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS); return }; val prepareIntent = VpnService.prepare(context); if (prepareIntent == null) { vpnGranted = true; onAllow() } else vpnLauncher.launch(prepareIntent) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { Spacer(Modifier.height(8.dp)); Text("Permissions", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Allow Linko to operate the connection engine", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(20.dp)); LinkoCard { SettingsRow({ Text("📶", fontSize = 18.sp) }, "Private VPN tunnel", if (vpnGranted) "Ready" else "Required for secure connections", Blue) }; Spacer(Modifier.height(10.dp)); LinkoCard { SettingsRow({ Text("🔔", fontSize = 18.sp) }, "Notifications", if (notificationGranted) "Ready" else "Required for connection requests", Yellow) }; Spacer(Modifier.height(10.dp)); LinkoCard { SettingsRow({ Text("🌐", fontSize = 18.sp) }, "Internet access", "Required to reach the LINKO service", Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("ALLOW & CONTINUE", ::continueWithPermissions); Spacer(Modifier.height(24.dp)) }
}
