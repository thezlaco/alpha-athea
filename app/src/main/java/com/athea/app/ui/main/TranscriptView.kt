package com.athea.app.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.athea.app.R
import com.athea.app.core.model.CommandBlock
import com.athea.app.core.model.DisplayMode
import com.athea.app.core.model.OutputBlock
import com.athea.app.ui.SearchState
import com.athea.app.ui.SessionUi
import com.athea.app.ui.theme.Ui
import com.athea.app.ui.theme.CodeStyle
import com.athea.app.ui.theme.HighlightColor
import com.athea.app.ui.theme.OnHighlightColor
import com.athea.app.ui.theme.codeStyle
import com.athea.app.util.trimCommand
import com.athea.app.ui.theme.messageStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private const val TAIL_LINES = 5
private const val VIRTUALIZE_LINES_FACTOR = Ui.virtualizeLinesFactor
private const val CHUNK_LINES = 200 // kept for compat, not used (chunking via chunkSize)

private sealed interface DisplayItem {
    data class Block(val view: com.athea.app.transcript.BlockView) : DisplayItem
    data class Chunk(
        val blockId: String,
        val chunk: AnnotatedString,
        val chunkIndex: Int,
        val isFirst: Boolean,
        val isLast: Boolean,
        val exitCode: Int?,
    ) : DisplayItem
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TranscriptView(
    session: SessionUi,
    search: SearchState?,
    scrollRequests: Flow<String>,
    jumpToBottom: Flow<Unit>,
    previewLines: Int,
    contentTopPadding: Dp,
    pinchZoomEnabled: Boolean,
    virtualizeLargeOutput: Boolean = false,
    onOutputFontZoom: (Float) -> Unit,
    onToggleBlock: (String) -> Unit,
    onRevealBlock: (String) -> Unit,
    onLocateBlock: (String) -> Int,
    onCopyCommand: (String) -> Unit,
    onSelectCommandText: (String) -> Unit,
    onAddToFavorites: (String) -> Unit,
    onAreaResized: (rows: Int, cols: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val maxBubbleWidth = with(density) {
            (constraints.maxWidth * Ui.bubbleMaxWidthFraction).toInt().toDp()
        }

        // Report the real cell geometry to the engine so line wrapping matches.
        val measurer = rememberTextMeasurer()
        val cellStyle = codeStyle()
        LaunchedEffect(constraints.maxWidth, constraints.maxHeight, cellStyle) {
            val layout = measurer.measure(AnnotatedString("M"), cellStyle)
            val cols = (constraints.maxWidth / layout.size.width.coerceAtLeast(1))
                .coerceAtLeast(1)
            val rows = (constraints.maxHeight / layout.size.height.coerceAtLeast(1))
                .coerceAtLeast(1)
            onAreaResized(rows, cols)
        }

        if (session.displayMode == DisplayMode.RAW) {
            // Cap raw view rendering: the full text lives in the journal.
            val rawCapped = session.rawText.takeLast(10_000)
            RawStreamView(
                text = rawCapped,
                query = search?.query,
                modifier = Modifier.fillMaxSize(),
            )
            return@BoxWithConstraints
        }

        val listState = rememberLazyListState()
        val views = session.blocks
        // Architectural: huge fully-expanded output is split into multiple outer
        // LazyColumn items (chunks) so outer virtualization composes only visible
        // chunks and scrollToItem(last) is truly very end — no large offset hack.
        // Termux does same via TerminalBuffer grid, we do via chunked items.
        val displayItems = remember(views, previewLines, virtualizeLargeOutput) {
            val out = mutableListOf<DisplayItem>()
            for (view in views) {
                val block = view.block
                if (block is OutputBlock && !view.collapsed && !virtualizeLargeOutput) {
                    val plain = block.text.trimCommand()
                    val lineCount = plain.count { it == '\n' } + 1
                    val isHuge = lineCount > previewLines * VIRTUALIZE_LINES_FACTOR || plain.length > 8000
                    if (isHuge) {
                        val annotated = run {
                            val t = block.annotated.text.trimEnd('\n')
                            if (t.length == block.annotated.text.length) block.annotated
                            else AnnotatedString(t, block.annotated.spanStyles, block.annotated.paragraphStyles)
                        }
                        val chunks = chunkAnnotated(annotated)
                        chunks.forEachIndexed { idx, chunk ->
                            out.add(DisplayItem.Chunk(block.id, chunk, idx, idx == 0, idx == chunks.lastIndex, block.exitCode))
                        }
                        continue
                    }
                }
                out.add(DisplayItem.Block(view))
            }
            out
        }
        // Map blockId -> first display index for search navigation
        val blockToDisplayIndex = remember(displayItems) {
            val map = mutableMapOf<String, Int>()
            displayItems.forEachIndexed { idx, item ->
                val id = when (item) {
                    is DisplayItem.Block -> item.view.block.id
                    is DisplayItem.Chunk -> item.blockId
                }
                map.putIfAbsent(id, idx)
            }
            map
        }
        val itemCount = displayItems.size
        val lastTextLength =
            (views.lastOrNull()?.block as? OutputBlock)?.text?.length ?: 0

        // Stick to the bottom while the user is near it and output grows.
        // No offset hack needed — last display item is last chunk's bottom.
        LaunchedEffect(itemCount, lastTextLength) {
            if (itemCount == 0) return@LaunchedEffect
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val nearBottom = info.totalItemsCount == 0 ||
                lastVisible >= info.totalItemsCount - 2 || !listState.canScrollForward
            if (nearBottom) try { listState.scrollToItem(itemCount - 1) } catch (_: Exception) {}
        }

        // Search navigation: reveal the block, then scroll to its first chunk.
        LaunchedEffect(session.id) {
            scrollRequests.collect { blockId ->
                val index = blockToDisplayIndex[blockId] ?: onLocateBlock(blockId)
                if (index >= 0) {
                    onRevealBlock(blockId)
                    listState.animateScrollToItem(index)
                } else {
                    val fallback = onLocateBlock(blockId)
                    if (fallback >= 0) {
                        onRevealBlock(blockId)
                        listState.animateScrollToItem(blockToDisplayIndex[views[fallback].block.id] ?: fallback)
                    }
                }
            }
        }

        // Forced jumps to the very bottom (e.g. right after sending). Animate slightly slower than instant (250ms) as requested, but still much faster than default.
        LaunchedEffect(session.id, itemCount) {
            jumpToBottom.collect {
                val last = itemCount - 1
                if (last >= 0) try { listState.animateScrollToItem(last) } catch (_: Exception) {}
            }
        }

        // Jump button: appears when scrolling down with room below,
        // disappears when scrolling up or reaching the bottom.
        var showJumpDown by remember { mutableStateOf(false) }
        LaunchedEffect(listState) {
            var previous = -1L
            snapshotFlow {
                listState.firstVisibleItemIndex.toLong() * 100_000L +
                    listState.firstVisibleItemScrollOffset
            }.collect { pos ->
                if (pos != previous) {
                    val movingDown = previous >= 0 && pos > previous
                    if (movingDown) {
                        showJumpDown = listState.canScrollForward
                    } else if (!movingDown) {
                        showJumpDown = false
                    }
                    previous = pos
                }
            }
        }

        val currentMatchId = search?.matchBlockIds?.getOrNull(search.index)

        if (views.isEmpty()) {
            EmptyGreeting(Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (pinchZoomEnabled) {
                        Modifier.pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom != 1f) onOutputFontZoom(zoom)
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            contentPadding = PaddingValues(
                top = contentTopPadding + Ui.contentPaddingV,
                bottom = Ui.contentPaddingV,
            ),
        ) {
            items(displayItems, key = { item ->
                when (item) {
                    is DisplayItem.Block -> item.view.block.id
                    is DisplayItem.Chunk -> "${item.blockId}-chunk-${item.chunkIndex}"
                }
            }) { item ->
                when (item) {
                    is DisplayItem.Block -> {
                        val view = item.view
                        when (val block = view.block) {
                            is CommandBlock -> CommandBubble(
                                block = block,
                                collapsed = view.collapsed,
                                maxWidth = maxBubbleWidth,
                                previewLines = previewLines,
                                currentMatch = block.id == currentMatchId,
                                onToggle = { onToggleBlock(block.id) },
                                onCopy = { onCopyCommand(block.text) },
                                onSelectText = { onSelectCommandText(block.text) },
                                onFavorite = { onAddToFavorites(block.text) },
                            )
                            is OutputBlock -> OutputPanel(
                                block = block,
                                collapsed = view.collapsed,
                                query = search?.query,
                                currentMatch = block.id == currentMatchId,
                                virtualizeEnabled = virtualizeLargeOutput,
                                previewLines = previewLines,
                                jumpToBottom = jumpToBottom,
                                onToggle = { onToggleBlock(block.id) },
                            )
                        }
                    }
                    is DisplayItem.Chunk -> {
                        // Huge fully-expanded block split into outer items — true virtualization
                        // No inner scroll, no label, fully visible but composed only when on screen.
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (item.blockId == currentMatchId) MaterialTheme.colorScheme.primary.copy(alpha = Ui.highlightAlpha)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .padding(horizontal = Ui.outputPaddingH, vertical = if (item.isFirst) Ui.headerPaddingV else 0.dp)
                        ) {
                            if (item.isFirst && item.exitCode != null && item.exitCode != 0) {
                                Text(
                                    text = stringResource(R.string.exit_code, item.exitCode),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(bottom = Ui.keyRowPaddingV),
                                )
                            }
                            SelectionContainer {
                                Text(
                                    text = remember(item.chunk, search?.query) { item.chunk.withSearchHighlights(search?.query) },
                                    style = codeStyle(),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (item.isLast) {
                                val collapsible = true
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.cd_collapse),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Ui.chevronAlpha),
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(top = Ui.keyRowPaddingV)
                                        .size(Ui.chevronCollapseSize)
                                        .rotate(180f)
                                        .clickable(onClick = { onToggleBlock(item.blockId) }),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showJumpDown) {
            val scope = rememberCoroutineScope()
            Surface(
                onClick = {
                    // Use layoutInfo totalItemsCount at click time (fresh), not captured itemCount, so repeated presses always go to true end even if new chunks arrived
                    val last = listState.layoutInfo.totalItemsCount - 1
                    if (last >= 0) scope.launch { try { listState.animateScrollToItem(last) } catch (_: Exception) {} }
                    else {
                        val fallback = itemCount - 1
                        if (fallback >= 0) scope.launch { try { listState.animateScrollToItem(fallback) } catch (_: Exception) {} }
                    }
                },
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 10.dp),
            ) {
                    Box(Modifier.size(Ui.scrollbarButtonSize), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_scroll_down),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
            }
        }
    }
}

@Composable
private fun EmptyGreeting(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Welcome to Athea!",
            style = CodeStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Ui.spacerMedium))
        Text(
            text = "Working with the terminal:\n" +
                "- Type:   enter a command below\n" +
                "- Send:   tap the arrow button\n" +
                "- Edit:   tap the expand icon for multi-line",
            style = CodeStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Ui.spacerMedium))
        Text(
            text = "Working with sessions:\n" +
                "- Drawer:  tap the menu icon (top left)\n" +
                "- New:     tap the + icon (top right)\n" +
                "- Search:  tap ⋮ → Search",
            style = CodeStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Ui.spacerMedium))
        Text(
            text = "Long-press a sent command to add it to favorites.",
            style = CodeStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommandBubble(
    block: CommandBlock,
    collapsed: Boolean,
    maxWidth: Dp,
    previewLines: Int,
    currentMatch: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onSelectText: () -> Unit,
    onFavorite: () -> Unit,
) {
    val lines = block.text.lines()
    val collapsible = lines.size > previewLines
    val bubbleStyle = messageStyle()
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Ui.outputPaddingH, vertical = Ui.headerPaddingV),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = Ui.bubbleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = if (currentMatch) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
            modifier = Modifier
                .widthIn(max = maxWidth)
                .combinedClickable(
                    // Tapping the bubble body is reserved for text
                    // selection; expand/collapse lives on the chevrons.
                    onClick = {},
                    onLongClick = { menuOpen = true },
                ),
        ) {
            Column(Modifier.padding(start = Ui.bubblePaddingH, end = Ui.bubblePaddingH, top = Ui.bubblePaddingTop, bottom = Ui.bubblePaddingBottom)) {
                if (collapsed && collapsible) {
                    Box {
                        Text(
                            text = lines.take(previewLines).joinToString("\n"),
                            style = bubbleStyle,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        // Fade hugs the tail only: the first lines stay fully readable.
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(Ui.tailFadeHeight)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            MaterialTheme.colorScheme.secondaryContainer,
                                        ),
                                    ),
                                ),
                        )
                    }
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_expand_editor),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = Ui.keyRowPaddingV)
                            .size(Ui.chevronExpandSize)
                            .clickable(onClick = onToggle),
                    )
                } else {
                    Text(
                        text = block.text,
                        style = bubbleStyle,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    if (collapsible) {
                        // Calm collapse affordance at the tail, mirroring
                        // the expand chevron of the collapsed preview.
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_collapse),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                .copy(alpha = Ui.chevronAlphaCollapsedPreview),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = Ui.keyRowPaddingV)
                                .size(Ui.chevronExpandSize)
                                .rotate(180f)
                                .clickable(onClick = onToggle),
                        )
                    }
                }
            }

            AtheaDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                AtheaDropdownItem(
                    icon = Icons.Default.ContentCopy,
                    text = stringResource(R.string.copy),
                    onClick = { menuOpen = false; onCopy() },
                )
                AtheaDropdownItem(
                    icon = Icons.Default.TextFields,
                    text = stringResource(R.string.select_text),
                    onClick = { menuOpen = false; onSelectText() },
                )
                AtheaDropdownItem(
                    icon = Icons.Default.Star,
                    text = stringResource(R.string.add_to_favorites),
                    onClick = { menuOpen = false; onFavorite() },
                )
            }
        }
    }
}

