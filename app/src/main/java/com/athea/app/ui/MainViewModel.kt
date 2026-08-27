package com.athea.app.ui

import android.app.Application
import android.content.Context
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
import com.athea.app.parse.applyTo
import com.athea.app.transcript.BlockView
import com.athea.app.transcript.TranscriptBuilder
import com.athea.app.util.dropOldestSharedFlow
import com.athea.app.util.normalizeCommand
import com.athea.app.util.shellEval
import com.athea.app.util.shellQuote
import com.athea.app.util.trimCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

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
    val rawStream: Boolean = false,
    val autocompleteEnabled: Boolean = true,
    val pinchZoomEnabled: Boolean = true,
    val previewLines: Int = com.athea.app.core.model.PREVIEW_LINES,
    val bubbleFontSizeSp: Int = 16,
    val showSettings: Boolean = false,
    val showFavorites: Boolean = false,
    val showAttachChooser: Boolean = false,
    val showKeyBuilder: Boolean = false,
    val renameTargetId: Long? = null,
    val deleteTargetId: Long? = null,
    val selectTextPayload: String? = null,
    val suggestion: String? = null,
    val customKeys: List<com.athea.app.data.CustomKey> = emptyList(),
    val attachments: List<com.athea.app.core.model.Attachment> = emptyList(),
)

sealed interface UiEvent {
    data object ShellStartFailed : UiEvent
    data class ShellExited(val exitCode: Int) : UiEvent
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

