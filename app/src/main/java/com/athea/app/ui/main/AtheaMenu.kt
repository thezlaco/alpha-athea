package com.athea.app.ui.main

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.athea.app.ui.theme.Ui

/**
 * The one overflow menu style used everywhere (top bar, message
 * long-press, session long-press): large, rounded, iconed - like chat
 * apps render theirs.
 */
@Composable
fun AtheaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = Ui.menuShape,
        modifier = Modifier.widthIn(min = Ui.menuMinWidth),
        content = content,
    )
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
