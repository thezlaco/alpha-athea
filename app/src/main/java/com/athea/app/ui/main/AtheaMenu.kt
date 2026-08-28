package com.athea.app.ui.main

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.athea.app.ui.theme.Ui

/**
 * The one overflow menu style used everywhere (top bar, message
 * long-press, session long-press): large, rounded, iconed - like chat
 * apps render theirs.
 * @param isPopup true=Popup DropdownMenu (message/session), false=inline Surface (TopBar) to avoid Popup window offset/rounded peek.
 */
@Composable
fun AtheaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    isPopup: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isPopup) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = offset,
            shape = Ui.menuShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(min = Ui.menuMinWidth),
            content = content,
        )
    } else {
        if (expanded) {
            androidx.compose.material3.Surface(
                shape = Ui.menuShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = Ui.menuElevation,
                modifier = Modifier.widthIn(min = Ui.menuMinWidth),
            ) {
                androidx.compose.foundation.layout.Column(content = content)
            }
        }
    }
}

@Composable
fun AtheaDropdownItem(
    icon: ImageVector,
    text: String,
    tinted: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (tinted) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    DropdownMenuItem(
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.heightIn(min = Ui.menuIconSize))
        },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (tinted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        modifier = Modifier.heightIn(min = Ui.menuItemMinHeight),
        onClick = onClick,
    )
}
