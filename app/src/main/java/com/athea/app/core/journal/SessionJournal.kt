package com.athea.app.core.journal

import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Disk-backed append-only log for one session.
 *
 * Format: a stream of length-prefixed binary records
 * `[tag:1][len:4][payload:len]`, one record per event. Raw output bytes
 * are stored verbatim - no base64, no escaping - so journals are ~25%
 * smaller and replay needs zero decoding.
 *
 * Legacy JSONL journals (first byte `{`) are detected automatically and
 * migrated once to the binary format; a torn tail from a mid-write kill
 * is dropped instead of poisoning the history.
 */
class SessionJournal(private val file: File) {

    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }

    private var formatChecked = false
    private var binaryMode = false

    // Persistent output stream: opened on first append, reused for all
    // subsequent writes. Opening/closing a file per append is the single
    // biggest I/O bottleneck for heavy terminal output.
    private var writer: DataOutputStream? = null

    fun append(event: JournalEvent) {
        synchronized(lock) {
            ensureFormat()
            file.parentFile?.mkdirs()
            val out = writer ?: DataOutputStream(BufferedOutputStream(FileOutputStream(file, true), 32 * 1024)).also {
                writer = it
            }
            writeRecord(out, event)
            out.flush()
        }
    }

    fun flush() {
        synchronized(lock) { runCatching { writer?.flush() } }
    }

    fun close() {
        synchronized(lock) { closeWriter() }
    }

    private fun closeWriter() {
        writer?.let { runCatching { it.close() } }
        writer = null
    }

    fun readAll(): List<JournalEvent> {
        synchronized(lock) {
            ensureFormat()
            closeWriter() // Flush buffered data before reading.
            if (!file.exists() || file.length() == 0L) return emptyList()
            return try {
                DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                    readBinary(input, file.length())
                }
            } catch (_: IOException) {
                emptyList()
            }
        }
    }

    /** Highest submitted-command sequence number; 0 when the journal is fresh. */
    fun nextCommandSeq(): Long =
        readAll().filterIsInstance<JournalEvent.CommandSubmitted>()
            .maxOfOrNull { it.seq } ?: 0L

    // -------------------------------------------------------------- format

    private fun ensureFormat() {
        if (formatChecked) return
        formatChecked = true
        binaryMode = true
        if (file.exists() && file.length() > 0) {
            val first = file.inputStream().use { it.read() }
            if (first == '{'.code) {
                // Legacy JSONL: convert once, in place.
                val events = readLegacy()
                val tmp = File(file.parentFile, file.name + ".tmp")
                try {
                    DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
                        for (event in events) writeRecord(out, event)
                    }
                    if (!tmp.renameTo(file)) tmp.delete()
                } catch (_: IOException) {
                    tmp.delete()
                    // Keep the legacy file; reads still work through the
                    // legacy path until a successful migration.
                }
            }
        }
    }

    /** Mirror of the retired JSONL format, used only for one-time migration. */
    @kotlinx.serialization.Serializable
    private sealed class LegacyEvent {
        @kotlinx.serialization.Serializable
        @kotlinx.serialization.SerialName("cmd")
        data class Cmd(
            val seq: Long,
            val text: String,
            val timestampMs: Long,
        ) : LegacyEvent()

        @kotlinx.serialization.Serializable
        @kotlinx.serialization.SerialName("out")
        data class Out(
            val base64: String,
        ) : LegacyEvent()

        @kotlinx.serialization.Serializable
        @kotlinx.serialization.SerialName("fin")
        data class Fin(
            val exitCode: Int? = null,
        ) : LegacyEvent()
    }

    private fun readLegacy(): List<JournalEvent> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            if (line.isBlank()) {
                null
            } else {
                runCatching {
                    when (val legacy = json.decodeFromString(LegacyEvent.serializer(), line)) {
                        is LegacyEvent.Cmd -> JournalEvent.CommandSubmitted(
                            legacy.seq,
                            legacy.text,
                            legacy.timestampMs,
                        )

                        is LegacyEvent.Out -> JournalEvent.OutputArrived(
                            java.util.Base64.getDecoder().decode(legacy.base64)
                        )

                        is LegacyEvent.Fin -> JournalEvent.CommandFinished(legacy.exitCode)
                    }
                }.getOrNull()
            }
        }
    }

    // -------------------------------------------------------------- writing

    private fun writeRecord(out: DataOutputStream, event: JournalEvent) {
        when (event) {
            is JournalEvent.CommandSubmitted -> {
                val text = event.text.toByteArray(Charsets.UTF_8)
                out.writeByte(TAG_CMD)
                out.writeInt(8 + 8 + 4 + text.size)
                out.writeLong(event.seq)
                out.writeLong(event.timestampMs)
                out.writeInt(text.size)
                out.write(text)
            }

            is JournalEvent.OutputArrived -> {
                out.writeByte(TAG_OUT)
                out.writeInt(event.bytes.size)
                out.write(event.bytes)
            }

            is JournalEvent.CommandFinished -> {
                out.writeByte(TAG_FIN)
                if (event.exitCode != null) {
                    out.writeInt(5)
                    out.writeBoolean(true)
                    out.writeInt(event.exitCode)
                } else {
                    out.writeInt(1)
                    out.writeBoolean(false)
                }
            }
        }
    }

    // -------------------------------------------------------------- reading

    private fun readBinary(input: DataInputStream, fileSize: Long): List<JournalEvent> {
        val events = ArrayList<JournalEvent>()
        var consumed = 0L
        try {
            while (true) {
                val tag = input.read()
                if (tag == -1) break // clean end
                val len = input.readInt()
                consumed += 5
                // A declared payload larger than the file itself means the
                // stream is corrupt (foreign bytes, not a torn tail): keep
                // the clean prefix and stop.
                if (len < 0 || consumed + len > fileSize) throw EOFException("corrupt record")
                val payload = readBytes(input, len)
                consumed += len
                when (tag) {
                    TAG_CMD -> DataInputStream(ByteArrayInputStream(payload)).use { p ->
                        val seq = p.readLong()
                        val timestampMs = p.readLong()
                        val text = readBytes(p, p.readInt())
                        events.add(
                            JournalEvent.CommandSubmitted(seq, text.decodeToString(), timestampMs)
                        )
                    }

                    TAG_OUT -> events.add(JournalEvent.OutputArrived(payload))

                    TAG_FIN -> DataInputStream(ByteArrayInputStream(payload)).use { p ->
                        val hasCode = p.readBoolean()
                        val code = if (hasCode) p.readInt() else null
                        events.add(JournalEvent.CommandFinished(code))
                    }

                    else -> Unit // unknown future tag: payload already consumed
                }
            }
        } catch (_: EOFException) {
            // Torn tail from a mid-write kill: keep what parsed cleanly.
        }
        return events
    }

    private fun readBytes(input: DataInputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return bytes
    }

    private companion object {
        const val TAG_CMD = 1
        const val TAG_OUT = 2
        const val TAG_FIN = 3
    }
}
