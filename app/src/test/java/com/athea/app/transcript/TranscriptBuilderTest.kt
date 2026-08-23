package com.athea.app.transcript

import com.athea.app.core.journal.JournalEvent
import com.athea.app.core.model.CommandBlock
import com.athea.app.core.model.OutputBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class TranscriptBuilderTest {

    private fun b64(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    private fun outputEvent(text: String) =
        JournalEvent.OutputArrived(base64 = b64(text))

    @Test
    fun `command and marked output produce closed block with exit code`() {
        val builder = TranscriptBuilder.replay(
            listOf(
                JournalEvent.CommandSubmitted(1, "ls", 0),
                outputEvent("\u001B]133;C\u0007total\n"),
                JournalEvent.CommandFinished(0),
            ),
        )

        val snapshot = builder.snapshot(displayRaw = false)
        assertEquals(2, snapshot.blocks.size)
        assertFalse(snapshot.running)

        val command = snapshot.blocks[0].block as CommandBlock
        assertEquals("cmd-1", command.id)
        assertEquals("ls", command.text)
        assertFalse(snapshot.blocks[0].collapsed)

        val output = snapshot.blocks[1].block as OutputBlock
        assertEquals("total\n", output.text)
        assertEquals(0, output.exitCode)
        assertFalse(output.running)
        assertFalse(snapshot.blocks[1].collapsed)
    }

    @Test
    fun `output without marks closes heuristically on next command`() {
        val builder = TranscriptBuilder.replay(
            listOf(
                JournalEvent.CommandSubmitted(1, "a", 0),
                outputEvent("out1\n"),
                JournalEvent.CommandSubmitted(2, "b", 0),
            ),
        )

        val snapshot = builder.snapshot(displayRaw = false)
        val firstOutput = snapshot.blocks[1].block as OutputBlock
        assertFalse(firstOutput.running)
        assertNull(firstOutput.exitCode)
        assertTrue(snapshot.blocks[1].collapsed)
    }

    @Test
    fun `older outputs collapse when a new command arrives`() {
        val builder = TranscriptBuilder()
        builder.applyCommandSubmitted(1, "a")
        builder.applyOutput("first\n")
        builder.applyCommandEnd(0)

        var snapshot = builder.snapshot(false)
        assertFalse(snapshot.blocks[1].collapsed) // newest → expanded

        builder.applyCommandSubmitted(2, "b")
        snapshot = builder.snapshot(false)
        assertTrue(snapshot.blocks[1].collapsed) // older → auto-collapsed
    }

    @Test
    fun `manual toggle overrides the default`() {
        val builder = TranscriptBuilder()
        val longCommand = (1..5).joinToString("\n") { "line$it" }
        builder.applyCommandSubmitted(1, longCommand)

        var view = builder.snapshot(false).blocks.first()
        assertTrue(view.collapsed) // > PREVIEW_LINES collapses by default

        builder.toggleExpanded(view.block.id)
        view = builder.snapshot(false).blocks.first()
        assertFalse(view.collapsed)

        builder.toggleExpanded(view.block.id)
        view = builder.snapshot(false).blocks.first()
        assertTrue(view.collapsed)
    }

    @Test
    fun `reveal forces a block open`() {
        val builder = TranscriptBuilder()
        builder.applyCommandSubmitted(1, "a")
        builder.applyOutput("x")
        builder.applyCommandSubmitted(2, "b")

        assertTrue(builder.snapshot(false).blocks[1].collapsed)

        builder.reveal("out-1")
        val view = builder.snapshot(false).blocks[1]
        assertFalse(view.collapsed)
    }

    @Test
    fun `raw projection respects the cap`() {
        val builder = TranscriptBuilder()
        builder.applyCommandSubmitted(1, "big")
        repeat(15) { builder.applyOutput("A".repeat(100_000)) }

        val rawText = builder.snapshot(displayRaw = true).rawText
        // Chunked eviction lands in [cap, cap + slack): never above the
        // hard ceiling, never a character below the floor.
        assertTrue(rawText.length >= TranscriptBuilder.DEFAULT_RAW_CAP)
        assertTrue(rawText.length <= TranscriptBuilder.DEFAULT_RAW_CAP + (1 shl 16))
        assertEquals("", builder.snapshot(displayRaw = false).rawText)
    }

    @Test
    fun `blank stray output never creates a ghost block`() {
        val builder = TranscriptBuilder()
        builder.applyOutput("\n")
        builder.applyOutput("   \n")

        val snapshot = builder.snapshot(false)
        assertTrue(snapshot.blocks.isEmpty())
        assertFalse(snapshot.running)
    }

    @Test
    fun `replay from journal equals live building`() {
        val live = TranscriptBuilder()
        live.applyCommandSubmitted(7, "echo hi")
        live.applyOutput("hi\n")
        live.applyCommandEnd(0)

        val events = listOf(
            JournalEvent.CommandSubmitted(7, "echo hi", 42),
            outputEvent("hi\n"),
            JournalEvent.CommandFinished(0),
        )
        val replayed = TranscriptBuilder.replay(events)

        assertEquals(
            live.snapshot(true).copy(rawText = ""),
            replayed.snapshot(true).copy(rawText = ""),
        )
    }
}
