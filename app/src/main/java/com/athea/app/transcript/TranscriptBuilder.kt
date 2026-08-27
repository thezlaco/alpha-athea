package com.athea.app.transcript

import com.athea.app.core.journal.JournalEvent
import com.athea.app.core.model.Block
import com.athea.app.core.model.CommandBlock
import com.athea.app.core.model.OutputBlock
import com.athea.app.core.model.PREVIEW_LINES
import com.athea.app.parse.StreamEvent
import com.athea.app.parse.StreamParser
import com.athea.app.parse.applyTo

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
    private var runningOutputIndex: Int = -1
    private val runningText = StringBuilder()
    private var runningAnnotated = androidx.compose.ui.text.AnnotatedString.Builder()
    private var outputCounter = 0

    // Raw projection kept as chunks: trimming drops whole head chunks in
    // O(1) instead of copying the surviving megabyte on every overflow.
    private val rawChunks = ArrayDeque<String>()
    private var rawSize = 0

    /** Lines shown in a collapsed command bubble; live-tunable in settings. */
    @Volatile var previewLines: Int = previewLines
        private set

    fun applyPreviewLines(lines: Int) {
        previewLines = lines.coerceIn(1, 10)
    }

    // ---------------------------------------------------------------- input

    fun applyCommandSubmitted(seq: Long, text: String) {
        finishRunning(null)
        // Only the current expanded output's override matters; clearing all
        // OutputBlock overrides is O(n). Keep map small by removing only
        // out-* keys when needed (usually 0-1).
        if (expandedOverrides.isNotEmpty()) {
            expandedOverrides.keys.removeIf { it.startsWith("out-") }
        }
        blocks.add(CommandBlock(id = cmdId(seq), text = text))
        appendRaw(text + "\n")
    }

    fun applyOutput(annotated: androidx.compose.ui.text.AnnotatedString) {
        val text = annotated.text
        if (text.isEmpty()) return
        if (text.isBlank() && runningOutputId == null) return
        val id = runningOutputId
        if (id == null) {
            val newId = nextOutputId()
            runningText.setLength(0)
            runningAnnotated = androidx.compose.ui.text.AnnotatedString.Builder()
            runningText.append(text)
            runningAnnotated.append(annotated)
            blocks.add(OutputBlock(id = newId, text = text, annotated = annotated, running = true))
            runningOutputId = newId
            runningOutputIndex = blocks.lastIndex
        } else {
            runningText.append(text)
            runningAnnotated.append(annotated)
            if (runningText.length > MAX_RUNNING_CHARS) {
                val excess = runningText.length - MAX_RUNNING_CHARS
                runningText.delete(0, excess)
                // Trim annotated builder similarly by rebuilding from tail
                val full = runningAnnotated.toAnnotatedString()
                runningAnnotated = androidx.compose.ui.text.AnnotatedString.Builder()
                runningAnnotated.append(full.text.takeLast(MAX_RUNNING_CHARS).let { tail ->
                    // Preserve spans for tail — simplified: re-append tail without spans for now
                    // Full span preservation would require slicing spans, keep plain tail
                    androidx.compose.ui.text.AnnotatedString(tail)
                })
            }
        }
        appendRaw(text)
    }

    // Backward compat for tests / simple callers
    fun applyOutput(text: String) = applyOutput(androidx.compose.ui.text.AnnotatedString(text))

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
            val viewBlock = if (block is OutputBlock && block.running) {
                val tailText = runningText.takeLast(STREAM_RENDER_TAIL).toString()
                val fullAnnotated = runningAnnotated.toAnnotatedString()
                val tailAnnotated = if (fullAnnotated.text.length <= STREAM_RENDER_TAIL) {
                    fullAnnotated
                } else {
                    val start = fullAnnotated.text.length - STREAM_RENDER_TAIL
                    androidx.compose.ui.text.AnnotatedString(
                        text = fullAnnotated.text.takeLast(STREAM_RENDER_TAIL),
                        spanStyles = fullAnnotated.spanStyles.mapNotNull { span ->
                            if (span.end <= start) null else androidx.compose.ui.text.AnnotatedString.Range(
                                span.item, maxOf(0L, span.start.toLong() - start), span.end.toLong() - start
                            )
                        },
                        paragraphStyles = fullAnnotated.paragraphStyles.mapNotNull { span ->
                            if (span.end <= start) null else androidx.compose.ui.text.AnnotatedString.Range(
                                span.item, maxOf(0L, span.start.toLong() - start), span.end.toLong() - start
                            )
                        }
                    )
                }
                block.copy(text = tailText, annotated = tailAnnotated)
            } else {
                block
            }
            views.add(BlockView(viewBlock, computeCollapsed(viewBlock, expandedOutput)))
        }
        return TranscriptSnapshot(
            blocks = views,
            running = runningOutputId != null,
            rawText = if (displayRaw) rawText().takeLast(RAW_RENDER_CAP) else "",
        )
    }

    private fun computeCollapsed(block: Block, expandedOutputId: String?): Boolean {
        expandedOverrides[block.id]?.let { return it }
        return when (block) {
            is CommandBlock -> block.text.count { it == '\n' } + 1 > previewLines
            is OutputBlock -> !(block.running || block.id == expandedOutputId)
        }
    }

    // -------------------------------------------------------------- helpers

    private fun finishRunning(exitCode: Int?) {
        val id = runningOutputId ?: return
        val index = if (runningOutputIndex >= 0 && runningOutputIndex < blocks.size && blocks[runningOutputIndex].id == id) {
            runningOutputIndex
        } else {
            indexOfBlock(id)
        }
        runningOutputId = null
        runningOutputIndex = -1
        if (index >= 0) {
            val current = blocks[index] as OutputBlock
            blocks[index] = current.copy(
                text = runningText.toString(),
                annotated = runningAnnotated.toAnnotatedString(),
                running = false,
                exitCode = exitCode,
            )
        }
        runningText.setLength(0)
        runningAnnotated = androidx.compose.ui.text.AnnotatedString.Builder()
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
        rawChunks.addLast(text)
        rawSize += text.length
        if (rawSize > rawCapChars + RAW_TRIM_SLACK) {
            // Drop head chunks only while the remainder still stays above
            // the cap: the buffer lands in [cap, cap + chunk) after a trim.
            while (rawChunks.size > 1 && rawSize - rawChunks.first().length >= rawCapChars) {
                rawSize -= rawChunks.removeFirst().length
            }
        }
    }

    private fun rawText(): String =
        buildString(rawSize) {
            for (chunk in rawChunks) append(chunk)
        }

    companion object {
        const val DEFAULT_RAW_CAP = 1 shl 20
        private const val RAW_TRIM_SLACK = 1 shl 16

        /** Chars shown in the UI while a command is streaming. */
        const val STREAM_RENDER_TAIL = 3000

        /** Max chars rendered in raw view / finished block UI. */
        const val RAW_RENDER_CAP = 50_000

        /** Cap for in-memory running buffer to avoid OOM on huge streams. */
        private const val MAX_RUNNING_CHARS = 1_200_000

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

                    is JournalEvent.OutputArrived -> {
                        val parsedEvents = try {
                            parser.feed(event.bytes)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        for (parsed in parsedEvents) parsed.applyTo(builder)
                    }

                    is JournalEvent.CommandFinished ->
                        builder.applyCommandEnd(event.exitCode)
                }
            }
            return builder
        }
    }
}