    private val _events = dropOldestSharedFlow<UiEvent>(replay = 1, extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    /** One-shot requests to scroll the transcript to a block (search jumps). */
    private val _scrollRequests = dropOldestSharedFlow<String>(extraBufferCapacity = 8)
    val scrollRequests: SharedFlow<String> = _scrollRequests.asSharedFlow()

    /** One-shot requests to force the transcript to its very bottom. */
    private val _jumpToBottom = dropOldestSharedFlow<Unit>(extraBufferCapacity = 4)
    val jumpToBottom: SharedFlow<Unit> = _jumpToBottom.asSharedFlow()

    private val storage = AtheaStorage(application.filesDir)

    private val metas = LinkedHashMap<Long, SessionMeta>()
    private val pipes = LinkedHashMap<Long, SessionPipe>()
    private val engines = HashMap<Long, TerminalEngine>()
    private val engineJobs = HashMap<Long, Job>()
    private val rawQueues = HashMap<Long, Channel<EngineEvent>>()
    private val nextCommandSeqs = HashMap<Long, Long>()

    /** Submitted command texts per session, newest last (autocomplete source). */
    private val histories = HashMap<Long, List<String>>()

    /** Guards every multi-step session mutation against concurrent engine events. */
    private val lock = Any()

    /** Sessions with pending UI refreshes, drained at a fixed rate. */
    private val dirtySessions = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    private var nextSessionIdValue: Long = 1
    private var favoriteCounter: Long = 0

    // Extracted managers — thin the 850-line god object (audit 2)
    @Suppress("unused") private val sessionManager = SessionManager()
    private val searchManager = SearchManager()

    init {
        restoreSettings()
        restoreSessions()
        restoreFavorites()
        restoreKeys()
        // Throttled UI refresh: heavy output produces many batches per
        // second; without throttling each one triggers a full recompose.
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                kotlinx.coroutines.delay(100)
                if (dirtySessions.isEmpty()) continue
                val toRefresh = dirtySessions.toList()
                dirtySessions.removeAll(toRefresh)
                synchronized(lock) {
                    for (id in toRefresh) refreshSession(id)
                }
            }
        }
    }

    // ----------------------------------------------------------- restoration

    private fun restoreSessions() {
        // I/O outside lock — don't block keystrokes while replaying.
        val index = storage.loadIndex()
        val settings = storage.loadSettings()
        val preview = settings.previewLines
        val globalMode = if (settings.rawStream) DisplayMode.RAW else DisplayMode.BLOCKS
        val replayed = index.items.map { meta ->
            val journal = storage.journalFor(meta.id)
            val events = journal.readAll()
            val pipe = SessionPipe(
                journal = journal,
                parser = StreamParser(),
                builder = TranscriptBuilder.replay(events, previewLines = preview),
            )
            val history = events.filterIsInstance<JournalEvent.CommandSubmitted>().map { it.text }
            val nextSeq = (events.filterIsInstance<JournalEvent.CommandSubmitted>().maxOfOrNull { it.seq } ?: 0L) + 1
            Triple(meta.copy(displayMode = globalMode), pipe, history to nextSeq)
        }
        synchronized(lock) {
            for ((meta, pipe, historySeq) in replayed) {
                metas[meta.id] = meta
                pipes[meta.id] = pipe
                histories[meta.id] = historySeq.first
                nextCommandSeqs[meta.id] = historySeq.second
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

    private fun restoreKeys() {
        val keys = storage.loadKeys()
        _state.update { it.copy(customKeys = keys.items) }
    }

    /** Replaces the whole key row (used by the settings editor). */
    fun setCustomKeys(keys: List<com.athea.app.data.CustomKey>) {
        _state.update { it.copy(customKeys = keys) }
        storage.saveKeys(com.athea.app.data.KeysIndex(items = keys))
    }

    fun resetKeysToDefaults() {
        setCustomKeys(emptyList())
    }

    /**
     * Stages a picked file: bytes land in the app attachment folder and a
     * ready-to-edit command is attached to the next submission.
     */
    fun importAttachment(
        uri: android.net.Uri,
        action: String,
        context: Context,
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val resolver = context.contentResolver
                val name = runCatching {
                    resolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                    }
                }.getOrNull() ?: "file"

                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val dir = File(context.filesDir, "attachments").apply { mkdirs() }
                val target = File(dir, "${System.currentTimeMillis()}_$safeName")
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("cannot open $uri")

                val quoted = target.absolutePath.shellQuote()
                val command = when (action) {
                    "move" -> "mv $quoted ./"
                    "link" -> "ln -s $quoted ./'$safeName'"
                    "name" -> quoted
                    else -> "cp $quoted ./"
                }
                com.athea.app.util.AtheaLog.log("attach", "imported $safeName action=$action")
                _state.update {
                    it.copy(
                        attachments = it.attachments + com.athea.app.core.model.Attachment(
                            name = safeName,
                            command = command,
                        )
                    )
                }
            } catch (e: Exception) {
                com.athea.app.util.AtheaLog.error("attach", "import failed", e)
            }
        }
    }

    fun removeAttachment(attachment: com.athea.app.core.model.Attachment) {
        _state.update { it.copy(attachments = it.attachments - attachment) }
    }

    private fun restoreSettings() {
        val settings = storage.loadSettings()
        _state.update {
            it.copy(
                keyRowVisible = settings.keyRowVisible,
                enterSends = settings.enterSends,
                outputFontSizeSp = settings.outputFontSizeSp,
                autoScrollOnSend = settings.autoScrollOnSend,
                rawStream = settings.rawStream,
                autocompleteEnabled = settings.autocompleteEnabled,
                pinchZoomEnabled = settings.pinchZoomEnabled,
                previewLines = settings.previewLines,
                bubbleFontSizeSp = settings.bubbleFontSizeSp,
            )
        }
    }

    private fun attachEngine(meta: SessionMeta) {
        val app = getApplication<Application>()
        val shellPath = try { storage.loadSettings().shellPath } catch (_: Exception) { "/system/bin/sh" }
        val engine: com.athea.app.core.terminal.TerminalEngine = when {
            shellPath.startsWith("ssh://") -> com.athea.app.engine.SshShellEngine(shellPath)
            else -> NativeShellEngine(
                homeDir = storage.shellHome().absolutePath,
                rcPath = storage.ensureShellRc(readShellAsset(app)).absolutePath,
                shellPath = shellPath,
            )
        }
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
                // One dead event must never kill the pipeline forever.
                try {
                    processBatch(meta.id, batch)
                } catch (e: Exception) {
                    com.athea.app.util.AtheaLog.error("pipeline", "processBatch failed", e)
                }
            }
        }
    }

    private fun readShellAsset(app: Application): ByteArray =
        runCatching {
            app.assets.open(SHELL_ASSET).use { it.readBytes() }
        }.getOrDefault(ByteArray(0))

    // -------------------------------------------------------- engine events

    private fun processBatch(id: Long, batch: List<EngineEvent>) {
        // Capture per-session reference without lock: the pipe is only
        // removed in performDelete, which cancels this worker first.
        val pipe = pipes[id] ?: return

        // Phase 1: extract data from batch — no lock, pure CPU.
        // ByteArrayOutputStream avoids O(n²) intermediate array copies.
        var shellExited = false
        var shellExitCode: Int? = null
        val byteBuffer = java.io.ByteArrayOutputStream()
        for (event in batch) {
            when (event) {
                is EngineEvent.Output -> byteBuffer.write(event.data)

                is EngineEvent.Exited -> {
                    shellExited = true
                    shellExitCode = event.exitCode
                }
            }
        }
        val bytes: ByteArray? = if (byteBuffer.size() > 0) byteBuffer.toByteArray() else null

        // Phase 2: journal write + parse — no lock. Journal is per-session
        // (single writer), parser is per-session (single feeder). Disk I/O
        // and text processing MUST NOT hold the lock that the main thread
        // needs for every keystroke.
        var parsedEvents: List<StreamEvent> = emptyList()
        if (bytes != null) {
            pipe.journal.append(JournalEvent.OutputArrived(bytes))
            parsedEvents = pipe.parser.feed(bytes)
        }

        // Phase 3: apply to builder — brief lock. StringBuilder appends
        // are O(1), so this block takes microseconds, not milliseconds.
        synchronized(lock) {
            if (pipes[id] == null) return // deleted while parsing
            for (parsed in parsedEvents) parsed.applyTo(pipe.builder)

            if (shellExited) {
                pipe.builder.applyCommandEnd(null)
                _events.tryEmit(UiEvent.ShellExited(shellExitCode ?: -1))
            }
            dirtySessions.add(id)
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
        val meta = SessionMeta(
            id = id,
            name = name,
            displayMode = if (_state.value.rawStream) DisplayMode.RAW else DisplayMode.BLOCKS,
        )
        metas[id] = meta
        histories[id] = emptyList()
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
            pipes.remove(id)?.journal?.close()
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
        // In-memory only: persisting on every keystroke means a disk write
        // per character (visible as paste jank). Drafts flush on pause,
        // send and session switch instead.
        mutateMeta(id, persist = false) { it.copy(draft = text) }
        updateSuggestion(id, text)
    }

    fun sendDraft() {
        val id = _state.value.currentSessionId ?: return
        val draft = metas[id]?.draft.orEmpty().trimCommand()
        val attachments = _state.value.attachments
        if (draft.isBlank() && attachments.isEmpty()) return
        // Staged files go first, each as its own command.
        for (attachment in attachments) {
            submit(id, attachment.command)
        }
        if (draft.isNotBlank()) {
            submit(id, draft)
        }
        if (attachments.isNotEmpty()) {
            _state.update { it.copy(attachments = emptyList()) }
        }
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
        val text = command.trimCommand()
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
        val engine = engines[id]
        if (engine == null) {
            com.athea.app.util.AtheaLog.error("submit", "no engine for session $id")
            return
        }
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
            val hist = histories[id]?.toMutableList() ?: mutableListOf()
            hist.add(text)
            if (hist.size > 500) hist.removeAt(0)
            histories[id] = hist
            refreshSession(id)
        }
        val payload = text.shellEval()
        com.athea.app.util.AtheaLog.log("submit", "seq=$seq payloadSize=${payload.length}")
        engine.write(payload.toByteArray(Charsets.UTF_8))
    }

    // ------------------------------------------------------------- favorites

    fun addFavorite(text: String) {
        val trimmed = text.trimCommand()
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
        val trimmed = text.normalizeCommand()
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

    fun setShowAttachChooser(visible: Boolean) = _state.update {
        it.copy(showAttachChooser = visible)
    }

    fun setShowKeyBuilder(visible: Boolean) = _state.update {
        it.copy(showKeyBuilder = visible)
    }

    fun addCustomKey(key: com.athea.app.data.CustomKey) {
        setCustomKeys(_state.value.customKeys + key)
    }

    // --------------------------------------------------------------- search

    fun enterSearch() = _state.update { it.copy(search = SearchState()) }

    fun exitSearch() = _state.update { it.copy(search = null) }

    fun updateSearchQuery(query: String) {
        val id = _state.value.currentSessionId ?: return
        val needle = query.lowercase()
        val matches = _state.value.sessions.firstOrNull { it.id == id }
            ?.blocks
            ?.mapNotNull { view ->
                val haystack = when (val block = view.block) {
                    is CommandBlock -> block.text
                    is OutputBlock -> block.text
                }
                if (haystack.lowercase().contains(needle)) view.block.id else null
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

    fun setAutocompleteEnabled(value: Boolean) {
        _state.update {
            it.copy(autocompleteEnabled = value, suggestion = if (value) it.suggestion else null)
        }
        saveSettings()
    }

    fun setPinchZoomEnabled(value: Boolean) {
        _state.update { it.copy(pinchZoomEnabled = value) }
        saveSettings()
    }

    /** Pinch on the transcript: scale the output font live. */
    fun onOutputFontZoom(factor: Float) {
        if (!_state.value.pinchZoomEnabled) return
        val current = _state.value.outputFontSizeSp
        val next = (current * factor).toInt().coerceIn(10, 22)
        if (next != current) {
            _state.update { it.copy(outputFontSizeSp = next) }
            saveSettings()
        }
    }

    /**
     * Ghost-text suggestion: the newest history command that extends the
     * current draft, zsh-autosuggestions style.
     */
    private fun updateSuggestion(id: Long, draft: String) {
        val enabled = _state.value.autocompleteEnabled
        val suggestion = if (!enabled || draft.isEmpty()) {
            null
        } else {
            histories[id]
                ?.lastOrNull { it != draft && it.startsWith(draft) }
        }
        _state.update { it.copy(suggestion = suggestion) }
    }

    /** Tab with a visible suggestion: take the whole command. */
    fun acceptSuggestion() {
        val id = _state.value.currentSessionId ?: return
        val suggestion = _state.value.suggestion ?: return
        mutateMeta(id) { it.copy(draft = suggestion) }
        _state.update { it.copy(suggestion = null) }
    }

    fun setAutoScrollOnSend(value: Boolean) {
        _state.update { it.copy(autoScrollOnSend = value) }
        saveSettings()
    }

    /** Global transcript view: raw stream instead of chat blocks. */
    fun setRawStream(enabled: Boolean) {
        val mode = if (enabled) DisplayMode.RAW else DisplayMode.BLOCKS
        synchronized(lock) {
            _state.update { it.copy(rawStream = enabled) }
            for (id in metas.keys.toList()) {
                metas[id]?.let { metas[id] = it.copy(displayMode = mode) }
            }
            persistIndexLocked()
            for (id in pipes.keys.toList()) refreshSession(id)
        }
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
                rawStream = st.rawStream,
                previewLines = st.previewLines,
                bubbleFontSizeSp = st.bubbleFontSizeSp,
                autocompleteEnabled = st.autocompleteEnabled,
                pinchZoomEnabled = st.pinchZoomEnabled,
            ),
        )
    }

    fun sendDirectText(payload: String) =
        engines[_state.value.currentSessionId]?.write(payload.toByteArray(Charsets.UTF_8))

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

    private fun mutateMeta(id: Long, persist: Boolean = true, transform: (SessionMeta) -> SessionMeta) {
        synchronized(lock) {
            val current = metas[id] ?: return
            metas[id] = transform(current)
            if (persist) persistIndexLocked()
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
        pipes.values.forEach { it.journal.close() }
        super.onCleared()
    }

    private companion object {
        const val INITIAL_ROWS = 24
        const val INITIAL_COLS = 80
        const val SHELL_ASSET = "mkshrc"
    }

    class Factory(private val app: Application, private val container: com.athea.app.di.AppContainer? = null) :
        androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(app) as T
        }
    }
}
