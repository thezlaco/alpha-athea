package com.athea.app.core.journal

import com.athea.app.core.journal.JournalEvent
import com.athea.app.core.journal.JournalEvent.CommandFinished
import com.athea.app.core.journal.JournalEvent.CommandSubmitted
import com.athea.app.core.journal.JournalEvent.OutputArrived
import org.junit.Assert.assertEquals
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
        journal.append(OutputArrived(base64 = "aGk="))
        journal.append(CommandFinished(exitCode = 0))

        val events = journal.readAll()
        assertEquals(3, events.size)
        assertEquals(CommandSubmitted(1, "ls -la", 5), events[0])
        assertEquals(OutputArrived("aGk="), events[1])
        assertEquals(CommandFinished(0), events[2])
    }

    @Test
    fun `corrupt lines are skipped instead of poisoning history`() {
        val (journal, file) = newJournal()
        journal.append(CommandSubmitted(1, "ls", 0))

        file.appendText("{not json}\n")
        journal.append(CommandFinished(0))

        val events = journal.readAll()
        assertEquals(2, events.size)
        assertEquals(CommandFinished(0), events[1])
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
