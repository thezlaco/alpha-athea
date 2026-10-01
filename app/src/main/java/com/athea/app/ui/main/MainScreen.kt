package com.athea.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athea.app.R
import com.athea.app.data.KeyKind
import com.athea.app.ui.MainViewModel
import com.athea.app.ui.UiEvent
import com.athea.app.ui.UiState
import com.athea.app.ui.common.AtheaDialogContainer
import com.athea.app.ui.theme.LocalMessageFontSize
import com.athea.app.ui.theme.LocalOutputFontSize
import com.athea.app.ui.theme.Ui
import com.athea.app.util.copyToClipboard
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.showKeyBuilder) {
        BackHandler { viewModel.setShowKeyBuilder(false) }
        KeyBuilderScreen(
            onAdd = { key ->
                viewModel.addCustomKey(key)
            },
            onBack = { viewModel.setShowKeyBuilder(false) },
        )
        return
    }

    if (state.showSettings) {
        BackHandler { viewModel.setShowSettings(false) }
        SettingsScreen(
            keyRowVisible = state.keyRowVisible,
            enterSends = state.enterSends,
            autoScrollOnSend = state.autoScrollOnSend,
            rawStream = state.rawStream,
            autocompleteEnabled = state.autocompleteEnabled,
            pinchZoomEnabled = state.pinchZoomEnabled,
            virtualizeLargeOutput = state.virtualizeLargeOutput,
            outputFontSizeSp = state.outputFontSizeSp,
            previewLines = state.previewLines,
            bubbleFontSizeSp = state.bubbleFontSizeSp,
            customKeys = state.customKeys,
            onKeyRowVisibleChange = viewModel::setKeyRowVisible,
            onEnterSendsChange = viewModel::setEnterSends,
            onAutoScrollOnSendChange = viewModel::setAutoScrollOnSend,
            onRawStreamChange = viewModel::setRawStream,
            onAutocompleteEnabledChange = viewModel::setAutocompleteEnabled,
            onPinchZoomEnabledChange = viewModel::setPinchZoomEnabled,
            onVirtualizeLargeOutputChange = viewModel::setVirtualizeLargeOutput,
            onOutputFontSizeChange = viewModel::setOutputFontSize,
            onPreviewLinesChange = viewModel::setPreviewLines,
            onBubbleFontSizeChange = viewModel::setBubbleFontSize,
            onCustomKeysChange = viewModel::setCustomKeys,
            onResetKeys = viewModel::resetKeysToDefaults,
            onOpenKeyBuilder = { viewModel.setShowKeyBuilder(true) },
            onBack = { viewModel.setShowSettings(false) },
        )
        return
    }

    if (state.showFavorites) {
        BackHandler { viewModel.setShowFavorites(false) }
        FavoritesScreen(
            favorites = state.favorites,
            onBack = { viewModel.setShowFavorites(false) },
            onInsert = viewModel::insertIntoDraft,
            onRun = viewModel::executeDirectly,
            onEdit = viewModel::updateFavorite,
            onDelete = viewModel::deleteFavorite,
        )
        return
    }

    // ---- Attachment picker flow -----------------------------------------

    val pickerContext = LocalContext.current
    var attachAction by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val action = attachAction
        attachAction = null
        if (uri != null && action != null) {
            viewModel.importAttachment(uri, action, pickerContext)
        }
    }
    if (state.showAttachChooser) {
        val attachOptions: List<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>> = listOf(
            Triple("copy", stringResource(R.string.attach_copy), Icons.Filled.ContentCopy),
            Triple("move", stringResource(R.string.attach_move), Icons.Filled.ContentCut),
            Triple("link", stringResource(R.string.attach_link), Icons.Filled.InsertLink),
            Triple("name", stringResource(R.string.attach_name), Icons.Filled.Title),
        )
        AtheaDialogContainer(
            title = stringResource(R.string.attach_chooser_title),
            onDismiss = { viewModel.setShowAttachChooser(false) },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    attachOptions.forEach { (action, label, icon) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(Ui.attachmentShape)
                                .clickable {
                                    attachAction = action
                                    viewModel.setShowAttachChooser(false)
                                    filePicker.launch(arrayOf("*/*"))
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            },
            confirm = {},
        )
    }

    CompositionLocalProvider(
        LocalOutputFontSize provides state.outputFontSizeSp,
        LocalMessageFontSize provides state.bubbleFontSizeSp,
    ) {
        MainScreenContent(viewModel, state)
    }
}

