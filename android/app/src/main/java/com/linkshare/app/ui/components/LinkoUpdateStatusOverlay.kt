package com.linkshare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.BuildConfig
import com.linkshare.app.ui.theme.Blue
import com.linkshare.app.ui.theme.Green
import com.linkshare.app.ui.theme.JetBrainsMono
import com.linkshare.app.ui.theme.TextPrimary
import com.linkshare.app.ui.theme.TextSub

@Composable
fun LinkoUpdateStatusOverlay(onCheckForUpdates: () -> Unit) {
    LinkoCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.width(16.dp), color = Blue, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("LINKO UPDATE SERVICE", color = Green, fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                Text("Automatic latest-build detection is active", color = TextPrimary, fontSize = 11.sp, fontFamily = JetBrainsMono)
                Text("Current: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = TextSub, fontSize = 10.sp, fontFamily = JetBrainsMono)
            }
        }
        Spacer(Modifier.padding(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PrimaryButton("CHECK FOR UPDATES", onCheckForUpdates, color = Blue, modifier = Modifier.fillMaxWidth())
        }
    }
}
