package com.athea.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.athea.app.R
import com.athea.app.ui.common.AtheaMenuHost
import com.athea.app.ui.common.AtheaPill
import com.athea.app.ui.common.AtheaPillIcon
import com.athea.app.ui.common.AtheaRoundButton
import com.athea.app.ui.common.AtheaScrim
import com.athea.app.ui.common.rememberAtheaMenu
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
    val menu = rememberAtheaMenu()

    Box(modifier.fillMaxSize()) {
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
            AtheaRoundButton(onClick = onOpenDrawer) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = stringResource(R.string.cd_open_drawer),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            AtheaPill {
                if (hasMessages) {
                    AtheaPillIcon(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_new_session),
                        onClick = onNewSession,
                    )
                }
                AtheaPillIcon(
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.cd_more),
                    onClick = menu::toggle,
                )
            }
        }

        // Scrim sits above the buttons: with the menu open, every tap
        // outside the menu panel — including the pill itself — closes it.
        AtheaScrim(menu.expanded, menu::close)

        // Menu panel: inline (not Popup) so it exactly covers the pill — Popup has own window + 8dp offset.
        // Above the scrim, so its items stay tappable while the scrim is live.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = Ui.menuTopOffset, end = Ui.topBarHorizontalPadding),
        ) {
            AtheaMenuHost(menu, isPopup = false) {
                if (hasMessages) {
                    item(
                        icon = Icons.Default.Search,
                        text = stringResource(R.string.menu_search),
                    ) { onSearch() }
                }
                item(
                    icon = Icons.Default.Edit,
                    text = stringResource(R.string.menu_rename),
                ) { onRename() }
                item(
                    icon = Icons.Default.PushPin,
                    text = stringResource(
                        if (pinned) R.string.menu_unpin else R.string.menu_pin
                    ),
                ) { onTogglePin() }
                if (hasMessages) {
                    item(
                        icon = Icons.Default.Delete,
                        text = stringResource(R.string.menu_delete),
                        tinted = true,
                    ) { onDelete() }
                }
            }
        }
    }
}
