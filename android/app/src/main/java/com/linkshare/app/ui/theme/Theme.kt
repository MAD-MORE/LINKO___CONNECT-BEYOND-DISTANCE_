package com.linkshare.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LinkoColorScheme = darkColorScheme(
    background = GradientMid,
    surface = Surface,
    primary = Blue,
    secondary = Accent,
    onPrimary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Red,
    outline = Border,
)

private val LinkoTypography = Typography(
    bodyLarge   = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodyMedium  = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    bodySmall   = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    titleLarge  = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    labelSmall  = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.18.sp),
    headlineSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.14.sp),
)

@Composable
fun LinkoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LinkoColorScheme,
        typography = LinkoTypography,
        content = content
    )
}
