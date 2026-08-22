package com.linkshare.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.components.*
import com.linkshare.app.ui.theme.*

@Composable
fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text("LINKO", color = Blue, fontSize = 28.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.2.sp)
        Spacer(Modifier.height(4.dp)); Text("SECURE CONNECTION ENGINE", color = TextMuted, fontSize = 11.sp, fontFamily = JetBrainsMono, letterSpacing = 0.1.sp)
        Spacer(Modifier.height(40.dp)); Ring(Blue, 200.dp, idle = true, label = "READY", onClick = onCreateAccount)
        Spacer(Modifier.height(40.dp)); Text("Welcome", color = TextPrimary, fontSize = 20.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text("Secure connection beyond distance", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(14.dp)); Text("LINKO ENGINE READY", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, letterSpacing = 0.15.sp)
        Spacer(Modifier.weight(1f)); PrimaryButton("CREATE ACCOUNT", onCreateAccount); Spacer(Modifier.height(4.dp)); GhostButton("Already registered? SIGN IN", onSignIn); Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun CreateAccountScreen(onContinue: () -> Unit) {
    var identity by remember { mutableStateOf("") }; var displayName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Create Account", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp)); Text("Create your Linko identity", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(24.dp))
        LinkoInput("IDENTITY", identity, { identity = it }, "Phone or email", "Used for account verification"); Spacer(Modifier.height(12.dp))
        LinkoInput("DISPLAY NAME", displayName, { displayName = it }, "Your display name", "Visible to trusted friends"); Spacer(Modifier.weight(1f)); PrimaryButton("CONTINUE", onContinue); Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun VerifyScreen(onVerify: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Verify Account", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp)); Text("Confirm your identity securely", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(24.dp))
        LinkoCard {
            Text("VERIFICATION CODE", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.18.sp); Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) { repeat(6) { Box(Alignment.Center, Modifier.size(40.dp, 50.dp).clip(RoundedCornerShape(10.dp)).background(Card2).border(1.dp, Border, RoundedCornerShape(10.dp))) { Text("·", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) } } }
            Spacer(Modifier.height(14.dp)); Text("Short-lived verification code", color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth())
        }
        GhostButton("Resend code") {}; Spacer(Modifier.weight(1f)); PrimaryButton("VERIFY", onVerify); Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ProfileScreen(onSave: () -> Unit) {
    var name by remember { mutableStateOf("") }; var linkoId by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp)); Text("Set Up Profile", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp)); Text("Your Linko identity", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(28.dp))
        Avatar("P", Blue, 80.dp); Spacer(Modifier.height(28.dp)); LinkoInput("DISPLAY NAME", name, { name = it }, "Padmore", "Shown to trusted peers"); Spacer(Modifier.height(12.dp)); LinkoInput("LINKO ID", linkoId, { linkoId = it }, "@padmore", "Unique identity"); Spacer(Modifier.weight(1f)); PrimaryButton("SAVE PROFILE", onSave); Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun RegisterDeviceScreen(onRegister: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Register Device", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Bind this device to your identity", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(20.dp))
        LinkoCard { SettingsRow({ Text("📱", fontSize = 18.sp) }, "Android Device", "Trusted device identity", Blue) }; Spacer(Modifier.height(10.dp)); LinkoCard { SettingsRow({ Text("🔐", fontSize = 18.sp) }, "Protected Keys", "Stored securely on device", Green) }; Spacer(Modifier.weight(1f)); PrimaryButton("REGISTER DEVICE", onRegister); Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PermissionsScreen(onAllow: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Permissions", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Allow Linko to operate the connection engine", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(20.dp))
        LinkoCard { SettingsRow({ Text("📶", fontSize = 18.sp) }, "Network", "Connection and tunnel access", Blue) }; Spacer(Modifier.height(10.dp)); LinkoCard { SettingsRow({ Text("🔔", fontSize = 18.sp) }, "Notifications", "Session and request updates", Yellow) }; Spacer(Modifier.weight(1f)); PrimaryButton("ALLOW & CONTINUE", onAllow); Spacer(Modifier.height(24.dp))
    }
}
