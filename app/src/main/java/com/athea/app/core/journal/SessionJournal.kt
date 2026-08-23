package com.athea.app.core.journal

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Disk-backed append-only log for one session: one JSON line per event.
 * Corrupt trailing lines (crash mid-write) are skipped on read instead of
 * poisoning the whole history.
 */
class SessionJournal(private val file: File) {

    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }

    fun append(event: JournalEvent) {
        synchronized(lock) {
            file.parentFile?.mkdirs()
            val line = json.encodeToString(JournalEvent.serializer(), event)
            file.appendText(line + "\n")
        }
    }

    fun readAll(): List<JournalEvent> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            if (line.isBlank()) {
                null
            } else {
                runCatching {
                    json.decodeFromString(JournalEvent.serializer(), line)
                }.getOrNull()
            }
        }
    }

    /** Highest submitted-command sequence number; 0 when the journal is fresh. */
    fun nextCommandSeq(): Long =
        readAll().filterIsInstance<JournalEvent.CommandSubmitted>()
            .maxOfOrNull { it.seq } ?: 0L
}
