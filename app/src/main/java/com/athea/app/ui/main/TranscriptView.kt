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
import androidx.compose.ui.res.pluralStringResource
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
import com.athea.app.ui.theme.CodeStyle
import com.athea.app.ui.theme.HighlightColor
import com.athea.app.ui.theme.OnHighlightColor
import com.athea.app.ui.theme.codeStyle
import com.athea.app.ui.theme.messageStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private const val TAIL_LINES = 3
private val FADE_HEIGHT = 24.dp
private val TAIL_FADE_HEIGHT = 26.dp

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
            (constraints.maxWidth * 0.85f).toInt().toDp()
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
            RawStreamView(
                text = session.rawText,
                query = search?.query,
                modifier = Modifier.fillMaxSize(),
            )
            return@BoxWithConstraints
        }

        val listState = rememberLazyListState()
        val views = session.blocks
        val itemCount = views.size
        val lastTextLength =
            (views.lastOrNull()?.block as? OutputBlock)?.text?.length ?: 0

        // Stick to the bottom while the user is near it and output grows.
        LaunchedEffect(itemCount, lastTextLength) {
            if (itemCount == 0) return@LaunchedEffect
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val nearBottom = info.totalItemsCount == 0 ||
                lastVisible >= info.totalItemsCount - 2
            if (nearBottom) listState.scrollToItem(itemCount - 1)
        }

        // Search navigation: reveal the block, then scroll to it.
        LaunchedEffect(session.id) {
            scrollRequests.collect { blockId ->
                val index = onLocateBlock(blockId)
                if (index >= 0) {
                    onRevealBlock(blockId)
                    listState.animateScrollToItem(index)
                }
            }
        }

        // Forced jumps to the very bottom (e.g. right after sending).
        LaunchedEffect(session.id) {
            jumpToBottom.collect {
                if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
            }
        }

        val canScrollForward by remember {
            derivedStateOf { listState.canScrollForward }
        }

        // The jump button only follows downward scrolling: moving up hides
        // it, moving down (with room below) brings it back.
        var showJumpDown by remember { mutableStateOf(false) }
        var lastScrollPos by remember { mutableStateOf(-1) }
        LaunchedEffect(listState) {
            snapshotFlow {
                listState.firstVisibleItemIndex * 100_000 +
                    listState.firstVisibleItemScrollOffset
            }.collect { pos ->
                if (pos != lastScrollPos) {
                    val movingDown = lastScrollPos in 0 until pos
                    showJumpDown = movingDown && listState.canScrollForward
                    lastScrollPos = pos
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
                top = contentTopPadding + 8.dp,
                bottom = 8.dp,
            ),
        ) {
            items(views, key = { it.block.id }) { view ->
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
                        onToggle = { onToggleBlock(block.id) },
                    )
                }
            }
        }

        if (showJumpDown) {
            val scope = rememberCoroutineScope()
            Surface(
                onClick = {
                    if (itemCount > 0) scope.launch { listState.animateScrollToItem(itemCount - 1) }
                },
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 10.dp),
            ) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
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
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Working with the terminal:\n" +
                "- Type:   enter a command below\n" +
                "- Send:   tap the arrow button\n" +
                "- Edit:   tap the expand icon for multi-line",
            style = CodeStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Working with sessions:\n" +
                "- Drawer:  tap the menu icon (top left)\n" +
                "- New:     tap the + icon (top right)\n" +
                "- Search:  tap ⋮ → Search",
            style = CodeStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
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
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
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
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
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
                                .height(TAIL_FADE_HEIGHT)
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
                            .padding(top = 2.dp)
                            .size(20.dp)
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
                                .copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 2.dp)
                                .size(20.dp)
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
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .then(
                if (currentMatch) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
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
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        val text = if (block.running) block.text else block.text.trimEnd('\n')
        val lineCount = text.lines().size
        val collapsible = lineCount > TAIL_LINES && !block.running

        if (collapsed && collapsible) {
            val allLines = text.lines()
            Row(
                Modifier
                    .clickable(onClick = onToggle)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⋯ " + pluralStringResource(
                        R.plurals.output_lines,
                        allLines.size,
                        allLines.size,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box {
                Text(
                    text = allLines.takeLast(TAIL_LINES).joinToString("\n"),
                    style = codeStyle(),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(FADE_HEIGHT)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background,
                                    androidx.compose.ui.graphics.Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        } else {
            SelectionContainer {
                val annotated = remember(text, query) { buildHighlighted(text, query) }
                Text(
                    text = annotated,
                    style = codeStyle(),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (block.running) {
                BlinkingCursor()
            } else if (collapsible) {
                // Collapse affordance: centered, same style as command bubbles.
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_collapse),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 2.dp)
                        .size(16.dp)
                        .rotate(180f)
                        .clickable(onClick = onToggle),
                )
            }
        }
    }
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
                .padding(12.dp),
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
            .padding(top = 2.dp),
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
