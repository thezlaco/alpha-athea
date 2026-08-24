package com.athea.app.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.athea.app.R
import com.athea.app.data.CustomKey
import com.athea.app.data.KeyKind

private val BUILDER_ROWS: List<List<Pair<String, String>>> = listOf(
    listOf("ESC" to "\u001B", "TAB" to "\t", "-" to "-", "=" to "=", "[" to "[", "]" to "]", "\\" to "\\", ";" to ";", "'" to "'"),
    listOf("q" to "q", "w" to "w", "e" to "e", "r" to "r", "t" to "t", "y" to "y", "u" to "u", "i" to "i", "o" to "o", "p" to "p"),
    listOf("a" to "a", "s" to "s", "d" to "d", "f" to "f", "g" to "g", "h" to "h", "j" to "j", "k" to "k", "l" to "l"),
    listOf("z" to "z", "x" to "x", "c" to "c", "v" to "v", "b" to "b", "n" to "n", "m" to "m", "," to ",", "." to "."),
    listOf("1" to "1", "2" to "2", "3" to "3", "4" to "4", "5" to "5", "6" to "6", "7" to "7", "8" to "8", "9" to "9", "0" to "0"),
    listOf("↑" to "\u001B[A", "↓" to "\u001B[B", "←" to "\u001B[D", "→" to "\u001B[C", "/" to "/", "|" to "|", "~" to "~", "$" to "$", "_" to "_", "SP" to " "),
)

/**
 * Visual key builder: tap keys to append to the sequence, hold CTRL to
 * arm a control combination (the next letter becomes a control char).
 * Save turns the sequence into a key-row button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyBuilderScreen(
    onAdd: (CustomKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var buffer by remember { mutableStateOf("") }
    var ctrlArmed by remember { mutableStateOf(false) }

    fun append(raw: String) {
        buffer += if (ctrlArmed && raw.length == 1 && raw[0] in 'a'..'z') {
            ctrlArmed = false
            (raw[0].code and 0x1F).toChar().toString()
        } else {
            raw
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
                Text(
                    text = stringResource(R.string.builder_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider()

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.builder_buffer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp),
                ) {
                    Text(
                        text = buffer.ifEmpty { "…" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = ctrlArmed,
                        onClick = { ctrlArmed = !ctrlArmed },
                        label = { Text(stringResource(R.string.builder_ctrl)) },
                    )
                    OutlinedButton(onClick = { buffer = buffer.dropLast(1) }) {
                        Text(stringResource(R.string.builder_backspace))
                    }
                    OutlinedButton(onClick = { buffer = "" }) {
                        Text(stringResource(R.string.builder_clear))
                    }
                }

                Spacer(Modifier.height(12.dp))

                BUILDER_ROWS.forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { (label, payload) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClick = { append(payload) },
                                        onLongClick = { append(payload) },
                                    ),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall
                                        .copy(fontFamily = FontFamily.Monospace),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (buffer.isNotEmpty()) {
                        onAdd(
                            CustomKey(
                                label = buffer
                                    .replace("\u001B", "␛")
                                    .replace("\t", "⇥")
                                    .take(12),
                                payload = buffer,
                                kind = KeyKind.SEND,
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.builder_save))
            }
        }
    }
}