@Composable
private fun OutputPanel(
    block: OutputBlock,
    collapsed: Boolean,
    query: String?,
    currentMatch: Boolean,
    virtualizeEnabled: Boolean,
    previewLines: Int,
    jumpToBottom: Flow<Unit>,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Ui.outputPaddingH, vertical = Ui.headerPaddingV)
            .then(
                if (currentMatch) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = Ui.highlightAlpha))
                } else {
                    Modifier
                },
            ),
    ) {
        val code = block.exitCode
        if (!block.running && code != null && code != 0) {
            Text(
                text = stringResource(R.string.exit_code, code),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = Ui.keyRowPaddingV),
            )
        }

        val plainText = if (block.running) block.text else block.text.trimCommand()
        val displayAnnotated = if (block.running) block.annotated else {
            // Trim trailing newline for display, keep spans aligned
            val t = block.annotated.text.trimEnd('\n')
            if (t.length == block.annotated.text.length) block.annotated
            else AnnotatedString(t, block.annotated.spanStyles, block.annotated.paragraphStyles)
        }
        // Fast line count without allocating list (huge text 1.2M would allocate 20k strings)
        val lineCount = plainText.count { it == '\n' } + 1
        val collapsible = lineCount > TAIL_LINES && !block.running

        if (collapsed && collapsible) {
            val allLines = plainText.lines()
            Box {
                Text(
                    text = allLines.takeLast(TAIL_LINES).joinToString("\n"),
                    style = codeStyle(),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                // Fade at the bottom: suggests more content, tap to expand.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(Ui.fadeHeight)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                )
            }
            // Centered expand chevron, same pattern as command bubbles.
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_expand_editor),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = Ui.keyRowPaddingV)
                    .size(Ui.chevronExpandSize)
                    .clickable(onClick = onToggle),
            )
        } else if (virtualizeEnabled && lineCount > previewLines * VIRTUALIZE_LINES_FACTOR) {
            // Large output: internal scroll only when toggle on; threshold is 4× collapsed lines.
            VirtualizedOutput(annotated = displayAnnotated, query = query, jumpToBottom = jumpToBottom)
            if (block.running) {
                BlinkingCursor()
            } else if (collapsible) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_collapse),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Ui.chevronAlpha),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = Ui.keyRowPaddingV)
                        .size(Ui.chevronCollapseSize)
                        .rotate(180f)
                        .clickable(onClick = onToggle),
                )
            }
        } else {
            // Always chunk huge text to avoid Compose Text OOM, even when
            // virtualize toggle is off. Off = fully expanded Column (no inner
            // scroll, no label), on = bounded LazyColumn inside 50% box.
            // Termux solves same via TerminalBuffer grid + canvas draw; we copy
            // by chunking AnnotatedString (4k) and rendering per-chunk Texts.
            val isHuge = lineCount > previewLines * Ui.virtualizeLinesFactor || plainText.length > Ui.hugeCharsThreshold
            if (isHuge) {
                ChunkedExpanded(annotated = displayAnnotated, query = query)
            } else {
                SelectionContainer {
                    val annotated = remember(displayAnnotated, query) { displayAnnotated.withSearchHighlights(query) }
                    Text(
                        text = annotated,
                        style = codeStyle(),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (block.running) {
                BlinkingCursor()
            } else if (collapsible) {
                // Collapse affordance: centered, same style as command bubbles.
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_collapse),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Ui.chevronAlpha),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = Ui.keyRowPaddingV)
                        .size(Ui.chevronCollapseSize)
                        .rotate(180f)
                        .clickable(onClick = onToggle),
                )
            }
        }
    }
}

