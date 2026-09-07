package com.linkshare.app.ui.screens

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.linkshare.app.ui.components.PrimaryButton as BasePrimaryButton
import com.linkshare.app.ui.theme.Blue
import kotlinx.coroutines.delay

/**
 * Engine-screen PrimaryButton wrapper.
 * Every tap becomes a one-shot action: it shows loading immediately, blocks duplicate taps,
 * and automatically releases the button after the action completes, the screen is stopped,
 * or the safety timeout expires.
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

    LaunchedEffect(actionLoading, lifecycleOwner) {
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
                try {
                    onClick()
                } catch (t: Throwable) {
                    actionLoading = false
                    throw t
                }
            }
        },
        color = color,
        outline = outline,
        enabled = enabled && !actionLoading,
        loading = effectiveLoading,
        modifier = modifier
    )
}
