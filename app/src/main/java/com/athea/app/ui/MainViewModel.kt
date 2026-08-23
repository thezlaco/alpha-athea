package com.athea.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.athea.app.R
import com.athea.app.data.AtheaStorage
import com.athea.app.data.FavoritesIndex
import com.athea.app.data.SessionMeta
import com.athea.app.data.SessionsIndex
import com.athea.app.core.journal.JournalEvent
import com.athea.app.core.journal.SessionJournal
import com.athea.app.core.model.CommandBlock
import com.athea.app.core.model.DisplayMode
import com.athea.app.core.model.FavoriteCommand
import com.athea.app.core.model.OutputBlock
import com.athea.app.core.terminal.EngineEvent
import com.athea.app.core.terminal.TerminalEngine
import com.athea.app.engine.NativeShellEngine
import com.athea.app.parse.StreamEvent
import com.athea.app.parse.StreamParser
import com.athea.app.transcript.BlockView
import com.athea.app.transcript.TranscriptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Search within one session transcript. */
data class SearchState(
    val query: String = "",
    val matchBlockIds: List<String> = emptyList(),
    val index: Int = 0,
)

/** Everything the main screen renders for one session. */
data class SessionUi(
    val id: Long,
    val name: String,
    val pinned: Boolean,
    val displayMode: DisplayMode,
    val draft: String,
    val blocks: List<BlockView>,
    val running: Boolean,
    val rawText: String,
)

data class UiState(
    val sessions: List<SessionUi> = emptyList(),
    val currentSessionId: Long? = null,
    val favorites: List<FavoriteCommand> = emptyList(),
    val search: SearchState? = null,
    val editorExpanded: Boolean = false,
    val stickyCtrl: Boolean = false,
    val keyRowVisible: Boolean = true,
    val enterSends: Boolean = true,
    val outputFontSizeSp: Int = 13,
    val autoScrollOnSend: Boolean = true,
    val previewLines: Int = 3,
    val bubbleFontSizeSp: Int = 16,
    val showSettings: Boolean = false,
    val showFavorites: Boolean = false,
    val renameTargetId: Long? = null,
    val deleteTargetId: Long? = null,
    val selectTextPayload: String? = null,
)

sealed interface UiEvent {
    data object ShellStartFailed : UiEvent
    data object CopiedToClipboard : UiEvent
}

/** Per-session pipeline pieces that always travel together. */
private class SessionPipe(
    val journal: SessionJournal,
    val parser: StreamParser,
    val builder: TranscriptBuilder,
)