@Composable
private fun MainScreenContent(viewModel: MainViewModel, state: UiState) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Native toasts: the system renders them, no custom surfaces needed.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                UiEvent.ShellStartFailed ->
                    context.getString(R.string.shell_start_failed)

                is UiEvent.ShellExited ->
                    context.getString(R.string.shell_exited, event.exitCode)
            }
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    val current = state.sessions.firstOrNull { it.id == state.currentSessionId }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Drawer swipes must not fight with fullscreen overlays.
        gesturesEnabled = !state.editorExpanded && state.selectTextPayload == null,
        drawerContent = {
            SessionsDrawerContent(
                sessions = state.sessions,
                currentSessionId = state.currentSessionId,
                onSelectSession = { id ->
                    viewModel.selectSession(id)
                    scope.launch { drawerState.close() }
                },
                onRenameSession = viewModel::requestRename,
                onTogglePin = viewModel::togglePin,
                onDeleteSession = viewModel::requestDelete,
                onOpenFavorites = { viewModel.setShowFavorites(true) },
                onSettings = { viewModel.setShowSettings(true) },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Full-bleed: the scrim and floating top buttons must reach
            // the status bar; insets are handled per-component instead.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val topScrimHeight = statusBarTop + Ui.topBarScrimHeight
            // Outer Box overlays TopBar/scrim above the Column so the menu
            // scrim covers the whole screen (transcript + composer + key row),
            // not just the transcript area — dismiss works from any tap.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // Transcript fills the available area; the bottom bar
                // (InputBar + key row) floats above it for both the
                // session and the empty state, wired in exactly one place.
            if (current != null) {
                    // Peek-through: let transcript peek behind InputBar margins with dimming; keep last line above KeyRow only
                    val overlayBottom = 72.dp + Ui.contentPaddingV
                    TranscriptView(
                        session = current,
                        search = state.search,
                        scrollRequests = viewModel.scrollRequests,
                        jumpToBottom = viewModel.jumpToBottom,
                        previewLines = state.previewLines,
                        contentTopPadding = statusBarTop + Ui.topBarContentTop,
                        contentBottomPadding = overlayBottom,
                        pinchZoomEnabled = state.pinchZoomEnabled,
                        virtualizeLargeOutput = state.virtualizeLargeOutput,
                        onOutputFontZoom = viewModel::onOutputFontZoom,
                        onToggleBlock = viewModel::toggleBlockCollapsed,
                        onRevealBlock = viewModel::revealBlock,
                        onLocateBlock = { blockId ->
                            current.blocks.indexOfFirst { it.block.id == blockId }
                        },
                        onCopyCommand = { text ->
                            context.copyToClipboard(text, "athea-command")
                        },
                        onSelectCommandText = viewModel::showSelectText,
                        onAddToFavorites = viewModel::addFavorite,
                        onAreaResized = viewModel::onTranscriptAreaResized,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Obvious half-height dimming: starts mid-pill, strong fade so peek is visible
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.45f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.70f),
                                )
                            ),
                    )
                }

                BottomBar(
                    viewModel = viewModel,
                    state = state,
                    draft = if (current != null) current.draft.orEmpty() else "",
                    suggestion = if (state.search == null) state.suggestion else null,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                )

                // Top scrim: same gradient as before, now sibling of Column
                // so it sits above transcript but below TopBar.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(topScrimHeight)
                        .align(Alignment.TopStart)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black,
                                1f to Color.Transparent,
                            )
                        ),
                )

                // TopBar overlay — its internal menu scrim is now full-screen
                // (covers transcript + composer + key row) because TopBar
                // fills the outer Box, not just the transcript Box.
                TopBar(
                    modifier = Modifier.align(Alignment.TopCenter),
                    hasMessages = current?.blocks?.isNotEmpty() == true,
                    pinned = current?.pinned ?: false,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNewSession = viewModel::newSession,
                    onSearch = viewModel::enterSearch,
                    onRename = { current?.let { viewModel.requestRename(it.id) } },
                    onTogglePin = { current?.let { viewModel.togglePin(it.id) } },
                    onDelete = { current?.let { viewModel.requestDelete(it.id) } },
                )
            }
        }
    }

    // ---- Fullscreen overlays -------------------------------------------

    if (state.editorExpanded && current != null) {
        EditorOverlay(
            draft = current.draft,
            onDraftChange = viewModel::updateDraft,
            onSend = viewModel::sendFromExpandedEditor,
            onClose = { viewModel.setEditorExpanded(false) },
        )
    }

    state.selectTextPayload?.let { payload ->
        SelectTextScreen(text = payload, onClose = viewModel::dismissSelectText)
    }

    // ---- Dialogs ---------------------------------------------------------

    state.renameTargetId?.let { id ->
        val target = state.sessions.firstOrNull { it.id == id }
        if (target != null) {
            RenameDialog(
                initialName = target.name,
                onDismiss = viewModel::cancelRename,
                onSave = { name -> viewModel.renameSession(id, name) },
            )
        }
    }

    state.deleteTargetId?.let { id ->
        val target = state.sessions.firstOrNull { it.id == id }
        if (target != null) {
            DeleteConfirmDialog(
                sessionName = target.name,
                onDismiss = viewModel::cancelDelete,
                onConfirm = viewModel::confirmDelete,
            )
        }
    }
}

