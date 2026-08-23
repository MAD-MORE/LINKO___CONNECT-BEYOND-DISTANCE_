package com.linkshare.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.LinkoInput
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SignupOtpScreen(auth: LinkoAuth, onVerified: () -> Unit, onBack: () -> Unit) = OtpScreen(
    title = "Verify Email",
    subtitle = "Enter the 6-digit code sent to your email.",
    email = auth.pendingVerificationEmail().orEmpty(),
    verify = { auth.verifySignupOtp(it.first, it.second) },
    resend = { auth.sendSignupOtp(it) },
    onSuccess = onVerified,
    onBack = onBack,
)

@Composable
fun RecoveryOtpScreen(auth: LinkoAuth, onVerified: () -> Unit, onBack: () -> Unit) = OtpScreen(
    title = "Verify Recovery",
    subtitle = "Enter the 6-digit recovery code sent to your email.",
    email = auth.pendingVerificationEmail().orEmpty(),
    verify = { auth.verifyRecoveryOtp(it.first, it.second) },
    resend = { auth.sendRecoveryOtp(it) },
    onSuccess = onVerified,
    onBack = onBack,
)

@Composable
private fun OtpScreen(
    title: String,
    subtitle: String,
    email: String,
    verify: suspend (Pair<String, String>) -> com.linkshare.app.auth.AuthResult,
    resend: suspend (String) -> com.linkshare.app.auth.AuthResult,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(title, color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(24.dp))
        LinkoCard { Text(email.ifBlank { "EMAIL NOT AVAILABLE" }, color = TextPrimary, fontSize = 12.sp, fontFamily = JetBrainsMono) }
        Spacer(Modifier.height(12.dp))
        LinkoInput("VERIFICATION CODE", code, { if (it.length <= 6) code = it.filter(Char::isDigit) }, "123456", "Enter the code from your email")
        message?.let { Spacer(Modifier.height(12.dp)); LinkoCard { Text(it, color = if (success) Green else Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono) } }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(if (busy) "VERIFYING…" else "VERIFY CODE", {
            if (busy) return@PrimaryButton
            if (code.length != 6) { message = "Enter the 6-digit verification code."; return@PrimaryButton }
            busy = true; message = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { verify(email to code) }
                busy = false
                success = result.success
                message = if (result.success) "Verified" else result.message.replace('_', ' ')
                if (result.success) onSuccess()
            }
        })
        Spacer(Modifier.height(8.dp))
        PrimaryButton("RESEND CODE", {
            if (busy) return@PrimaryButton
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { resend(email) }
                busy = false
                success = result.success
                message = if (result.success) "A new code was sent." else result.message.replace('_', ' ')
            }
        }, outline = true)
        Spacer(Modifier.height(8.dp))
        PrimaryButton("BACK", onBack, outline = true)
        Spacer(Modifier.height(24.dp))
    }
}
