package com.linkshare.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.LinkoInput
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PasswordResetScreen(auth: LinkoAuth, onDone: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp)); Text("New Password", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp)); Text("Choose a new password for your LINKO account.", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(24.dp)); LinkoInput("NEW PASSWORD", password, { password = it }, "New password", "8–72 characters")
        Spacer(Modifier.height(12.dp)); LinkoInput("CONFIRM PASSWORD", confirm, { confirm = it }, "Repeat password", "Must match the new password")
        message?.let { Spacer(Modifier.height(12.dp)); LinkoCard { Text(it, color = if (it == "Password updated") Green else Yellow, fontSize = 11.sp, fontFamily = JetBrainsMono) } }
        Spacer(Modifier.height(20.dp)); PrimaryButton(if (busy) "UPDATING…" else "UPDATE PASSWORD", {
            if (busy) return@PrimaryButton
            when { password.length !in 8..72 -> message = "Password must be 8–72 characters."; password != confirm -> message = "Passwords do not match."; else -> { busy = true; scope.launch { val result = withContext(Dispatchers.IO) { auth.updatePassword(password) }; busy = false; if (result.success) { message = "Password updated"; onDone() } else message = result.message.replace('_', ' ') } } }
        }); Spacer(Modifier.height(24.dp))
    }
}