/**
 * The single wiring for the bottom panel (InputBar + key row). Used by both
 * the active-session and the empty states — there is exactly one place that
 * binds the composer to the ViewModel here, instead of the old copy-paste
 * of the whole block.
 */
@Composable
private fun BottomBar(
    viewModel: MainViewModel,
    state: UiState,
    draft: String,
    suggestion: String?,
    modifier: Modifier = Modifier,
) {
    InputBar(
        draft = draft,
        suggestion = suggestion,
        attachments = state.attachments,
        onDraftChange = viewModel::updateDraft,
        onSend = viewModel::sendDraft,
        onExpandEditor = { viewModel.setEditorExpanded(true) },
        onAddClick = { viewModel.setShowAttachChooser(true) },
        onRemoveAttachment = viewModel::removeAttachment,
        search = state.search,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSearchNext = viewModel::nextSearchMatch,
        onExitSearch = viewModel::exitSearch,
        enterSends = state.enterSends,
    )
    if (state.keyRowVisible && state.search == null) {
        val keys = remember(state.customKeys) { keyRowKeys(state) }
        KeyRow(
            keys = keys,
            stickyCtrl = state.stickyCtrl,
            suggestionActive = suggestion != null,
            onInsert = viewModel::insertIntoDraft,
            onSendBytes = viewModel::sendDirectText,
            onAcceptSuggestion = viewModel::acceptSuggestion,
            onToggleStickyCtrl = viewModel::toggleStickyCtrl,
            onConsumeStickyCtrl = viewModel::consumeStickyCtrl,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/** User-configured keys, or the built-in row when nothing is customized. */
private fun keyRowKeys(state: UiState): List<TerminalKey> {
    val custom = state.customKeys
    if (custom.isEmpty()) return DefaultKeys.row
    return custom.map { key ->
        TerminalKey(
            label = key.label,
            kind = when (key.kind) {
                KeyKind.INSERT -> KeyActionKind.INSERT_INTO_DRAFT
                KeyKind.SEND -> KeyActionKind.SEND_TO_TERMINAL
                KeyKind.CTRL -> KeyActionKind.TOGGLE_STICKY_CTRL
            },
            payload = key.payload,
        )
    }
}


