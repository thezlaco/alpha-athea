package com.athea.app.parse

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Semantic events extracted from the raw terminal byte stream.
 */
sealed interface StreamEvent {
    /** Styled human-readable text (ANSI colors preserved as spans). */
    data class Text(val annotated: AnnotatedString) : StreamEvent {
        val value: String get() = annotated.text
    }

    /** A command's output begins (OSC 133;C). */
    data object OutputBegin : StreamEvent

    /** The running command finished (OSC 133;D), exit code when reported. */
    data class CommandEnd(val exitCode: Int?) : StreamEvent
}

/**
 * Incremental washer between the engine and the journal projections.
 *
 * Responsibilities:
 *  - stream-decode UTF-8 across chunk boundaries without replacement gaps;
 *  - preserve ANSI SGR colors as spans, strip other VT noise;
 *  - apply carriage-return overwrite semantics;
 *  - recognize shell integration marks (OSC 133 C/D[;exit]).
 *
 * Pure Kotlin: no Android dependencies, fully unit-testable.
 */
class StreamParser {

    private enum class State { NORMAL, ESC, CSI, OSC, OSC_ESC, CHARSET }

    private var state = State.NORMAL
    private val csiBuffer = StringBuilder()
    private val oscBuffer = StringBuilder()

    // SGR attributes live in SgrState so the screen projection resolves a
    // 256-colour index to exactly the same RGB a chat span gets.
    private val sgr = SgrState()

    private var lineBuilder = AnnotatedString.Builder()
    private var lineLength = 0
    private var pendingBuilder = AnnotatedString.Builder()
    private var pendingLength = 0
    private val events = ArrayList<StreamEvent>()

