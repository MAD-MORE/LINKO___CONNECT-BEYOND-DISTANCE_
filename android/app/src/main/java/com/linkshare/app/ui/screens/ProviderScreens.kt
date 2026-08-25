package com.linkshare.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.LinkoProfileApi
import com.linkshare.app.network.LinkoRealtimeEvent
import com.linkshare.app.network.LinkoRealtimeManager
import com.linkshare.app.provider.LinkoProviderService
import com.linkshare.app.ui.components.LinkoCard
import com.linkshare.app.ui.theme.Border
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.Red
import com.linkshare.app.ui.theme.Surface
import com.linkshare.app.ui.theme.TextMuted
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub
import com.linkshare.app.ui.theme.JetBrainsMono
import kotlinx.coroutines.delay

private object ProviderReadyAlgorithm {
    fun requestNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && context is Activity && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                7002
            )
        }
    }
}

@Composable
private fun ShareConnectionSpiral(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "share_spiral")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "share_rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "share_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .rotate(rotation)
                .border(2.dp, if (active) Green else Border, CircleShape)
                .padding(9.dp)
                .border(2.dp, if (active) Green else Border, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(58.dp)
                .rotate(-rotation * 1.35f)
                .border(2.dp, if (active) Green else Border, CircleShape)
                .padding(11.dp)
                .border(2.dp, if (active) Green else Border, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(if (active) Green else Surface, CircleShape)
                .then(if (active) Modifier else Modifier.border(1.dp, Border, CircleShape))
                .padding(8.dp)
                .background(Surface, CircleShape)
        )
        Text(
            if (active) "LINK" else "OFF",
            color = if (active) Green else TextMuted,
            fontSize = 7.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.rotate(-rotation * 0.5f)
        )
    }
}

@Composable
fun ProviderReadyScreen(onIncomingRequest: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { LinkoAuth(context) }
    val profileApi = remember {
        LinkoProfileApi(
            accessTokenProvider = { auth.currentAccessToken() },
            userIdProvider = { auth.currentUserId() },
            refreshProvider = { auth.refreshSession().success }
        )
    }

    var username by remember {
        mutableStateOf(
            auth.currentUsername()
                ?: auth.currentDisplayName()
                ?: "LINKO User"
        )
    }
    var linkoId by remember { mutableStateOf(auth.currentLinkoId().orEmpty()) }
    var providerState by remember { mutableStateOf(if (linkoId.isNotBlank()) "READY" else "LOADING") }
    var profileLoading by remember { mutableStateOf(true) }
    var profileError by remember { mutableStateOf(false) }
    var retryProfile by remember { mutableStateOf(0) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ProviderReadyAlgorithm.requestNotificationPermission(context)
        LinkoProviderService.start(context)

        LinkoRealtimeManager.events.collect { event ->
            when (event) {
                is LinkoRealtimeEvent.FriendRequestReceived -> onIncomingRequest()
                is LinkoRealtimeEvent.SessionStateChanged -> {
                    providerState = when (event.state) {
                        "requested" -> "REQUESTED"
                        "approved" -> "APPROVED"
                        "signaling" -> "SIGNALING"
                        "connected" -> "SHARING"
                        "denied" -> "DECLINED"
                        "revoked", "expired" -> "ENDED"
                        else -> providerState
                    }
                }
                is LinkoRealtimeEvent.TransportError -> {
                    if (linkoId.isNotBlank()) providerState = "OFFLINE"
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(retryProfile) {
        profileLoading = true
        profileError = false

        runCatching { profileApi.load() }
            .onSuccess { profile ->
                username = profile.username
                    ?.takeIf { it.isNotBlank() }
                    ?: profile.displayName.takeIf { it.isNotBlank() }
                    ?: "LINKO User"
                linkoId = profile.linkoId.trim()
                providerState = if (linkoId.isNotBlank()) "READY" else "ID_ERROR"
            }
            .onFailure {
                profileError = true
                if (linkoId.isNotBlank()) {
                    providerState = "READY"
                } else {
                    providerState = "OFFLINE"
                }
            }

        profileLoading = false
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }

    val connectionBusy = providerState == "LOADING" ||
        providerState == "REQUESTED" ||
        providerState == "APPROVED" ||
        providerState == "SIGNALING"
    val shareVisualActive = linkoId.isNotBlank() && providerState != "OFFLINE" && providerState != "ID_ERROR"

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            "READY TO SHARE",
            color = TextPrimary,
            fontSize = 22.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Share your connection with a friend",
            color = TextSub,
            fontSize = 13.sp,
            fontFamily = JetBrainsMono,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))

        // Always-visible connection-sharing visual. It is not the identity loader;
        // it communicates that this screen is the Provider/share-connection surface.
        ShareConnectionSpiral(active = shareVisualActive)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("USERNAME", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                Text(
                    "@${username.removePrefix("@")}"
                    ,
                    color = Green,
                    fontSize = 18.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text("LINKO ID", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (profileLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Green,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(9.dp))
                    }
                    Text(
                        when {
                            linkoId.isNotBlank() -> linkoId
                            profileLoading -> "Loading LINKO ID…"
                            else -> "LINKO ID unavailable"
                        },
                        color = if (profileError && linkoId.isBlank()) Red else Green,
                        fontSize = 19.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                enabled = linkoId.isNotBlank() && !profileLoading,
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("LINKO ID", linkoId))
                    copied = true
                }
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy LINKO ID",
                    tint = if (linkoId.isNotBlank() && !profileLoading) Green else TextMuted
                )
            }
        }

        if (copied) {
            Text(
                "COPIED",
                color = Green,
                fontSize = 9.sp,
                fontFamily = JetBrainsMono,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        LinkoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ROLE", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                    Text("PROVIDER", color = Green, fontSize = 17.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("STATUS", color = TextSub, fontSize = 9.sp, fontFamily = JetBrainsMono)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connectionBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Green,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(
                            providerState,
                            color = when (providerState) {
                                "OFFLINE", "ID_ERROR", "DECLINED", "ENDED" -> Red
                                "LOADING", "REQUESTED", "APPROVED", "SIGNALING" -> TextSub
                                else -> Green
                            },
                            fontSize = 15.sp,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (providerState) {
                    "READY" -> "Waiting for a friend to connect..."
                    "SHARING" -> "A friend is using your authorized connection."
                    "REQUESTED" -> "A friend is requesting access to your connection."
                    "APPROVED" -> "Connection approved. Preparing the tunnel..."
                    "SIGNALING" -> "Establishing a secure connection..."
                    "OFFLINE" -> "Your LINKO profile could not be loaded."
                    "ID_ERROR" -> "Your LINKO profile has no LINKO ID."
                    else -> "Preparing your LINKO provider..."
                },
                color = TextSub,
                fontSize = 10.sp,
                fontFamily = JetBrainsMono
            )

            if (connectionBusy) {
                Spacer(Modifier.height(12.dp))
                Text(
                    when (providerState) {
                        "LOADING" -> "Preparing your sharing service…"
                        "REQUESTED" -> "Waiting for your approval…"
                        "APPROVED" -> "Preparing secure sharing…"
                        "SIGNALING" -> "Connecting the devices…"
                        else -> "Working…"
                    },
                    color = TextSub,
                    fontSize = 9.sp,
                    fontFamily = JetBrainsMono
                )
            }

            if (profileError && linkoId.isBlank()) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { retryProfile++ },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text(
                        "RETRY",
                        color = Green,
                        fontSize = 10.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Your connection is never shared without your approval.",
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = JetBrainsMono
        )
        Spacer(Modifier.height(12.dp))
    }
}
