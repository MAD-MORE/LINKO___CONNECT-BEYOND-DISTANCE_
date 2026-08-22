package com.linkshare.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.auth.PasswordRecovery
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.LinkoInput
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Forgot Password", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("We'll send a secure password reset link to your email.", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(24.dp))
        LinkoInput("EMAIL", email, { email = it }, "you@example.com", "The email used for your LINKO account")
        message?.let {
            Spacer(Modifier.height(12.dp))
            LinkoCard { Text(it, color = if (success) Green else Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono) }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(if (busy) "SENDING…" else "SEND RESET LINK", onClick = {
            if (busy) return@PrimaryButton
            busy = true
            message = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { PasswordRecovery.send(email) }
                busy = false
                if (result.success) {
                    success = true
                    message = "Check your email for the password reset link."
                } else {
                    success = false
                    message = when (result.message) {
                        "valid_email_required" -> "Enter a valid email address."
                        "too_many_requests" -> "Too many attempts. Please wait and try again."
                        else -> result.message.replace('_', ' ')
                    }
                }
            }
        })
        Spacer(Modifier.height(8.dp))
        PrimaryButton("BACK TO SIGN IN", onBack)
        Spacer(Modifier.height(24.dp))
    }
}
