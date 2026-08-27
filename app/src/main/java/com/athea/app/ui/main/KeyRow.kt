package com.athea.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athea.app.ui.theme.Ui
import com.athea.app.util.TabCompletionHandler
import com.athea.app.util.isCtrlCombinable
import com.athea.app.util.toControlChar

enum class KeyActionKind {
    /** Plain text appended to the editor draft. */
    INSERT_INTO_DRAFT,

    /** Bytes sent straight to the terminal, bypassing the editor entirely. */
    SEND_TO_TERMINAL,

    /** Arms the sticky modifier for the next combinable key. */
    TOGGLE_STICKY_CTRL,
}

data class TerminalKey(
    val label: String,
    val kind: KeyActionKind,
    val payload: String = "",
)

object DefaultKeys {
    val CTRL = TerminalKey("CTRL", KeyActionKind.TOGGLE_STICKY_CTRL)
    val ESC = TerminalKey("ESC", KeyActionKind.SEND_TO_TERMINAL, "\u001B")
    val TAB = TerminalKey("TAB", KeyActionKind.SEND_TO_TERMINAL, "\t")
    val UP = TerminalKey("↑", KeyActionKind.SEND_TO_TERMINAL, "\u001B[A")
    val DOWN = TerminalKey("↓", KeyActionKind.SEND_TO_TERMINAL, "\u001B[B")
    val LEFT = TerminalKey("←", KeyActionKind.SEND_TO_TERMINAL, "\u001B[D")
    val RIGHT = TerminalKey("→", KeyActionKind.SEND_TO_TERMINAL, "\u001B[C")
    val SLASH = TerminalKey("/", KeyActionKind.INSERT_INTO_DRAFT, "/")
    val PIPE = TerminalKey("|", KeyActionKind.INSERT_INTO_DRAFT, "|")
    val DASH = TerminalKey("-", KeyActionKind.INSERT_INTO_DRAFT, "-")
    val KILL = TerminalKey("^C", KeyActionKind.SEND_TO_TERMINAL, "\u0003")

    val row = listOf(CTRL, ESC, TAB, UP, DOWN, LEFT, RIGHT, SLASH, PIPE, DASH, KILL)
}

@Composable
fun KeyRow(
    keys: List<TerminalKey>,
    stickyCtrl: Boolean,
    suggestionActive: Boolean,
    onInsert: (String) -> Unit,
    onSendBytes: (String) -> Unit,
    onAcceptSuggestion: () -> Unit,
    onToggleStickyCtrl: () -> Unit,
    onConsumeStickyCtrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fixed bar with thin separators — not floating chips. Tighter to the
    // composer, larger tap targets, calmer than spaced chips.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            keys.forEachIndexed { index, key ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                            ),
                    )
                }
                val selected = key.kind == KeyActionKind.TOGGLE_STICKY_CTRL && stickyCtrl
                KeyCell(key = key, selected = selected) {
                    when (key.kind) {
                        KeyActionKind.INSERT_INTO_DRAFT -> onInsert(key.payload)

                        KeyActionKind.SEND_TO_TERMINAL -> {
                            if (key == DefaultKeys.TAB && suggestionActive) {
                                onAcceptSuggestion()
                                return@KeyCell
                            }
                            val ch = key.payload.singleOrNull()
                            val combinable = ch?.isCtrlCombinable() == true
                            if (stickyCtrl && combinable && ch != null) {
                                val control = ch.toControlChar()
                                onSendBytes(control.toString())
                                onConsumeStickyCtrl()
                            } else {
                                onSendBytes(key.payload)
                            }
                        }

                        KeyActionKind.TOGGLE_STICKY_CTRL -> onToggleStickyCtrl()
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCell(
    key: TerminalKey,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .defaultMinSize(minWidth = Ui.keyMinWidth, minHeight = Ui.keyMinHeight)
            .clip(Ui.smallShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Ui.keyPaddingH),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
