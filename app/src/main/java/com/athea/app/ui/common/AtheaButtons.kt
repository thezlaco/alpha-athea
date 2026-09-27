package com.athea.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.athea.app.ui.theme.Ui

/**
 * The one floating round button (TopBar drawer toggle, scaffold back, ...):
 * a circle of [Ui.topButtonSize] in the high surface color.
 */
@Composable
fun AtheaRoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Box(Modifier.size(Ui.topButtonSize), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/**
 * The one merged pill hosting a row of icon buttons (TopBar right,
 * drawer bottom). Callers only place [AtheaPillIcon]s inside.
 */
@Composable
fun AtheaPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = Ui.pillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** The one icon button for pills. */
@Composable
fun AtheaPillIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
