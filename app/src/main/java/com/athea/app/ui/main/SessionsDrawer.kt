package com.athea.app.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.athea.app.R
import com.athea.app.ui.SessionUi
import com.athea.app.ui.theme.Ui

/**
 * Persistent navigation drawer: sessions on top (pinned first),
 * favorites and settings pinned to the bottom.
 */
@Composable
fun SessionsDrawerContent(
    sessions: List<SessionUi>,
    currentSessionId: Long?,
    onSelectSession: (Long) -> Unit,
    onRenameSession: (Long) -> Unit,
    onTogglePin: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onOpenFavorites: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Stable sort: pinned rise to the top, original order preserved otherwise.
    val ordered = remember(sessions) {
        sessions.sortedByDescending { it.pinned }
    }

    // Surface (not a bare background) so inner text picks up the proper
    // content color instead of the default dark one.
    Surface(
        color = androidx.compose.ui.graphics.Color.Black,
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(Ui.drawerWidthFraction),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Start + WindowInsetsSides.Bottom
                    )
                ),
        ) {
            Text(
                text = stringResource(R.string.drawer_sessions),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )

            // Sessions area: scrollable list fills the space.
            // Two tiers at the bottom: (1) full-width translucent panel
            // to the screen edge, (2) compact pill on top — same pill as
            // the top-right in MainScreen.
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 104.dp),
                ) {
                    ordered.forEach { session ->
                        SessionRow(
                            session = session,
                            selected = session.id == currentSessionId,
                            onSelect = { onSelectSession(session.id) },
                            onRename = { onRenameSession(session.id) },
                            onTogglePin = { onTogglePin(session.id) },
                            onDelete = { onDeleteSession(session.id) },
                        )
                    }
                }

                // Tier 1: translucent full-width panel behind the pill,
                // starts a bit higher above the pill and gets darker at the bottom.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(116.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.30f to Color.Black.copy(alpha = 0.28f),
                                0.62f to Color.Black.copy(alpha = 0.62f),
                                1f to Color.Black.copy(alpha = 0.92f),
                            )
                        ),
                )

                // Tier 2: compact pill — identical to TopBar right pill,
                // now left-aligned as requested.
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = onOpenFavorites) {
                            Icon(
                                Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.favorites),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionUi,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            // Selection spans the full row edge to edge, no rounding -
            // calmer, like chat apps do it.
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .combinedClickable(onClick = onSelect, onLongClick = { menuOpen = true }),
    ) {
        Row(
            Modifier
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProcessDot(running = session.running)
            Text(
                text = session.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
            if (session.pinned) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
            AtheaDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AtheaDropdownItem(
                    icon = Icons.Default.Edit,
                    text = stringResource(R.string.menu_rename),
                    onClick = { menuOpen = false; onRename() },
                )
                AtheaDropdownItem(
                    icon = Icons.Default.PushPin,
                    text = stringResource(
                        if (session.pinned) R.string.menu_unpin else R.string.menu_pin
                    ),
                    onClick = { menuOpen = false; onTogglePin() },
                )
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

@Composable
private fun ProcessDot(running: Boolean) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (running) 0.2f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Box(
        Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.primary.copy(
                    alpha = if (running) alpha else 0.85f
                )
            ),
    )
}
