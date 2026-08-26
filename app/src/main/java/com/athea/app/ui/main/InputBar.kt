package com.athea.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.athea.app.R
import com.athea.app.ui.SearchState
import com.athea.app.ui.theme.Ui
import com.athea.app.ui.theme.messageStyle




/** Space the buttons occupy at the tail of the field, shared by every
 *  layout that reserves room for them. */


@Composable
fun InputBar(
    draft: String,
    suggestion: String?,
    attachments: List<com.athea.app.core.model.Attachment>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onExpandEditor: () -> Unit,
    onAddClick: () -> Unit,
    onRemoveAttachment: (com.athea.app.core.model.Attachment) -> Unit,
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
            suggestion = suggestion,
            attachments = attachments,
            onDraftChange = onDraftChange,
            onSend = onSend,
            onExpandEditor = onExpandEditor,
            onAddClick = onAddClick,
            onRemoveAttachment = onRemoveAttachment,
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

/** Union of navigation-bar and IME insets: tighter to the key row. */
@Composable
private fun Modifier.barPadding(): Modifier =
    this
        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        .padding(horizontal = 10.dp, vertical = 5.dp)

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
    suggestion: String?,
    attachments: List<com.athea.app.core.model.Attachment>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onExpandEditor: () -> Unit,
    onAddClick: () -> Unit,
    onRemoveAttachment: (com.athea.app.core.model.Attachment) -> Unit,
    enterSends: Boolean,
    modifier: Modifier = Modifier,
) {
    var lineCount by remember { mutableStateOf(1) }
    var grown by remember { mutableStateOf(false) }
    if (draft.isEmpty()) grown = false
    if (lineCount > 1) grown = true
    // Sticky growth: a mid-length draft wraps in the narrow width but
    // fits in the wide one - without the latch the panel flip-flops.
    val compact = !grown

    Surface(
        shape = Ui.panelShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .barPadding(),
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            if (attachments.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachments.forEach { attachment ->
                        AttachmentChip(
                            attachment = attachment,
                            onRemove = { onRemoveAttachment(attachment) },
                        )
                    }
                }
            }

            Box {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (compact) 50.dp else 16.dp,
                            end = if (compact) Ui.composerButtonSpace else Ui.composerFieldEndGrown,
                            top = if (compact) 4.dp else Ui.composerFieldTopGrown,
                            bottom = if (compact) 0.dp else Ui.composerButtonRowHeight,
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
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .then(if (compact) Modifier.heightIn(min = 40.dp) else Modifier),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (draft.isEmpty()) {
                                Text(
                                    stringResource(R.string.input_hint),
                                    style = messageStyle(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            // Ghost suggestion: the opaque draft text covers the
                            // prefix, the gray tail sticks out past the cursor.
                            if (suggestion != null && suggestion.startsWith(draft)) {
                                Text(
                                    suggestion,
                                    style = messageStyle(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.45f),
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (compact) {
                    IconButton(onClick = onAddClick, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.attach_add),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Row(
                    Modifier
                        .align(if (compact) Alignment.CenterEnd else Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = if (compact) 0.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!compact) {
                        IconButton(onClick = onExpandEditor, modifier = Modifier.size(Ui.composerButtonSize)) {
                            Icon(
                                Icons.Default.OpenInFull,
                                contentDescription = stringResource(R.string.cd_expand_editor),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val hasText = draft.isNotBlank() || attachments.isNotEmpty()
                    FilledIconButton(
                        onClick = onSend,
                        enabled = hasText,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (hasText) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            contentColor = if (hasText) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.size(Ui.composerButtonSize),
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = stringResource(R.string.cd_send),
                        )
                    }
                }
                if (!compact) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.align(Alignment.BottomStart),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.attach_add),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: com.athea.app.core.model.Attachment,
    onRemove: () -> Unit,
) {
    Box(
        Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
            Icon(
                Icons.Default.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp),
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp),
                )
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
        shape = Ui.panelShape,
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
                shape = Ui.panelShape,
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
