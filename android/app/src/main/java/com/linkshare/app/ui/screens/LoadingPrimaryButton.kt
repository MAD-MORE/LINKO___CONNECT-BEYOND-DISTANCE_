package com.linkshare.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.linkshare.app.ui.components.PrimaryButton as BasePrimaryButton
import com.linkshare.app.ui.theme.Blue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/**
 * Engine-screen PrimaryButton wrapper.
 * Shows loading immediately, prevents duplicate taps, and always releases the button
 * after navigation, lifecycle stop, failure, or the 20-second safety timeout.
 */
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
    var actionLoading by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(actionLoading) {
        if (actionLoading) {
            delay(20_000L)
            actionLoading = false
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
                actionLoading = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        try {
            awaitCancellation()
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val effectiveLoading = loading || actionLoading
    BasePrimaryButton(
        label = label,
        onClick = {
            if (!effectiveLoading && enabled) {
                actionLoading = true
                runCatching { onClick() }
                    .onFailure { actionLoading = false }
            }
        },
        color = color,
        outline = outline,
        enabled = enabled && !actionLoading,
        loading = effectiveLoading,
        modifier = modifier
    )
}
