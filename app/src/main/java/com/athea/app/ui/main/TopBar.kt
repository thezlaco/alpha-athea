package com.athea.app.ui.main

import androidx.compose.foundation.layout.Box
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
 * grows search and new-session actions only once the transcript has
 * content; everything else lives in the overflow menu.
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

    // The menu anchors to the very top-right (zero-size anchor), so it
    // drops over the buttons region like chat apps do, not under them.
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 6.dp),
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

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(1.dp),
        ) {
            AtheaDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                offset = androidx.compose.ui.unit.DpOffset(0.dp, (-48).dp),
            ) {
                        if (hasMessages) {
                            AtheaDropdownItem(
                                icon = Icons.Default.Search,
                                text = stringResource(R.string.menu_search),
                                onClick = { menuOpen = false; onSearch() },
                            )
                        }
                        AtheaDropdownItem(
                            icon = Icons.Default.Edit,
                            text = stringResource(R.string.menu_rename),
                            onClick = { menuOpen = false; onRename() },
                        )
                        AtheaDropdownItem(
                            icon = Icons.Default.PushPin,
                            text = stringResource(
                                if (pinned) R.string.menu_unpin else R.string.menu_pin
                            ),
                            onClick = { menuOpen = false; onTogglePin() },
                        )
                        if (hasMessages) {
                            AtheaDropdownItem(
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
