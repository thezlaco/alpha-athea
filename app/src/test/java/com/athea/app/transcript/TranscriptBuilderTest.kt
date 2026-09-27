package com.athea.app.transcript

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.athea.app.core.journal.JournalEvent
import com.athea.app.core.model.CommandBlock
import com.athea.app.core.model.OutputBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptBuilderTest {

    private fun outputEvent(text: String) =
        JournalEvent.OutputArrived(bytes = text.toByteArray(Charsets.UTF_8))

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
    fun `output without marks stays open across the next command`() {
        val builder = TranscriptBuilder.replay(
            listOf(
                JournalEvent.CommandSubmitted(1, "a", 0),
                outputEvent("out1\n"),
                JournalEvent.CommandSubmitted(2, "b", 0),
            ),
        )

        val snapshot = builder.snapshot(displayRaw = false)
        val firstOutput = snapshot.blocks[1].block as OutputBlock
        // No OSC 133;D ever arrived, so the honest state is "still running":
        // force-closing it here is what used to drop the real exit code of
        // commands that finished after the next one was submitted.
        assertTrue(firstOutput.running)
        assertTrue(snapshot.running)
        assertNull(firstOutput.exitCode)
        assertFalse(snapshot.blocks[1].collapsed)
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
        builder.applyCommandEnd(0)
        builder.applyCommandSubmitted(2, "b")

        assertTrue(builder.snapshot(false).blocks[1].collapsed)

        builder.reveal("out-1")
        val view = builder.snapshot(false).blocks[1]
        assertFalse(view.collapsed)
    }

    @Test
    fun `command submitted while another runs keeps output on the running block`() {
        val builder = TranscriptBuilder()
        builder.applyCommandSubmitted(1, "sleep 100")
        builder.applyOutput("started\n")
        // Typed while the first command is still executing: the shell buffers
        // these bytes, the bubble shows immediately, the first command is not
        // cancelled.
        builder.applyCommandSubmitted(2, "ls")
        builder.applyOutput("still sleeping\n")
        builder.applyCommandEnd(0)
        builder.applyOutput("file-a\n")

        val blocks = builder.snapshot(false).blocks.map { it.block }
        assertEquals(4, blocks.size)
        assertEquals("cmd-1", blocks[0].id)
        assertEquals("out-1", blocks[1].id)
        assertEquals("cmd-2", blocks[2].id)
        assertEquals("out-2", blocks[3].id)

        val firstOutput = blocks[1] as OutputBlock
        assertEquals("started\nstill sleeping\n", firstOutput.text)
        // The whole point: the late exit code is not thrown away.
        assertEquals(0, firstOutput.exitCode)

        val secondOutput = blocks[3] as OutputBlock
        assertEquals("file-a\n", secondOutput.text)
    }

    @Test
    fun `long running output keeps its colors after trimming`() {
        val builder = TranscriptBuilder()
        builder.applyCommandSubmitted(1, "flood")
        val red = Color(0xFFFF0000)
        val chunk = AnnotatedString(
            text = "x".repeat(700_000),
            spanStyles = listOf(
                AnnotatedString.Range(SpanStyle(color = red), 0, 700_000),
            ),
        )
        builder.applyOutput(chunk)
        builder.applyOutput(chunk) // 1.4M chars, over MAX_RUNNING_CHARS

        val block = builder.snapshot(false).blocks.last().block as OutputBlock
        assertTrue(block.running)
        assertTrue(
            "trimming the running buffer must not drop colour spans",
            block.annotated.spanStyles.isNotEmpty(),
        )
        assertEquals(red, block.annotated.spanStyles.first().item.color)
    }

    @Test
    fun `raw projection respects the render cap`() {
        val builder = TranscriptBuilder()
        builder.applyCommandSubmitted(1, "big")
        repeat(15) { builder.applyOutput("A".repeat(100_000)) }

        val rawText = builder.snapshot(displayRaw = true).rawText
        assertTrue(rawText.length <= TranscriptBuilder.RAW_RENDER_CAP)
        assertTrue(rawText.isNotEmpty())
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