    private var cursorAtLineStart = false

    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)

    private var carry = ByteArray(0)

    fun feed(chunk: ByteArray): List<StreamEvent> {
        events.clear()
        val text = decode(chunk)
        for (ch in text) {
            step(ch)
        }
        finishLine()
        flushPending()
        return ArrayList(events)
    }

    // ------------------------------------------------------------- decoding

    private fun decode(chunk: ByteArray): String {
        if (chunk.isEmpty()) return ""
        val bytes = if (carry.isEmpty()) chunk else carry + chunk
        val input = java.nio.ByteBuffer.wrap(bytes)
        val output = java.nio.CharBuffer.allocate(bytes.size)
        try {
            decoder.decode(input, output, false)
        } catch (_: java.nio.charset.CharacterCodingException) {
        }
        output.flip()
        val text = output.toString()
        val remaining = input.remaining()
        carry = if (remaining > 0) {
            val rest = ByteArray(remaining)
            input.get(rest)
            rest
        } else {
            ByteArray(0)
        }
        return text
    }

    // ------------------------------------------------------------ state machine

    private fun step(ch: Char) {
        when (state) {
            State.ESC -> stepEsc(ch)
            State.CSI -> stepCsi(ch)
            State.OSC -> stepOsc(ch)
            State.OSC_ESC -> stepOscEsc(ch)
            State.CHARSET -> state = State.NORMAL
            State.NORMAL -> stepNormal(ch)
        }
    }

    private fun stepEsc(ch: Char) {
        when (ch) {
            '[' -> {
                csiBuffer.setLength(0)
                state = State.CSI
            }
            ']' -> {
                oscBuffer.setLength(0)
                state = State.OSC
            }
            '(', ')' -> state = State.CHARSET
            else -> state = State.NORMAL
        }
    }

    private fun stepCsi(ch: Char) {
        when {
            ch in '0'..'?' || ch in ' '..'/' -> csiBuffer.append(ch)
            ch == 'm' -> {
                handleSgr(csiBuffer.toString())
                state = State.NORMAL
            }
            ch in '@'..'~' -> state = State.NORMAL // other CSI, strip
            else -> state = State.NORMAL
        }
    }

    private fun stepOsc(ch: Char) {
        when (ch) {
            BEL -> {
                state = State.NORMAL
                handleOsc(oscBuffer.toString())
            }
            ESC -> state = State.OSC_ESC
            else -> oscBuffer.append(ch)
        }
    }

    private fun stepOscEsc(ch: Char) {
        if (ch == '\\') {
            state = State.NORMAL
            handleOsc(oscBuffer.toString())
        } else {
            state = State.NORMAL
        }
    }

    private fun stepNormal(ch: Char) {
        if (ch == ESC) {
            state = State.ESC
            return
        }
        when (ch) {
            '\n' -> {
                // CRLF must keep line content.
                appendToLine("\n", chatSpanStyle())
                pendingBuilder.append(lineBuilder.toAnnotatedString())
                pendingLength += lineLength + 1
                lineBuilder = AnnotatedString.Builder()
                lineLength = 0
                // Also account for newline char in pending
                if (pendingLength > 0) {
                    // pending already contains line + "\n" via lineBuilder
                }
                cursorAtLineStart = false
                // Reset lineBuilder already cleared, pending already has line
                // Need to clear line after flush? Actually we appended line to pending, so line is cleared
            }
            '\r' -> cursorAtLineStart = true
            '\b' -> {
                cursorAtLineStart = false
                if (lineLength > 0) {
                    // Remove last char from lineBuilder - approximate by rebuilding
                    val current = lineBuilder.toAnnotatedString()
                    lineBuilder = AnnotatedString.Builder()
                    if (current.text.isNotEmpty()) {
                        val truncated = current.text.dropLast(1)
                        // Re-append truncated, keeping the spans the line already had
                        lineBuilder.append(AnnotatedString(truncated, current.spanStyles))
                        // Actually need to preserve spans, but for \b we drop last char with its span
                        // Simplified: rebuild from current with spans adjusted
                        // For now, just keep text length tracking
                    }
                    lineLength = maxOf(0, lineLength - 1)
                }
            }
            '\t' -> appendToLine("\t", chatSpanStyle())
            else -> {
                if (ch >= ' ') {
                    if (cursorAtLineStart) {
                        lineBuilder = AnnotatedString.Builder()
                        lineLength = 0
                        cursorAtLineStart = false
                    }
                    appendToLine(ch.toString(), chatSpanStyle())
                }
            }
        }
    }

    private fun appendToLine(text: String, style: SpanStyle) {
        if (style == SpanStyle()) {
            lineBuilder.append(text)
        } else {
            lineBuilder.pushStyle(style)
            lineBuilder.append(text)
            lineBuilder.pop()
        }
        lineLength += text.length
    }

    // ------------------------------------------------------------------ marks

    private fun handleOsc(payload: String) {
        val parts = payload.split(';')
        if (parts.firstOrNull()?.toIntOrNull() != 133) return
        finishLine()
        flushPending()
        when (parts.getOrNull(1)) {
            "A", "B" -> Unit
            "C" -> events.add(StreamEvent.OutputBegin)
            "D" -> events.add(StreamEvent.CommandEnd(parts.getOrNull(2)?.toIntOrNull()))
        }
    }

    private fun handleSgr(params: String) {
        sgr.applySgr(params)
    }

    /**
     * The chat projection of the current attributes. Deliberately narrower
     * than what a grid cell can express: colour, background and weight only,
     * which is exactly what chat output has always rendered.
     */
    private fun chatSpanStyle(): SpanStyle {
        var style = SpanStyle()
        sgr.foreground?.let { style = style.copy(color = it) }
        sgr.background?.let { style = style.copy(background = it) }
        if (sgr.bold) style = style.copy(fontWeight = FontWeight.Bold)
        return style
    }

    // ----------------------------------------------------------------- helpers

    private fun finishLine() {
        if (lineLength > 0) {
            pendingBuilder.append(lineBuilder.toAnnotatedString())
            pendingLength += lineLength
            lineBuilder = AnnotatedString.Builder()
            lineLength = 0
        } else if (lineBuilder.toAnnotatedString().text.isNotEmpty()) {
            // newline case already handled via pending append
            lineBuilder = AnnotatedString.Builder()
            lineLength = 0
        }
    }

    private fun flushPending() {
        if (pendingLength > 0 || pendingBuilder.toAnnotatedString().text.isNotEmpty()) {
            events.add(StreamEvent.Text(pendingBuilder.toAnnotatedString()))
            pendingBuilder = AnnotatedString.Builder()
            pendingLength = 0
        }
    }

    private companion object {
        const val ESC = '\u001B'
        const val BEL = '\u0007'
    }
}
