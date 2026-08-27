package com.linkshare.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoNetworkException
import com.linkshare.app.network.LinkoProfileApi
import com.linkshare.app.ui.components.GhostButton
import com.linkshare.app.ui.components.InfoRow
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.components.LinkoInput
import com.linkshare.app.ui.components.PrimaryButton
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RealAccountProfileScreen(onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { LinkoAuth(context) }
    val api = remember { LinkoProfileApi(auth::currentAccessToken, auth::currentUserId) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(auth.currentDisplayName().orEmpty()) }
    var username by remember { mutableStateOf(auth.currentUsername().orEmpty()) }
    var linkoId by remember { mutableStateOf(auth.currentLinkoId().orEmpty()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun loadCanonicalProfile() {
        val profile = withContext(Dispatchers.IO) { api.load() }
        name = profile.displayName
        username = profile.username.orEmpty()
        linkoId = profile.linkoId
        auth.saveProfile(profile.displayName, profile.linkoId, profile.username)
    }

    LaunchedEffect(Unit) {
        runCatching { loadCanonicalProfile() }
            .onFailure { message = "Could not load your account profile." }
        loading = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Your Profile", color = TextPrimary, fontSize = 22.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("This identity belongs to the signed-in account", color = TextSub, fontSize = 13.sp, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(20.dp))

        LinkoCard {
            Text("ACCOUNT IDENTITY", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            InfoRow("DISPLAY NAME", if (name.isBlank()) "Loading…" else name, "Canonical account name", accent = Blue)
            Spacer(Modifier.height(10.dp))
            InfoRow("USERNAME", if (username.isBlank()) "Loading…" else "@${username.removePrefix("@")}", "Used by Share Connection", accent = Green)
            Spacer(Modifier.height(10.dp))
            InfoRow("LINKO ID", if (linkoId.isBlank()) "Loading…" else "@$linkoId", "Stable account identity", accent = Green)
        }

        Spacer(Modifier.height(12.dp))
        LinkoCard {
            Text("EDIT DISPLAY NAME", color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LinkoInput("DISPLAY NAME", name, { name = it }, "Your display name", "2–40 characters")
        }

        Spacer(Modifier.height(12.dp))
        message?.let {
            Text(
                it,
                color = when {
                    it.startsWith("✓") -> Green
                    it.startsWith("⚠") || it.startsWith("✕") -> Red
                    else -> TextSub
                },
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            if (saving) "SAVING…" else "SAVE PROFILE",
            {
                if (!saving && !loading) {
                    saving = true
                    message = null
                    scope.launch {
                        val requestedName = name.trim()
                        runCatching {
                            require(requestedName.length in 2..40) { "Display name must be 2–40 characters." }
                            withContext(Dispatchers.IO) { api.updateDisplayName(requestedName) }
                            val confirmed = withContext(Dispatchers.IO) { api.load() }
                            check(confirmed.displayName == requestedName) { "profile_save_not_verified" }
                            name = confirmed.displayName
                            username = confirmed.username.orEmpty()
                            linkoId = confirmed.linkoId
                            auth.saveProfile(confirmed.displayName, confirmed.linkoId, confirmed.username)
                        }.onSuccess {
                            message = "✓ PROFILE SAVED"
                        }.onFailure { error ->
                            message = when (error) {
                                is LinkoNetworkException -> when (error.message) {
                                    "profile_save_not_verified" -> "⚠ PROFILE SAVE NOT VERIFIED"
                                    "auth_required", "auth_user_required" -> "✕ SESSION EXPIRED. SIGN IN AGAIN."
                                    else -> "✕ PROFILE NOT SAVED"
                                }
                                else -> if (error.message?.contains("2–40") == true) "✕ ${error.message}" else "✕ PROFILE NOT SAVED"
                            }
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
