package com.linkshare.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshare.app.ui.theme.*

// ── Cards ──────────────────────────────────────────────────────────────────

@Composable
fun LinkoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Card2, Card)))
            .border(1.dp, GlassStroke, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content
    )
}

/** Glassmorphic card with a coloured accent glow border — for featured/hero sections. */
@Composable
fun GlassCard(accentColor: Color = Blue, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 0.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.08f), Card.copy(alpha = 0.95f))))
            .border(1.5.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content
    )
}

// ── InfoRow ─────────────────────────────────────────────────────────────────

@Composable
fun InfoRow(label: String, value: String, sub: String? = null, accent: Color = TextPrimary, large: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(if (large) 36.dp else 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent.copy(alpha = 0.60f))
                .align(Alignment.CenterVertically)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically { it / 3 } + fadeIn(tween(200))).togetherWith(
                        slideOutVertically { -it / 3 } + fadeOut(tween(150)))
                },
                label = "infoValue"
            ) { v ->
                Text(v, color = accent, fontSize = if (large) 22.sp else 14.sp, fontFamily = JetBrainsMono,
                    fontWeight = if (large) FontWeight.Bold else FontWeight.Medium)
            }
            if (sub != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(sub, color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
            }
        }
    }
}

// ── LinkoInput ──────────────────────────────────────────────────────────────

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
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    color: Color = Blue,
    outline: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && enabled && !loading) 0.97f else 1f, label = "btnScale")
    val effectiveColor = if (!enabled) color.copy(alpha = 0.38f) else color
    val textColor = when {
        !enabled -> if (outline) color.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.5f)
        outline -> color
        else -> Color.White
    }
    val fillBrush = if (!outline && enabled) Brush.linearGradient(
        listOf(color.copy(alpha = if (isPressed) 0.75f else 1f), color.copy(alpha = if (isPressed) 0.55f else 0.80f))
    ) else null

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (!outline && enabled)
                    Modifier.shadow(elevation = 6.dp, spotColor = color.copy(alpha = 0.40f), shape = RoundedCornerShape(14.dp))
                else Modifier
            )
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (fillBrush != null) Modifier.background(fillBrush)
                else if (outline) Modifier.background(Color.Transparent)
                else Modifier.background(effectiveColor)
            )
            .border(
                if (outline) 1.5.dp else 0.dp,
                if (outline) effectiveColor else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .then(
                if (enabled && !loading) Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                else Modifier
            )
            .padding(vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), color = textColor, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(label, color = textColor, fontSize = 13.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, letterSpacing = 0.14.sp)
        }
    }
}


@Composable
fun GhostButton(label: String, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = TextSub, fontSize = 12.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Box(Modifier.width(48.dp).height(1.dp).background(TextMuted.copy(alpha = 0.50f)))
        }
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
    val isActive = color == Green
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        if (isActive) BlinkingDot(color) else Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, color = color, fontSize = 10.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BlinkingDot(color: Color = Green, size: androidx.compose.ui.unit.Dp = 5.dp) {
    val alpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Box(modifier = Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha)))
}

@Composable
fun NavBadge(
    count: Int = 0,
    text: String? = null,
    color: Color = Red,
    modifier: Modifier = Modifier
) {
    val scale by rememberInfiniteTransition(label = "badgePulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "badgeScale"
    )

    val labelText = text ?: if (count > 99) "99+" else count.toString()
    if (count > 0 || !text.isNullOrBlank()) {
        Box(
            modifier = modifier
                .scale(scale)
                .clip(RoundedCornerShape(10.dp))
                .background(color)
                .border(1.dp, Card2, RoundedCornerShape(10.dp))
                .padding(horizontal = 4.5.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = labelText,
                color = Color.White,
                fontSize = 8.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                lineHeight = 10.sp
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 10.sp,
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.18.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsRow(
    icon: @Composable () -> Unit,
    label: String,
    sub: String? = null,
    accent: Color = TextPrimary,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPressed) Card2 else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interactionSource, indication = null) { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Card2)
        ) { icon() }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = accent, fontSize = 14.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, color = TextSub, fontSize = 11.sp, fontFamily = JetBrainsMono)
        }
        if (onClick != null) Text("›", color = TextMuted, fontSize = 18.sp, fontFamily = JetBrainsMono)
    }
}
