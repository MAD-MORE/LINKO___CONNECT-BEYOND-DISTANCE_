package androidx.compose.ui

import androidx.compose.ui.draw.alpha as drawAlpha

/** Compatibility bridge for the diagnostic screen's existing alpha import. */
fun Modifier.alpha(alpha: Float): Modifier = drawAlpha(alpha)
