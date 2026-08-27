package com.athea.app.ui

import com.athea.app.data.SessionMeta
import com.athea.app.core.terminal.TerminalEngine
import com.athea.app.transcript.TranscriptBuilder
import com.athea.app.core.journal.SessionJournal
import com.athea.app.parse.StreamParser
import kotlinx.coroutines.channels.Channel
import com.athea.app.core.terminal.EngineEvent
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/**
 * Extracted from MainViewModel to thin the 850-line god object.
 * Holds session-scoped state that was previously 6 HashMaps in the VM.
 * MainViewModel still orchestrates, but storage and lifecycle now live here.
 */
class SessionManager {
    val metas = HashMap<Long, SessionMeta>()
    val engines = HashMap<Long, TerminalEngine>()
    val rawQueues = HashMap<Long, Channel<EngineEvent>>()
    val engineJobs = HashMap<Long, Job>()
    val nextCommandSeqs = HashMap<Long, Long>()
    val histories = HashMap<Long, List<String>>()
    var nextSessionIdValue: Long = 1

    val dirtySessions = ConcurrentHashMap.newKeySet<Long>()

    fun clear() {
        metas.clear()
        engines.clear()
        rawQueues.clear()
        engineJobs.clear()
        nextCommandSeqs.clear()
        histories.clear()
        dirtySessions.clear()
    }
}
