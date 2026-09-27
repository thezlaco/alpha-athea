package com.athea.app.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import com.athea.app.ui.theme.Ui

/**
 * The one menu connector for the whole app: owns the open/close state and
 * the "close, then dispatch" ordering. Callers never write
 * `menuOpen = false; onX()` by hand — that one-shape pattern lives here.
 */
class AtheaMenu {
    var expanded by mutableStateOf(false)
        private set

    fun open() {
        expanded = true
    }

    fun close() {
        expanded = false
    }

    fun toggle() {
        expanded = !expanded
    }

    /** Close the menu, then run the action — the single place this order lives. */
    fun dispatch(action: () -> Unit) {
        close()
        action()
    }
}

@Composable
fun rememberAtheaMenu(): AtheaMenu = remember { AtheaMenu() }

/**
 * Receiver for menu content: every [item] closes the menu on its own,
 * the caller only describes the action.
 */
class AtheaMenuScope internal constructor(private val menu: AtheaMenu) {

    @Composable
    fun item(
        icon: ImageVector,
        text: String,
        tinted: Boolean = false,
        action: () -> Unit,
    ) {
        AtheaDropdownItem(
            icon = icon,
            text = text,
            tinted = tinted,
            onClick = { menu.dispatch(action) },
        )
    }
}

/**
 * Renders [AtheaMenu]'s content: popup DropdownMenu (message/session menus)
 * or inline Surface (TopBar) to avoid the Popup window offset.
 */
@Composable
fun AtheaMenuHost(
    menu: AtheaMenu,
    isPopup: Boolean = true,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable AtheaMenuScope.() -> Unit,
) {
    AtheaDropdownMenu(
        expanded = menu.expanded,
        onDismissRequest = menu::close,
        offset = offset,
        isPopup = isPopup,
    ) {
        with(AtheaMenuScope(menu)) { content() }
    }
}

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
