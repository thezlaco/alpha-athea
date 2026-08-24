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
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(min = 240.dp),
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
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.heightIn(min = 24.dp))
        },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (tinted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        modifier = Modifier.heightIn(min = 52.dp),
        onClick = onClick,
    )
}
