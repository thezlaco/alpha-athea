package com.athea.app.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athea.app.R
import com.athea.app.core.model.DisplayMode
import com.athea.app.ui.MainViewModel
import com.athea.app.ui.UiEvent
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                UiEvent.SettingsPlaceholder ->
                    context.getString(R.string.settings_placeholder)

                UiEvent.ShellStartFailed ->
                    context.getString(R.string.shell_start_failed)

                UiEvent.CopiedToClipboard ->
                    context.getString(R.string.copied)
            }
            snackbarHostState.showSnackbar(message)
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
                favorites = state.favorites,
                onSelectSession = { id ->
                    viewModel.selectSession(id)
                    scope.launch { drawerState.close() }
                },
                onRenameSession = viewModel::requestRename,
                onTogglePin = viewModel::togglePin,
                onDeleteSession = viewModel::requestDelete,
                onInsertFavorite = viewModel::insertIntoDraft,
                onRunFavorite = viewModel::executeDirectly,
                onSettings = viewModel::settingsPlaceholder,
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                TopBar(
                    title = current?.name.orEmpty(),
                    displayBlocks = current?.displayMode != DisplayMode.RAW,
                    pinned = current?.pinned ?: false,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNewSession = viewModel::newSession,
                    onSearch = viewModel::enterSearch,
                    onRename = { current?.let { viewModel.requestRename(it.id) } },
                    onTogglePin = { current?.let { viewModel.togglePin(it.id) } },
                    onToggleDisplayMode = {
                        current?.let {
                            val mode =
                                if (it.displayMode == DisplayMode.BLOCKS) {
                                    DisplayMode.RAW
                                } else {
                                    DisplayMode.BLOCKS
                                }
                            viewModel.setDisplayMode(mode)
                        }
                    },
                    onDelete = { current?.let { viewModel.requestDelete(it.id) } },
                )

                if (state.keyRowVisible && state.search == null) {
                    KeyRow(
                        stickyCtrl = state.stickyCtrl,
                        onInsert = viewModel::insertIntoDraft,
                        onSendBytes = viewModel::sendDirectText,
                        onToggleStickyCtrl = viewModel::toggleStickyCtrl,
                        onConsumeStickyCtrl = viewModel::consumeStickyCtrl,
                    )
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    if (current != null) {
                        TranscriptView(
                            session = current,
                            search = state.search,
                            scrollRequests = viewModel.scrollRequests,
                            onToggleBlock = viewModel::toggleBlockCollapsed,
                            onRevealBlock = viewModel::revealBlock,
                            onLocateBlock = { blockId ->
                                current.blocks.indexOfFirst { it.block.id == blockId }
                            },
                            onCopyCommand = { text ->
                                copyToClipboard(context, text)
                                viewModel.notifyCopied()
                            },
                            onSelectCommandText = viewModel::showSelectText,
                            onAddToFavorites = viewModel::addFavorite,
                            onAreaResized = viewModel::onTranscriptAreaResized,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                InputBar(
                    draft = current?.draft.orEmpty(),
                    onDraftChange = viewModel::updateDraft,
                    onSend = viewModel::sendDraft,
                    onExpandEditor = { viewModel.setEditorExpanded(true) },
                    search = state.search,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onSearchNext = viewModel::nextSearchMatch,
                    onExitSearch = viewModel::exitSearch,
                    enterSends = state.enterSends,
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

private fun copyToClipboard(context: Context, text: String) {
    val manager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("athea-command", text))
}
