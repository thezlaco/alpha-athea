package com.athea.app.core.journal

import com.athea.app.core.journal.JournalEvent
import com.athea.app.core.journal.JournalEvent.CommandFinished
import com.athea.app.core.journal.JournalEvent.CommandSubmitted
import com.athea.app.core.journal.JournalEvent.OutputArrived
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionJournalTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newJournal(): Pair<SessionJournal, File> {
        val file = File(tmp.root, "sessions/1/journal.log")
        return SessionJournal(file) to file
    }

    @Test
    fun `append and read round trip preserves events`() {
        val (journal, _) = newJournal()
        journal.append(CommandSubmitted(seq = 1, text = "ls -la", timestampMs = 5))
        journal.append(OutputArrived(bytes = "hi".toByteArray()))
        journal.append(CommandFinished(exitCode = 0))

        val events = journal.readAll()
        assertEquals(3, events.size)
        assertEquals(CommandSubmitted(1, "ls -la", 5), events[0])
        assertEquals(OutputArrived("hi".toByteArray()), events[1])
        assertEquals(CommandFinished(0), events[2])
    }

    @Test
    fun `binary payload survives any byte values`() {
        val (journal, _) = newJournal()
        val nasty = ByteArray(256) { it.toByte() } + byteArrayOf(0, 13, 10, -1, -128)
        journal.append(OutputArrived(bytes = nasty))

        assertEquals(OutputArrived(nasty), journal.readAll()[0])
    }

    @Test
    fun `legacy jsonl journals migrate to binary and stay readable`() {
        val (journal, file) = newJournal()
        file.parentFile?.mkdirs()
        file.writeText(
            "{\"type\":\"cmd\",\"seq\":1,\"text\":\"ls\",\"timestampMs\":5}\n" +
                "{\"type\":\"out\",\"base64\":\"aGk=\"}\n" +
                "{\"type\":\"fin\",\"exitCode\":null}\n"
        )

        val events = journal.readAll()
        assertEquals(3, events.size)
        assertEquals(CommandSubmitted(1, "ls", 5), events[0])
        assertEquals(OutputArrived("hi".toByteArray()), events[1])
        assertEquals(CommandFinished(null), events[2])
        assertTrue(file.inputStream().use { it.read() } != '{'.code)

        // New appends continue in binary; the whole file stays readable.
        journal.append(CommandSubmitted(2, "pwd", 9))
        val again = journal.readAll()
        assertEquals(4, again.size)
        assertEquals(CommandSubmitted(2, "pwd", 9), again[3])
    }

    @Test
    fun `torn binary tail is dropped instead of poisoning history`() {
        val (journal, file) = newJournal()
        journal.append(CommandSubmitted(1, "ls", 0))
        journal.readAll() // flush writer to disk
        val good = file.readBytes()
        // Simulate a kill mid-record: append a partial header + payload.
        file.writeBytes(good + byteArrayOf(2) + byteArrayOf(0, 0, 0, 100) + byteArrayOf(1, 2))

        val events = journal.readAll()
        assertEquals(1, events.size)
        assertEquals(CommandSubmitted(1, "ls", 0), events[0])
    }

    @Test
    fun `appended foreign garbage keeps the clean prefix without crashing`() {
        val (journal, file) = newJournal()
        journal.append(CommandSubmitted(1, "ls", 0))
        journal.readAll() // flush writer to disk
        val good = file.readBytes()
        // Foreign bytes (not our framing) must never poison the reader:
        // the clean prefix stays readable, the garbage region is cut off.
        file.writeBytes(good + "{not json}\n".toByteArray() + byteArrayOf(3, 0, 0, 0, 5, 1, 0, 0, 0, 0))

        val events = journal.readAll()
        assertEquals(1, events.size)
        assertEquals(CommandSubmitted(1, "ls", 0), events[0])
    }

    @Test
    fun `next command seq tracks the highest submitted`() {
        val (journal, _) = newJournal()
        assertEquals(0L, journal.nextCommandSeq())
        journal.append(CommandSubmitted(4, "a", 0))
        journal.append(CommandSubmitted(9, "b", 0))
        assertEquals(9L, journal.nextCommandSeq())
    }

    @Test
    fun `missing file reads as empty`() {
        val (journal, _) = newJournal()
        assertEquals(emptyList<JournalEvent>(), journal.readAll())
    }
}
