package com.athea.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.athea.app.R
import com.athea.app.ui.SearchState
import com.athea.app.ui.theme.messageStyle

private val PanelShape = RoundedCornerShape(28.dp)
private val BUTTON_ROW_HEIGHT = 44.dp

@Composable
fun InputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onExpandEditor: () -> Unit,
    search: SearchState?,
    onSearchQueryChange: (String) -> Unit,
    onSearchNext: () -> Unit,
    onExitSearch: () -> Unit,
    enterSends: Boolean,
    modifier: Modifier = Modifier,
) {
    if (search == null) {
        DraftPanel(
            draft = draft,
            onDraftChange = onDraftChange,
            onSend = onSend,
            onExpandEditor = onExpandEditor,
            enterSends = enterSends,
            modifier = modifier,
        )
    } else {
        SearchPanel(
            query = search.query,
            matchIndex = search.index,
            matchCount = search.matchBlockIds.size,
            onQueryChange = onSearchQueryChange,
            onNext = onSearchNext,
            onExit = onExitSearch,
            modifier = modifier,
        )
    }
}

/** Union of navigation-bar and IME insets: correct spacing with the keyboard open and closed. */
@Composable
private fun Modifier.barPadding(): Modifier =
    this
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        .padding(horizontal = 10.dp, vertical = 8.dp)

/**
 * One morphing panel, chat-app style:
 *  - short text: slim single row, buttons vertically centered beside it;
 *  - growing text: the field rises to full width, buttons stay on their
 *    own line at the tail, so no dead space appears beside the text.
 * The field is a single instance, so focus survives the morph.
 */
@Composable
private fun DraftPanel(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onExpandEditor: () -> Unit,
    enterSends: Boolean,
    modifier: Modifier = Modifier,
) {
    var lineCount by remember { mutableStateOf(1) }
    val compact = lineCount <= 1

    Surface(
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .barPadding(),
    ) {
        Box(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        end = if (compact) 96.dp else 8.dp,
                        bottom = if (compact) 0.dp else BUTTON_ROW_HEIGHT,
                    ),
                textStyle = messageStyle().copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 6,
                onTextLayout = { lineCount = it.lineCount },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii,
                    // None keeps the IME newline key: multi-line drafts stay possible.
                    imeAction = if (enterSends) ImeAction.Send else ImeAction.None,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSend() },
                ),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) {
                            Text(
                                stringResource(R.string.input_hint),
                                style = messageStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            Row(
                Modifier
                    .align(if (compact) Alignment.CenterEnd else Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = if (compact) 0.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExpandEditor, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.OpenInFull,
                        contentDescription = stringResource(R.string.cd_expand_editor),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledIconButton(
                    onClick = onSend,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = stringResource(R.string.cd_send),
                        modifier = Modifier.rotate(-90f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    matchIndex: Int,
    matchCount: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .barPadding(),
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        stringResource(R.string.search_in_session_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                textStyle = messageStyle().copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                shape = PanelShape,
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
            )
            Text(
                text = "${matchIndex + 1}/$matchCount",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onExit) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
