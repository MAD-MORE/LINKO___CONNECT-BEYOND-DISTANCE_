package com.linkshare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.auth.AuthResult
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.LinkoInput
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LinkoSignUpScreen(auth: LinkoAuth, onRegistered: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Create Account", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Create your Linko identity", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(24.dp))
        LinkoInput("EMAIL", email, { email = it }, "you@example.com", "Used for account verification")
        Spacer(Modifier.height(12.dp))
        LinkoInput("DISPLAY NAME", displayName, { displayName = it }, "Your display name", "Visible to trusted friends")
        Spacer(Modifier.height(12.dp))
        SecretField("PASSWORD", password, { password = it }, "At least 8 characters")
        Spacer(Modifier.height(12.dp))
        SecretField("CONFIRM PASSWORD", confirm, { confirm = it }, "Repeat your password")
        message?.let { Spacer(Modifier.height(10.dp)); LinkoCard { Text(it, color = if (it == "Account created") Green else Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono) } }
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (busy) "CREATING…" else "CREATE ACCOUNT", {
            if (busy) return@PrimaryButton
            when {
                !email.contains('@') -> message = "Enter a valid email address."
                password.length < 8 -> message = "Password must be at least 8 characters."
                password != confirm -> message = "Passwords do not match."
                else -> {
                    busy = true
                    message = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { auth.signUp(email, password, displayName) }
                        busy = false
                        message = when {
                            result.success -> "Account created"
                            result.message == "user_already_exists" -> "Account already exists. Sign in instead."
                            else -> result.message.replace('_', ' ')
                        }
                        if (result.success) onRegistered()
                    }
                }
            }
        })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SecretField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.18.sp)
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextMuted, fontFamily = JetBrainsMono, fontSize = 14.sp) },
            visualTransformation = PasswordVisualTransformation(),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Blue),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun friendlyAuthError(result: AuthResult): String = when (result.message) {
    "valid_email_required" -> "Enter a valid email address."
    "password_min_8_chars" -> "Password must be at least 8 characters."
    else -> result.message.replace('_', ' ')
}
