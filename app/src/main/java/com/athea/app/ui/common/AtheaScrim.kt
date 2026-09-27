package com.athea.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * The one full-screen tap-catcher: any tap outside the overlay dismisses
 * it. No ripple — matches the drawer behaviour (no white flash).
 * Every hand-rolled "scrim Box" in the app goes through this.
 */
@Composable
fun AtheaScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    if (!visible) return
    Box(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss,
            ),
    )
}