private fun chunkAnnotated(annotated: AnnotatedString): List<AnnotatedString> {
    val total = annotated.text.length
    val chunkSize = Ui.chunkSize
    val list = mutableListOf<AnnotatedString>()
    var offset = 0
    while (offset < total) {
        val end = minOf(offset + chunkSize, total)
        val chunkEnd = if (end < total) {
            val nextNl = annotated.text.indexOf('\n', end)
            if (nextNl != -1 && nextNl - offset < chunkSize + Ui.chunkSlack) nextNl + 1 else end
        } else end
        list.add(annotated.subSequence(offset, chunkEnd))
        offset = chunkEnd
    }
    return list
}

@Composable
private fun VirtualizedOutput(annotated: AnnotatedString, query: String?, jumpToBottom: Flow<Unit>? = null) {
    val chunks = remember(annotated) { chunkAnnotated(annotated) }
    val innerState = rememberLazyListState()
    // Termux-like: inner virtualized list also pinned to bottom when new output arrives — instant, not animated
    androidx.compose.runtime.LaunchedEffect(chunks.size) {
        if (chunks.isNotEmpty()) try { innerState.scrollToItem(chunks.size - 1) } catch (_: Exception) {}
    }
    if (jumpToBottom != null) {
        androidx.compose.runtime.LaunchedEffect(jumpToBottom) {
            jumpToBottom.collect {
                if (chunks.isNotEmpty()) try { innerState.scrollToItem(chunks.size - 1) } catch (_: Exception) {}
            }
        }
    }
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val maxH = screenHeight * Ui.virtualizedMaxFraction
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxH)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = Ui.virtualizedBgAlpha)),
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                state = innerState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(chunks.size) { idx ->
                    val chunk = chunks[idx]
                    SelectionContainer {
                        Text(
                            text = remember(chunk, query) { chunk.withSearchHighlights(query) },
                            style = codeStyle(),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.scroll_inside),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ChunkedExpanded(annotated: AnnotatedString, query: String?) {
    // Termux-like: same chunking but fully expanded (no inner scroll, no label)
    // Prevents single huge Text OOM while keeping default fully visible.
    val chunks = remember(annotated) { chunkAnnotated(annotated) }
    SelectionContainer {
        Column(Modifier.fillMaxWidth()) {
            chunks.forEach { chunk ->
                Text(
                    text = remember(chunk, query) { chunk.withSearchHighlights(query) },
                    style = codeStyle(),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun AnnotatedString.withSearchHighlights(query: String?): AnnotatedString {
    if (query.isNullOrBlank()) return this
    val needle = query.lowercase()
    val haystackLower = text.lowercase()
    val builder = AnnotatedString.Builder(this)
    var from = 0
    while (true) {
        val idx = haystackLower.indexOf(needle, from)
        if (idx < 0) break
        builder.addStyle(
            SpanStyle(background = HighlightColor, color = OnHighlightColor),
            idx, idx + needle.length
        )
        from = idx + needle.length
    }
    return builder.toAnnotatedString()
}

@Composable
private fun RawStreamView(
    text: String,
    query: String?,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(text.length) { scroll.scrollTo(scroll.maxValue) }
    SelectionContainer {
        Text(
            text = buildHighlighted(text.ifEmpty { " " }, query),
            style = codeStyle(),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = modifier
                .verticalScroll(scroll)
                .padding(Ui.outputPaddingH),
        )
    }
}

@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    Text(
        text = "▊",
        style = codeStyle(),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .alpha(alpha)
            .padding(top = Ui.keyRowPaddingV),
    )
}

private fun buildHighlighted(text: String, query: String?): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (query.isNullOrBlank()) return@buildAnnotatedString
        val lower = text.lowercase()
        val needle = query.lowercase()
        var from = 0
        while (true) {
            val idx = lower.indexOf(needle, from)
            if (idx < 0) break
            addStyle(
                SpanStyle(background = HighlightColor, color = OnHighlightColor),
                idx,
                idx + needle.length,
            )
            from = idx + needle.length
        }
    }
