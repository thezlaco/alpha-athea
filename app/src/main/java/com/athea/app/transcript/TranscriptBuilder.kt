package com.athea.app.transcript

import com.athea.app.core.journal.JournalEvent
import com.athea.app.core.model.Block
import com.athea.app.core.model.CommandBlock
import com.athea.app.core.model.OutputBlock
import com.athea.app.core.model.PREVIEW_LINES
import com.athea.app.parse.StreamEvent
import com.athea.app.parse.StreamParser
import java.util.Base64

/** A block plus its presentation-time collapse state. */
data class BlockView(
    val block: Block,
    val collapsed: Boolean,
)

/** Immutable view of what the transcript currently looks like. */
data class TranscriptSnapshot(
    val blocks: List<BlockView>,
    val running: Boolean,
    val rawText: String,
)

/**
 * Builds the block projection and the raw-stream projection from journal
 * events. Pure Kotlin and deterministic: replaying the same events always
 * produces the same transcript, which is exactly how sessions survive
 * application restarts.
 *
 * Collapse rules (the manual override always wins):
 *  - a command bubble collapses when it exceeds [PREVIEW_LINES] lines;
 *  - an output block stays open while it runs or is the newest output;
 *    older outputs auto-collapse when the next command is submitted.
 */
class TranscriptBuilder(
    private val rawCapChars: Int = DEFAULT_RAW_CAP,
    previewLines: Int = PREVIEW_LINES,
) {

    private val blocks = ArrayList<Block>()
    private val expandedOverrides = HashMap<String, Boolean>()
    private var runningOutputId: String? = null
    private var outputCounter = 0
    private val raw = StringBuilder()

    /** Lines shown in a collapsed command bubble; live-tunable in settings. */
    @Volatile var previewLines: Int = previewLines
        private set

    fun applyPreviewLines(lines: Int) {
        previewLines = lines.coerceIn(1, 10)
    }

    // ---------------------------------------------------------------- input

    fun applyCommandSubmitted(seq: Long, text: String) {
        // Heuristic boundary for environments without marks: whatever was
        // still running is closed silently before the next command starts.
        finishRunning(null)
        blocks.filterIsInstance<OutputBlock>().forEach { expandedOverrides.remove(it.id) }
        blocks.add(CommandBlock(id = cmdId(seq), text = text))
        appendRaw(text + "\n")
    }

    fun applyOutput(text: String) {
        if (text.isEmpty()) return
        // Stray whitespace with no running command would create a ghost
        // one-line block; real output always carries content.
        if (text.isBlank() && runningOutputId == null) return
        val id = runningOutputId
        if (id == null) {
            val newId = nextOutputId()
            blocks.add(OutputBlock(id = newId, text = text, running = true))
            runningOutputId = newId
        } else {
            val index = indexOfBlock(id)
            if (index >= 0) {
                val current = blocks[index] as OutputBlock
                blocks[index] = current.copy(text = current.text + text)
            }
        }
        appendRaw(text)
    }

    fun applyCommandEnd(exitCode: Int?) {
        finishRunning(exitCode)
    }

    // ---------------------------------------------------------- interaction

    /** Flips the manual expand/collapse override for one block. */
    fun toggleExpanded(blockId: String) {
        val index = indexOfBlock(blockId)
        if (index < 0) return
        val current = computeCollapsed(blocks[index], expandedOutputId())
        expandedOverrides[blockId] = !current
    }

    /** Forces one block open (search navigation), touching nothing else. */
    fun reveal(blockId: String) {
        if (indexOfBlock(blockId) >= 0) {
            expandedOverrides[blockId] = false
        }
    }

    // ------------------------------------------------------------- snapshot

    fun snapshot(displayRaw: Boolean): TranscriptSnapshot {
        val expandedOutput = expandedOutputId()
        val views = ArrayList<BlockView>(blocks.size)
        for (block in blocks) {
            views.add(BlockView(block, computeCollapsed(block, expandedOutput)))
        }
        return TranscriptSnapshot(
            blocks = views,
            running = runningOutputId != null,
            rawText = if (displayRaw) raw.toString() else "",
        )
    }

    private fun computeCollapsed(block: Block, expandedOutputId: String?): Boolean {
        expandedOverrides[block.id]?.let { return it }
        return when (block) {
            is CommandBlock -> block.text.lines().size > previewLines
            is OutputBlock -> !(block.running || block.id == expandedOutputId)
        }
    }

    // -------------------------------------------------------------- helpers

    private fun finishRunning(exitCode: Int?) {
        val id = runningOutputId ?: return
        runningOutputId = null
        val index = indexOfBlock(id)
        if (index >= 0) {
            val current = blocks[index] as OutputBlock
            blocks[index] = current.copy(running = false, exitCode = exitCode)
        }
    }

    private fun indexOfBlock(id: String): Int =
        blocks.indexOfFirst { it.id == id }

    private fun nextOutputId(): String = "out-${++outputCounter}"

    /**
     * The output stays expanded only while it is the newest transcript
     * element. A newly submitted command lands after it, so every older
     * answer collapses automatically without extra bookkeeping.
     */
    private fun expandedOutputId(): String? {
        val lastCommandIndex = blocks.indexOfLast { it is CommandBlock }
        val lastOutputIndex = blocks.indexOfLast { it is OutputBlock }
        return if (lastOutputIndex > lastCommandIndex) {
            blocks[lastOutputIndex].id
        } else {
            null
        }
    }

    private fun appendRaw(text: String) {
        raw.append(text)
        if (raw.length > rawCapChars + RAW_TRIM_SLACK) {
            raw.delete(0, raw.length - rawCapChars)
        }
    }

    companion object {
        const val DEFAULT_RAW_CAP = 1 shl 20
        private const val RAW_TRIM_SLACK = 1 shl 16

        fun cmdId(seq: Long): String = "cmd-$seq"

        /**
         * Rebuilds a builder from persisted journal events. Raw output is
         * routed through a fresh parser so stored bytes are interpreted
         * with the current parsing rules.
         */
        fun replay(
            events: List<JournalEvent>,
            rawCapChars: Int = DEFAULT_RAW_CAP,
            previewLines: Int = PREVIEW_LINES,
        ): TranscriptBuilder {
            val builder = TranscriptBuilder(rawCapChars, previewLines)
            val parser = StreamParser()
            for (event in events) {
                when (event) {
                    is JournalEvent.CommandSubmitted ->
                        builder.applyCommandSubmitted(event.seq, event.text)

                    is JournalEvent.OutputArrived ->
                        runCatching { Base64.getDecoder().decode(event.base64) }
                            .getOrNull()
                            ?.let(parser::feed)
                            ?.forEach { parsed ->
                                when (parsed) {
                                    is StreamEvent.Text ->
                                        builder.applyOutput(parsed.value)

                                    is StreamEvent.OutputBegin -> Unit

                                    is StreamEvent.CommandEnd ->
                                        builder.applyCommandEnd(parsed.exitCode)
                                }
                            }

                    is JournalEvent.CommandFinished ->
                        builder.applyCommandEnd(event.exitCode)
                }
            }
            return builder
        }
    }
}
