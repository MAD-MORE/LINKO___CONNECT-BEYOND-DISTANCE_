package com.linkshare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LinkoInk = Color(0xFF101417)
val LinkoParchment = Color(0xFFF7F2E8)
val LinkoSeaGlass = Color(0xFF66BFB5)
val LinkoCopper = Color(0xFFE2A15F)
val LinkoPlum = Color(0xFF55415F)
val LinkoAlert = Color(0xFFB85042)

@Composable
fun LinkoScreenHeader(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("LINKO", color = LinkoSeaGlass, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(title, color = LinkoInk, fontSize = 31.sp, fontWeight = FontWeight.Black)
        Text(detail, color = LinkoInk.copy(alpha = .68f), fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
fun LinkoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = .78f), RoundedCornerShape(26.dp))
            .border(1.dp, LinkoInk.copy(alpha = .10f), RoundedCornerShape(26.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

@Composable
fun LinkoPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LinkoSeaGlass, contentColor = LinkoInk)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun LinkoSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LinkoCopper, contentColor = LinkoInk)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun LinkoStatusDot(active: Boolean, modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .size(11.dp)
            .background(if (active) LinkoSeaGlass else LinkoCopper, CircleShape)
    )
}

@Composable
fun LinkoStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = LinkoInk.copy(alpha = .62f), fontSize = 13.sp)
        Text(value, color = LinkoInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
