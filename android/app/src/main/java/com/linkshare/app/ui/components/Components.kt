package com.linkshare.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.theme.*

@Composable
fun LinkoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).border(1.dp, Border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp), content = content)
}

@Composable
fun InfoRow(label: String, value: String, sub: String? = null, accent: Color = TextPrimary, large: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.18.sp)
        Spacer(modifier = Modifier.height(5.dp))
        Text(value, color = accent, fontSize = if (large) 22.sp else 14.sp, fontFamily = JetBrainsMono, fontWeight = if (large) FontWeight.Bold else FontWeight.Medium)
        if (sub != null) { Spacer(modifier = Modifier.height(3.dp)); Text(sub, color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono) }
    }
}

@Composable
fun LinkoInput(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String = "", sub: String? = null) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) Blue else Border
    val labelColor = if (focused) Blue else TextMuted
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).border(1.dp, borderColor, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, color = labelColor, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.18.sp)
        Spacer(modifier = Modifier.height(6.dp))
        androidx.compose.material3.TextField(value = value, onValueChange = { onValueChange(it); focused = true }, placeholder = { Text(placeholder, color = TextMuted, fontFamily = JetBrainsMono, fontSize = 14.sp) }, colors = androidx.compose.material3.TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Blue), textStyle = androidx.compose.ui.text.TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, color = TextPrimary), modifier = Modifier.fillMaxWidth().padding(0.dp), singleLine = true)
        if (sub != null) { Spacer(modifier = Modifier.height(4.dp)); Text(sub, color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono) }
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, color: Color = Blue, outline: Boolean = false, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "btnScale")
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(14.dp)).background(if (outline) Color.Transparent else if (isPressed) BlueD else color).border(if (outline) 1.5.dp else 0.dp, if (outline) color else Color.Transparent, RoundedCornerShape(14.dp)).clickable(interactionSource = interactionSource, indication = null, onClick = onClick).padding(vertical = 15.dp)) {
        Text(label, color = if (outline) color else Color.White, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.14.sp)
    }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp)) {
        Text(label, color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LinkoProgressBar(value: Float, max: Float, color: Color = Blue) {
    val pct = (value / max).coerceIn(0f, 1f)
    Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(6.dp)).background(Border)) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct).clip(RoundedCornerShape(6.dp)).background(color))
    }
}

@Composable fun RowDivider() { HorizontalDivider(color = Border, thickness = 1.dp) }

@Composable
fun Avatar(initials: String, color: Color, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.13f)).border(1.5.dp, color.copy(alpha = 0.35f), CircleShape)) {
        Text(initials, color = color, fontSize = (size.value * 0.3f).sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

data class Friend(val name: String, val id: String, val status: String, val color: Color)

@Composable
fun FriendRow(friend: Friend, onClick: (() -> Unit)? = null) {
    val initials = friend.name.split(" ").take(2).joinToString("") { it.firstOrNull()?.toString().orEmpty() }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 11.dp)) {
        Avatar(initials, friend.color); Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) { Text(friend.name, color = TextPrimary, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium); Text(friend.id, color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono) }
        StatusChip(friend.status, friend.color)
    }
}

@Composable
fun StatusChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.1f)).border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color)); Spacer(modifier = Modifier.width(5.dp)); Text(label, color = color, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionLabel(text: String) { Text(text, color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.18.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }

@Composable
fun SettingsRow(icon: @Composable () -> Unit, label: String, sub: String? = null, accent: Color = TextPrimary, onClick: (() -> Unit)? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isPressed) Card2 else Color.Transparent).then(if (onClick != null) Modifier.clickable(interactionSource = interactionSource, indication = null) { onClick() } else Modifier).padding(horizontal = 16.dp, vertical = 13.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Card2)) { icon() }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) { Text(label, color = accent, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium); if (sub != null) Text(sub, color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono) }
        if (onClick != null) Text("›", color = TextMuted, fontSize = 18.sp, fontFamily = JetBrainsMono)
    }
}
