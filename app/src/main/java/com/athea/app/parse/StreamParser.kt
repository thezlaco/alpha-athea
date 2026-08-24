package com.athea.app.parse

/**
 * Semantic events extracted from the raw terminal byte stream.
 */
sealed interface StreamEvent {
    /** Clean human-readable text (ANSI noise already removed). */
    data class Text(val value: String) : StreamEvent

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
 *  - strip ANSI/VT noise (colors, cursor movement, other OSC sequences);
 *  - apply carriage-return overwrite semantics so progress bars collapse
 *    to their final line state;
 *  - recognize shell integration marks (OSC 133 C/D[;exit]) and emit them
 *    as [StreamEvent.OutputBegin] / [StreamEvent.CommandEnd]. Marks A/B
 *    are consumed but ignored: Athea renders input itself and keeps the
 *    prompt empty, so there is nothing to hide.
 *
 * Pure Kotlin: no Android dependencies, fully unit-testable.
 */
class StreamParser {

    private enum class State { NORMAL, ESC, CSI, OSC, OSC_ESC, CHARSET }

    private var state = State.NORMAL
    private val csiBuffer = StringBuilder()
    private val oscBuffer = StringBuilder()

    private val line = StringBuilder()
    private val pendingText = StringBuilder()
    private val events = ArrayList<StreamEvent>()

    /**
     * Set by a carriage return: the cursor is back at line start. Real
     * terminals end lines with CRLF, so a following LF must keep the line
     * intact; printable characters after CR overwrite it (progress bars).
     */
    private var cursorAtLineStart = false

    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)

    private var carry = ByteArray(0)

    /** Feeds raw bytes; returns semantic events in arrival order. */
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
            // Unreachable with REPLACE actions, kept as a hard guard.
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
        when (ch) {
            in '0'..'?', in ' '..'/' -> csiBuffer.append(ch)
            in '@'..'~' -> state = State.NORMAL // sequence consumed silently
            else -> state = State.NORMAL // malformed; stop consuming
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
            // Invalid terminator; abandon the sequence entirely.
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
                // CRLF (the common line ending) must keep the line content.
                pendingText.append(line)
                pendingText.append('\n')
                line.setLength(0)
                cursorAtLineStart = false
            }
            '\r' -> cursorAtLineStart = true
            '\b' -> {
                cursorAtLineStart = false
                if (line.isNotEmpty()) line.setLength(line.length - 1)
            }
            '\t' -> line.append(ch)
            else -> {
                if (ch >= ' ') {
                    if (cursorAtLineStart) {
                        // Overwrite semantics: progress bars redraw from
                        // the start of the line.
                        line.setLength(0)
                        cursorAtLineStart = false
                    }
                    line.append(ch)
                }
                // Other C0 controls are dropped.
            }
        }
    }

    // ------------------------------------------------------------------ marks

    private fun handleOsc(payload: String) {
        val parts = payload.split(';')
        if (parts.firstOrNull()?.toIntOrNull() != 133) return // unrelated OSC
        // A partially collected line belongs to the past: flush it before
        // the mark so text and marks keep their chronological order.
        finishLine()
        flushPending()
        when (parts.getOrNull(1)) {
            "A", "B" -> Unit // prompt boundaries: nothing to hide, consumed

            "C" -> events.add(StreamEvent.OutputBegin)

            "D" -> events.add(StreamEvent.CommandEnd(parts.getOrNull(2)?.toIntOrNull()))
        }
    }

    // ----------------------------------------------------------------- helpers

    private fun finishLine() {
        if (line.isNotEmpty()) {
            pendingText.append(line)
            line.setLength(0)
        }
    }

    private fun flushPending() {
        if (pendingText.isNotEmpty()) {
            events.add(StreamEvent.Text(pendingText.toString()))
            pendingText.setLength(0)
        }
    }

    private companion object {
        const val ESC = '\u001B'
        const val BEL = '\u0007'
    }
}
