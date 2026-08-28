package com.athea.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.athea.app.R
import com.athea.app.ui.theme.Ui

/**
 * Floating top controls, chat-app style: one standalone round button on
 * the left, one merged pill on the right. The overflow menu is a full-
 * screen overlay that covers the buttons and the content behind them.
 */
@Composable
fun TopBar(
    hasMessages: Boolean,
    pinned: Boolean,
    onOpenDrawer: () -> Unit,
    onNewSession: () -> Unit,
    onSearch: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        // Scrim: catches all outside taps to dismiss the menu.
        // No ripple — matches drawer behaviour (drawer has no white flash).
        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { menuOpen = false },
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = Ui.topBarHorizontalPadding,
                    end = Ui.topBarHorizontalPadding,
                    top = Ui.topBarTopPadding,
                    bottom = Ui.topBarBottomPadding,
                ),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onOpenDrawer,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(Modifier.size(Ui.topButtonSize), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = stringResource(R.string.cd_open_drawer),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Surface(
                shape = Ui.pillShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasMessages) {
                        IconButton(onClick = onNewSession) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.cd_new_session),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_more),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Menu panel: inline Surface (not Popup) so it is part of composition
        // and exactly covers the pill — DropdownMenu Popup has its own window
        // and 8dp offset, which left pill peeking through rounded corners.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 4.dp, end = Ui.topBarHorizontalPadding),
        ) {
            if (menuOpen) {
                Surface(
                    shape = Ui.menuShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    modifier = Modifier.widthIn(min = Ui.menuMinWidth),
                ) {
                    Column {
                        if (hasMessages) {
                            InlineMenuItem(
                                icon = Icons.Default.Search,
                                text = stringResource(R.string.menu_search),
                                onClick = { menuOpen = false; onSearch() },
                            )
                        }
                        InlineMenuItem(
                            icon = Icons.Default.Edit,
                            text = stringResource(R.string.menu_rename),
                            onClick = { menuOpen = false; onRename() },
                        )
                        InlineMenuItem(
                            icon = Icons.Default.PushPin,
                            text = stringResource(
                                if (pinned) R.string.menu_unpin else R.string.menu_pin
                            ),
                            onClick = { menuOpen = false; onTogglePin() },
                        )
                        if (hasMessages) {
                            InlineMenuItem(
                                icon = Icons.Default.Delete,
                                text = stringResource(R.string.menu_delete),
                                tinted = true,
                                onClick = { menuOpen = false; onDelete() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tinted: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (tinted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .heightIn(min = Ui.menuItemMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.heightIn(min = Ui.menuIconSize))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (tinted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun MenuRow(
    icon: @Composable () -> Unit,
    label: String,
    tinted: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .heightIn(min = Ui.menuItemMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (tinted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