/**
 * Owns live sessions end-to-end: persistence through [AtheaStorage],
 * transcripts through [TranscriptBuilder] fed by [StreamParser], processes
 * through [TerminalEngine]. One owner, no split brain between layers.
 *
 * History survives restarts: journals replay into fresh transcripts at
 * startup, then live shells are attached behind the restored sessions.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _events = MutableSharedFlow<UiEvent>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    /** One-shot requests to scroll the transcript to a block (search jumps). */
    private val _scrollRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val scrollRequests: SharedFlow<String> = _scrollRequests.asSharedFlow()

    /** One-shot requests to force the transcript to its very bottom. */
    private val _jumpToBottom = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val jumpToBottom: SharedFlow<Unit> = _jumpToBottom.asSharedFlow()

    private val storage = AtheaStorage(application.filesDir)

    private val metas = LinkedHashMap<Long, SessionMeta>()
    private val pipes = LinkedHashMap<Long, SessionPipe>()
    private val engines = HashMap<Long, TerminalEngine>()
    private val engineJobs = HashMap<Long, Job>()
    private val rawQueues = HashMap<Long, Channel<EngineEvent>>()
    private val nextCommandSeqs = HashMap<Long, Long>()

    /** Guards every multi-step session mutation against concurrent engine events. */
    private val lock = Any()

    private var nextSessionIdValue: Long = 1
    private var favoriteCounter: Long = 0

    init {
        restoreSettings()
        restoreSessions()
        restoreFavorites()
    }

    // ----------------------------------------------------------- restoration

    private fun restoreSessions() {
        synchronized(lock) {
            val index = storage.loadIndex()
            val preview = storage.loadSettings().previewLines
            for (meta in index.items) {
                metas[meta.id] = meta
                val journal = storage.journalFor(meta.id)
                pipes[meta.id] = SessionPipe(
                    journal = journal,
                    parser = StreamParser(),
                    builder = TranscriptBuilder.replay(journal.readAll(), previewLines = preview),
                )
                nextCommandSeqs[meta.id] = journal.nextCommandSeq() + 1
                attachEngine(meta)
                refreshSession(meta.id)
            }
            nextSessionIdValue =
                maxOf(index.nextSessionId, (metas.keys.maxOrNull() ?: 0L) + 1)
            if (pipes.isEmpty()) {
                createSessionLocked()
            } else {
                _state.update { it.copy(currentSessionId = pipes.keys.first()) }
            }
        }
    }

    private fun restoreFavorites() {
        val favorites = storage.loadFavorites()
        favoriteCounter = favorites.nextFavoriteId
        _state.update { it.copy(favorites = favorites.items) }
    }

    private fun restoreSettings() {
        val settings = storage.loadSettings()
        _state.update {
            it.copy(
                keyRowVisible = settings.keyRowVisible,
                enterSends = settings.enterSends,
                outputFontSizeSp = settings.outputFontSizeSp,
                autoScrollOnSend = settings.autoScrollOnSend,
                previewLines = settings.previewLines,
                bubbleFontSizeSp = settings.bubbleFontSizeSp,
            )
        }
    }

    private fun attachEngine(meta: SessionMeta) {
        val app = getApplication<Application>()
        val engine = NativeShellEngine(
            homeDir = storage.shellHome().absolutePath,
            rcPath = storage.ensureShellRc(readShellAsset(app)).absolutePath,
        )
        if (!engine.start(INITIAL_ROWS, INITIAL_COLS)) {
            // The session still appears in the UI; submissions will no-op.
            _events.tryEmit(UiEvent.ShellStartFailed)
            return
        }
        engines[meta.id] = engine
        // Delivery pipeline: the collector only forwards into an
        // unbounded channel (fast, can never drop), while a batch worker
        // drains everything already queued per iteration - one lock
        // acquisition, one journal append, one refresh per burst.
        val queue = Channel<EngineEvent>(Channel.UNLIMITED)
        rawQueues[meta.id] = queue
        viewModelScope.launch(Dispatchers.Default) {
            engine.events.collect { queue.send(it) }
        }
        engineJobs[meta.id] = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val first = queue.receive()
                val batch = ArrayList<EngineEvent>(8)
                batch.add(first)
                while (true) {
                    val next = queue.tryReceive().getOrNull() ?: break
                    batch.add(next)
                }
                processBatch(meta.id, batch)
            }
        }
    }

    private fun readShellAsset(app: Application): ByteArray =
        runCatching {
            app.assets.open(SHELL_ASSET).use { it.readBytes() }
        }.getOrDefault(ByteArray(0))

    // -------------------------------------------------------- engine events

    private fun processBatch(id: Long, batch: List<EngineEvent>) {
        synchronized(lock) {
            val pipe = pipes[id] ?: return
            var bytes: ByteArray? = null
            var shellExited = false
            for (event in batch) {
                when (event) {
                    is EngineEvent.Output ->
                        bytes = if (bytes == null) event.data else bytes + event.data

                    is EngineEvent.Exited -> shellExited = true
                }
            }

            bytes?.let { data ->
                // Raw bytes hit the journal before parsing: the log stays
                // the single source of truth regardless of parsing rules.
                pipe.journal.append(JournalEvent.OutputArrived(data))
                for (parsed in pipe.parser.feed(data)) {
                    when (parsed) {
                        is StreamEvent.Text -> pipe.builder.applyOutput(parsed.value)

                        is StreamEvent.OutputBegin -> Unit

                        is StreamEvent.CommandEnd -> pipe.builder.applyCommandEnd(parsed.exitCode)
                    }
                }
            }

            if (shellExited) {
                // The shell itself died; its exit code belongs to the shell,
                // not to the last command, so close without one.
                pipe.builder.applyCommandEnd(null)
            }
            refreshSession(id)
        }
    }

    // -------------------------------------------------------------- sessions

    fun newSession() {
        synchronized(lock) { createSessionLocked() }
    }

    private fun createSessionLocked() {
        val app = getApplication<Application>()
        val id = nextSessionIdValue++
        val name = app.getString(R.string.default_session_name, pipes.size + 1)
        val meta = SessionMeta(id = id, name = name)
        metas[id] = meta
        val journal = storage.journalFor(id)
        pipes[id] = SessionPipe(
            journal,
            StreamParser(),
            TranscriptBuilder(previewLines = _state.value.previewLines),
        )
        nextCommandSeqs[id] = journal.nextCommandSeq() + 1
        attachEngine(meta)
        persistIndexLocked()
        refreshSession(id)
        _state.update { it.copy(currentSessionId = id, search = null) }
    }

    fun selectSession(id: Long) = _state.update {
        it.copy(currentSessionId = id, search = null)
    }

    fun requestRename(id: Long) = _state.update { it.copy(renameTargetId = id) }

    fun cancelRename() = _state.update { it.copy(renameTargetId = null) }

    fun renameSession(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutateMeta(id) { it.copy(name = trimmed) }
        _state.update { it.copy(renameTargetId = null) }
    }

    fun togglePin(id: Long) = mutateMeta(id) { it.copy(pinned = !it.pinned) }

    fun requestDelete(id: Long) {
        val running = _state.value.sessions.any { it.id == id && it.running }
        if (running) {
            _state.update { it.copy(deleteTargetId = id) }
        } else {
            performDelete(id)
        }
    }

    fun confirmDelete() {
        val id = _state.value.deleteTargetId ?: return
        _state.update { it.copy(deleteTargetId = null) }
        performDelete(id)
    }

    fun cancelDelete() = _state.update { it.copy(deleteTargetId = null) }

    private fun performDelete(id: Long) {
        synchronized(lock) {
            // Kill first so the reader sees EOF and unwinds naturally;
            // then cancel the pipeline jobs and drop the queue.
            engines.remove(id)?.terminate()
            rawQueues.remove(id)?.cancel()
            engineJobs.remove(id)?.cancel()
            metas.remove(id)
            pipes.remove(id)
            nextCommandSeqs.remove(id)
            storage.deleteSession(id)
            val remaining = pipes.keys.toList()
            val current = _state.value.currentSessionId
            val nextCurrent = when {
                remaining.isEmpty() -> null
                current == id -> nearestNeighborId(remaining, id)

                else -> current?.takeIf { it in remaining }
            }
            if (pipes.isEmpty()) {
                createSessionLocked()
            } else {
                persistIndexLocked()
                _state.update { it.copy(currentSessionId = nextCurrent, search = null) }
            }
            rebuildAllSessions()
        }
    }

    private fun nearestNeighborId(ids: List<Long>, removedId: Long): Long? {
        val index = ids.indexOf(removedId)
        return ids.getOrNull(index + 1) ?: ids.getOrNull(index - 1)
    }

    fun setDisplayMode(mode: DisplayMode) {
        val id = _state.value.currentSessionId ?: return
        mutateMeta(id) { it.copy(displayMode = mode) }
    }

    // ----------------------------------------------------------------- input

    fun updateDraft(text: String) {
        val id = _state.value.currentSessionId ?: return
        mutateMeta(id) { it.copy(draft = text) }
    }

    fun sendDraft() {
        val id = _state.value.currentSessionId ?: return
        val draft = metas[id]?.draft.orEmpty().trimEnd('\n')
        if (draft.isBlank()) return
        submit(id, draft)
        mutateMeta(id) { it.copy(draft = "") }
        persistTransientState()
        jumpIfEnabled()
    }

    fun setEditorExpanded(expanded: Boolean) = _state.update {
        it.copy(editorExpanded = expanded)
    }

    fun sendFromExpandedEditor() {
        sendDraft()
        _state.update { it.copy(editorExpanded = false) }
    }

    /** Runs a favorite or self-executing key payload straight to the shell. */
    fun executeDirectly(command: String) {
        val id = _state.value.currentSessionId ?: return
        val text = command.trimEnd('\n')
        if (text.isBlank()) return
        submit(id, text)
        jumpIfEnabled()
    }

    private fun jumpIfEnabled() {
        if (_state.value.autoScrollOnSend) _jumpToBottom.tryEmit(Unit)
    }

    /** Appends text to the editor draft (favorites: edit before running). */
    fun insertIntoDraft(text: String) {
        val id = _state.value.currentSessionId ?: return
        mutateMeta(id) { it.copy(draft = metas[id]?.draft.orEmpty() + text) }
    }

    private fun submit(id: Long, text: String) {
        val engine = engines[id] ?: return
        var seq = 0L
        synchronized(lock) {
            val pipe = pipes[id] ?: return
            val next = nextCommandSeqs[id] ?: return
            nextCommandSeqs[id] = next + 1
            seq = next
            pipe.journal.append(
                JournalEvent.CommandSubmitted(
                    seq = seq,
                    text = text,
                    timestampMs = System.currentTimeMillis(),
                ),
            )
            pipe.builder.applyCommandSubmitted(seq, text)
            refreshSession(id)
        }
        engine.write((text + "\n").toByteArray(Charsets.UTF_8))
    }

    // ------------------------------------------------------------- favorites

    fun addFavorite(text: String) {
        val trimmed = text.trimEnd('\n')
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            val current = _state.value.favorites
            if (current.none { it.text == trimmed }) {
                val favorite = FavoriteCommand(id = ++favoriteCounter, text = trimmed)
                storage.saveFavorites(
                    FavoritesIndex(
                        items = current + favorite,
                        nextFavoriteId = favoriteCounter + 1,
                    ),
                )
                _state.update { it.copy(favorites = current + favorite) }
            }
        }
    }

    fun updateFavorite(id: Long, text: String) {
        val trimmed = text.trim().trimEnd('\n')
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            val current = _state.value.favorites
            if (current.any { it.id == id && it.text == trimmed }) return
            val updated = current.map { if (it.id == id) it.copy(text = trimmed) else it }
            storage.saveFavorites(
                FavoritesIndex(items = updated, nextFavoriteId = favoriteCounter + 1),
            )
            _state.update { it.copy(favorites = updated) }
        }
    }

    fun deleteFavorite(id: Long) {
        synchronized(lock) {
            val current = _state.value.favorites
            val remaining = current.filterNot { it.id == id }
            storage.saveFavorites(
                FavoritesIndex(items = remaining, nextFavoriteId = favoriteCounter + 1),
            )
            _state.update { it.copy(favorites = remaining) }
        }
    }

    fun setShowFavorites(visible: Boolean) = _state.update {
        it.copy(showFavorites = visible)
    }

    // --------------------------------------------------------------- search

    fun enterSearch() = _state.update { it.copy(search = SearchState()) }

    fun exitSearch() = _state.update { it.copy(search = null) }

    fun updateSearchQuery(query: String) {
        val id = _state.value.currentSessionId ?: return
        val matches = _state.value.sessions.firstOrNull { it.id == id }
            ?.blocks
            ?.mapNotNull { view ->
                val haystack = when (val block = view.block) {
                    is CommandBlock -> block.text
                    is OutputBlock -> block.text
                }
                if (haystack.contains(query, ignoreCase = true)) view.block.id else null
            }
            .orEmpty()
        _state.update { st ->
            st.copy(
                search = (st.search ?: SearchState()).copy(
                    query = query,
                    matchBlockIds = if (query.isBlank()) emptyList() else matches,
                    index = 0,
                ),
            )
        }
        _state.value.search?.matchBlockIds?.firstOrNull()?.let { _scrollRequests.tryEmit(it) }
    }

    fun nextSearchMatch() {
        val search = _state.value.search ?: return
        if (search.matchBlockIds.isEmpty()) return
        val nextIndex = (search.index + 1) % search.matchBlockIds.size
        _state.update { it.copy(search = search.copy(index = nextIndex)) }
        _scrollRequests.tryEmit(search.matchBlockIds[nextIndex])
    }

    // --------------------------------------------------- block interactions

    fun toggleBlockCollapsed(blockId: String) {
        val id = _state.value.currentSessionId ?: return
        synchronized(lock) {
            pipes[id]?.builder?.toggleExpanded(blockId)
            refreshSession(id)
        }
    }

    /** Expands a block without collapsing anything (search navigation). */
    fun revealBlock(blockId: String) {
        val id = _state.value.currentSessionId ?: return
        synchronized(lock) {
            pipes[id]?.builder?.reveal(blockId)
            refreshSession(id)
        }
    }

    // ------------------------------------------------------- key row & misc

    fun toggleStickyCtrl() = _state.update { it.copy(stickyCtrl = !it.stickyCtrl) }

    fun consumeStickyCtrl() = _state.update { it.copy(stickyCtrl = false) }

    fun setKeyRowVisible(visible: Boolean) {
        _state.update { it.copy(keyRowVisible = visible) }
        saveSettings()
    }

    fun setEnterSends(value: Boolean) {
        _state.update { it.copy(enterSends = value) }
        saveSettings()
    }

    fun setOutputFontSize(sizeSp: Int) {
        _state.update { it.copy(outputFontSizeSp = sizeSp.coerceIn(10, 22)) }
        saveSettings()
    }

    fun setPreviewLines(lines: Int) {
        val coerced = lines.coerceIn(1, 10)
        synchronized(lock) {
            _state.update { it.copy(previewLines = coerced) }
            pipes.values.forEach { it.builder.applyPreviewLines(coerced) }
            pipes.keys.toList().forEach { refreshSession(it) }
        }
        saveSettings()
    }

    fun setBubbleFontSize(sizeSp: Int) {
        _state.update { it.copy(bubbleFontSizeSp = sizeSp.coerceIn(12, 24)) }
        saveSettings()
    }

    fun setAutoScrollOnSend(value: Boolean) {
        _state.update { it.copy(autoScrollOnSend = value) }
        saveSettings()
    }

    fun setShowSettings(visible: Boolean) = _state.update {
        it.copy(showSettings = visible)
    }

    private fun saveSettings() {
        val st = _state.value
        storage.saveSettings(
            com.athea.app.data.AtheaSettings(
                keyRowVisible = st.keyRowVisible,
                enterSends = st.enterSends,
                outputFontSizeSp = st.outputFontSizeSp,
                autoScrollOnSend = st.autoScrollOnSend,
                previewLines = st.previewLines,
                bubbleFontSizeSp = st.bubbleFontSizeSp,
            ),
        )
    }

    fun sendDirectText(payload: String) =
        engines[_state.value.currentSessionId]?.write(payload.toByteArray(Charsets.UTF_8))

    fun notifyCopied() = _events.tryEmit(UiEvent.CopiedToClipboard)

    fun showSelectText(text: String) = _state.update {
        it.copy(selectTextPayload = text)
    }

    fun dismissSelectText() = _state.update { it.copy(selectTextPayload = null) }

    // ----------------------------------------------------- terminal sizing

    fun onTranscriptAreaResized(rows: Int, cols: Int) {
        val id = _state.value.currentSessionId ?: return
        engines[id]?.resize(rows.coerceAtLeast(1), cols.coerceAtLeast(1))
    }

    // ---------------------------------------------------------- persistence

    /** Persists metadata (names, drafts, modes); call from onPause too. */
    fun persistTransientState() {
        synchronized(lock) { persistIndexLocked() }
    }

    // ------------------------------------------------------------- internals

    private fun mutateMeta(id: Long, transform: (SessionMeta) -> SessionMeta) {
        synchronized(lock) {
            val current = metas[id] ?: return
            metas[id] = transform(current)
            persistIndexLocked()
            refreshSession(id)
        }
    }

    private fun persistIndexLocked() {
        val items = metas.values.sortedBy { it.id }
        val nextId = maxOf(nextSessionIdValue, (items.maxOfOrNull { it.id } ?: 0L) + 1)
        nextSessionIdValue = nextId
        storage.saveIndex(SessionsIndex(items = items, nextSessionId = nextId))
    }

    private fun refreshSession(id: Long) {
        val pipe = pipes[id] ?: return
        val meta = metas[id] ?: return
        val snapshot = pipe.builder.snapshot(displayRaw = meta.displayMode == DisplayMode.RAW)
        val session = SessionUi(
            id = meta.id,
            name = meta.name,
            pinned = meta.pinned,
            displayMode = meta.displayMode,
            draft = meta.draft,
            blocks = snapshot.blocks,
            running = snapshot.running,
            rawText = snapshot.rawText,
        )
        replaceOrAppend(session)
    }

    private fun rebuildAllSessions() {
        for (id in pipes.keys.toList()) refreshSession(id)
        _state.update { st ->
            st.copy(sessions = st.sessions.filter { it.id in pipes.keys })
        }
    }

    private fun replaceOrAppend(session: SessionUi) {
        _state.update { st ->
            val sessions = ArrayList(st.sessions)
            val index = sessions.indexOfFirst { it.id == session.id }
            if (index >= 0) sessions[index] = session else sessions.add(session)
            st.copy(sessions = sessions)
        }
    }

    override fun onCleared() {
        engines.values.forEach { it.terminate() }
        rawQueues.values.forEach { it.cancel() }
        super.onCleared()
    }

    private companion object {
        const val INITIAL_ROWS = 24
        const val INITIAL_COLS = 80
        const val SHELL_ASSET = "mkshrc"
    }
}
