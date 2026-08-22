package com.linkshare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.auth.AuthResult
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.ui.components.GhostButton
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.LinkoInput
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SignInScreen(auth: LinkoAuth, onSignedIn: () -> Unit, onCreateAccount: () -> Unit, onForgotPassword: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Sign In", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Reconnect to your LINKO identity", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(24.dp))
        LinkoInput("EMAIL", email, { email = it }, "you@example.com", "Your LINKO account email")
        Spacer(Modifier.height(12.dp))
        PasswordInput("PASSWORD", password, { password = it }, "Your password")
        message?.let {
            Spacer(Modifier.height(12.dp))
            LinkoCard { Text(it, color = if (it == "Authenticated") Green else Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono) }
        }
        Spacer(Modifier.height(8.dp))
        GhostButton("FORGOT PASSWORD?", onForgotPassword)
        Spacer(Modifier.height(8.dp))
        PrimaryButton(if (busy) "SIGNING IN…" else "SIGN IN", onClick = {
            if (busy) return@PrimaryButton
            busy = true
            message = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { auth.signIn(email, password) }
                busy = false
                if (result.success) {
                    message = "Authenticated"
                    onSignedIn()
                } else {
                    message = friendlyAuthError(result)
                }
            }
        })
        Spacer(Modifier.height(4.dp))
        GhostButton("Need an account? CREATE ACCOUNT", onCreateAccount)
        Spacer(Modifier.height(24.dp))
    }
}

private fun friendlyAuthError(result: AuthResult): String = when (result.message) {
    "valid_email_required" -> "Enter a valid email address."
    "password_min_8_chars" -> "Password must be at least 8 characters."
    "password_max_72_chars" -> "Password is too long. Use 72 characters or fewer."
    "session_missing" -> "Your session has expired. Please sign in again."
    "invalid_credentials" -> "Incorrect email or password."
    "too_many_requests" -> "Too many attempts. Please wait and try again."
    else -> result.message.replace('_', ' ')
}

@Composable
private fun PasswordInput(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) Blue else Border
    val labelColor = if (focused) Blue else TextMuted
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(Card)
            .border(1.dp, borderColor, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, color = labelColor, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.18.sp)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = value,
            onValueChange = { onValueChange(it); focused = true },
            placeholder = { Text(placeholder, color = TextMuted, fontFamily = JetBrainsMono, fontSize = 14.sp) },
            visualTransformation = PasswordVisualTransformation(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Blue,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, color = TextPrimary),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}
