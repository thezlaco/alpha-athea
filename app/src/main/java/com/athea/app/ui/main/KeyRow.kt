package com.athea.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
    stickyCtrl: Boolean,
    onInsert: (String) -> Unit,
    onSendBytes: (String) -> Unit,
    onToggleStickyCtrl: () -> Unit,
    onConsumeStickyCtrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // No solid strip behind the row: chips float on the canvas, with a
    // soft background darkening that starts at half the row height.
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.5f to androidx.compose.ui.graphics.Color.Transparent,
                        1f to androidx.compose.ui.graphics.Color.Black,
                    )
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DefaultKeys.row.forEach { key ->
                val selected = key.kind == KeyActionKind.TOGGLE_STICKY_CTRL && stickyCtrl
                KeyChip(key = key, selected = selected) {
                    when (key.kind) {
                        KeyActionKind.INSERT_INTO_DRAFT -> onInsert(key.payload)

                        KeyActionKind.SEND_TO_TERMINAL -> {
                            val ch = key.payload.singleOrNull()
                            val combinable = ch != null &&
                                (ch in 'a'..'z' || ch in 'A'..'Z')
                            if (stickyCtrl && combinable && ch != null) {
                                val control = (ch.uppercaseChar().code and 0x1F).toChar()
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
private fun KeyChip(
    key: TerminalKey,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        // Fixed floor width keeps the row rhythm even: short labels do
        // not shrink below it, long ones still expand naturally.
        modifier = Modifier.defaultMinSize(minWidth = 52.dp),
    ) {
        Box(
            Modifier
                .defaultMinSize(minWidth = 52.dp, minHeight = 40.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = key.label,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                textAlign = TextAlign.Center,
            )
        }
    }
}
