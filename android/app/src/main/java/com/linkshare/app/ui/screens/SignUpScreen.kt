package com.linkshare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.auth.AuthResult
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoFriendsApiHolder
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.LinkoInput
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LinkoSignUpScreen(auth: LinkoAuth, onRegistered: () -> Unit) {
    var email by remember { mutableStateOf("") }; var displayName by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; var message by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }; var cooldownUntil by remember { mutableLongStateOf(0L) }; val scope = rememberCoroutineScope(); val now = System.currentTimeMillis(); val cooldownActive = cooldownUntil > now
    LaunchedEffect(cooldownUntil) { if (cooldownUntil > System.currentTimeMillis()) { kotlinx.coroutines.delay((cooldownUntil - System.currentTimeMillis()).coerceAtLeast(1L)); cooldownUntil = 0L } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Create Account", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Create your Linko identity", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono); Spacer(Modifier.height(24.dp)); LinkoInput("EMAIL", email, { email = it }, "you@example.com", "Used for your LINKO account"); Spacer(Modifier.height(12.dp)); LinkoInput("DISPLAY NAME", displayName, { displayName = it }, "Your display name", "2–40 characters"); Spacer(Modifier.height(12.dp)); SecretField("PASSWORD", password, { password = it }, "8–72 chars, upper/lowercase + number"); Spacer(Modifier.height(12.dp)); SecretField("CONFIRM PASSWORD", confirm, { confirm = it }, "Repeat your password")
        message?.let { Spacer(Modifier.height(10.dp)); LinkoCard { Text(it, color = Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono) } }; Spacer(Modifier.height(12.dp))
        PrimaryButton(when { busy -> "CREATING…"; cooldownActive -> "WAIT…"; else -> "CREATE ACCOUNT" }, {
            if (busy || cooldownActive) return@PrimaryButton
            val normalizedEmail = email.trim().lowercase()
            when {
                !EMAIL_REGEX.matches(normalizedEmail) -> message = "Enter a valid email address."
                displayName.trim().length !in 2..40 -> message = "Display name must be 2–40 characters."
                password.length < 8 -> message = "Password must be at least 8 characters."
                password.length > 72 -> message = "Password must be 72 characters or fewer."
                !password.any(Char::isUpperCase) -> message = "Password needs at least one uppercase letter."
                !password.any(Char::isLowerCase) -> message = "Password needs at least one lowercase letter."
                !password.any(Char::isDigit) -> message = "Password needs at least one number."
                password != confirm -> message = "Passwords do not match."
                else -> { busy = true; message = null; cooldownUntil = System.currentTimeMillis() + SIGNUP_COOLDOWN_MS; scope.launch {
                    val result = withContext(Dispatchers.IO) { auth.signUp(normalizedEmail, password, displayName.trim()) }
                    if (result.success && !auth.currentAccessToken().isNullOrBlank()) {
                        val profileResult = withContext(Dispatchers.IO) { runCatching { LinkoFriendsApiHolder.api.ensureProfile(displayName.trim()) } }
                        if (profileResult.isSuccess) {
                            val profile = profileResult.getOrThrow(); auth.saveProfile(profile.optString("display_name").takeIf { it.isNotBlank() } ?: displayName.trim(), profile.optString("linko_id").takeIf { it.isNotBlank() })
                            busy = false; onRegistered()
                        } else { busy = false; message = "Profile could not be saved. Check your connection and try again." }
                    } else if (result.success) { busy = false; message = "Account created. Please sign in to continue." } else { busy = false; message = friendlyAuthError(result) }
                } }
            }
        }); Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun SecretField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) { var visible by remember { mutableStateOf(false) }; Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) { Text(label, color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.18.sp); TextField(value = value, onValueChange = onValueChange, placeholder = { Text(placeholder, color = TextMuted, fontFamily = JetBrainsMono, fontSize = 14.sp) }, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Hide $label" else "Show $label", tint = TextMuted) } }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Blue), singleLine = true, modifier = Modifier.fillMaxWidth()) } }
private fun friendlyAuthError(result: AuthResult): String = when { result.success -> ""; result.message.contains("already", true) -> "An account with this email already exists."; result.message == "too_many_requests" -> "Linko is protecting account creation from repeated requests. Please wait before trying again."; else -> result.message.replace('_', ' ') }
private const val SIGNUP_COOLDOWN_MS = 60_000L
private val EMAIL_REGEX = Regex("^[A-Za-z0-9.!#${'$'}%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+${'$'}")
