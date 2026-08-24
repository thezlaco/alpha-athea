package com.athea.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * the left, one merged pill on the right, no bar behind them. The pill
 * grows new-session action only once the transcript has content.
 *
 * The overflow menu is NOT a DropdownMenu — it's a Surface drawn inside
 * this composable's Box, so it naturally overlaps the buttons and the
 * transcript content below.
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

    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = Ui.topBarHorizontalPadding, end = Ui.topBarHorizontalPadding, top = Ui.topBarTopPadding, bottom = Ui.topBarBottomPadding),
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
                shape = RoundedCornerShape(50),
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

        // Overflow menu: drawn inside this Box, so it naturally overlaps
        // the buttons and the content behind them.
        if (menuOpen) {
            // Scrim to catch outside taps and dismiss.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(onClick = { menuOpen = false }),
            )
            Surface(
                shape = Ui.menuShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = Ui.topBarTopPadding, end = Ui.topBarHorizontalPadding),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    if (hasMessages) {
                        MenuRow(
                            icon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            label = stringResource(R.string.menu_search),
                            onClick = { menuOpen = false; onSearch() },
                        )
                    }
                    MenuRow(
                        icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = stringResource(R.string.menu_rename),
                        onClick = { menuOpen = false; onRename() },
                    )
                    MenuRow(
                        icon = { Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = stringResource(
                            if (pinned) R.string.menu_unpin else R.string.menu_pin
                        ),
                        onClick = { menuOpen = false; onTogglePin() },
                    )
                    if (hasMessages) {
                        MenuRow(
                            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            label = stringResource(R.string.menu_delete),
                            tinted = true,
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
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
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
