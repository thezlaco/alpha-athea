package com.athea.app.parse

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

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

    // ANSI SGR current style — updated on CSI m
    private var currentStyle = SpanStyle()
    private var currentFg: Color? = null
    private var currentBg: Color? = null
    private var isBold = false

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
                appendToLine("\n", currentStyle)
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
                        // Re-append truncated with same style (simplified: use currentStyle)
                        lineBuilder.append(AnnotatedString(truncated, current.spanStyles))
                        // Actually need to preserve spans, but for \b we drop last char with its span
                        // Simplified: rebuild from current with spans adjusted
                        // For now, just keep text length tracking
                    }
                    lineLength = maxOf(0, lineLength - 1)
                }
            }
            '\t' -> appendToLine("\t", currentStyle)
            else -> {
                if (ch >= ' ') {
                    if (cursorAtLineStart) {
                        lineBuilder = AnnotatedString.Builder()
                        lineLength = 0
                        cursorAtLineStart = false
                    }
                    appendToLine(ch.toString(), currentStyle)
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
        if (params.isEmpty()) {
            resetStyle()
            return
        }
        val codes = params.split(';').mapNotNull { it.toIntOrNull() }
        var i = 0
        while (i < codes.size) {
            when (val c = codes[i]) {
                0 -> resetStyle()
                1 -> isBold = true
                22 -> isBold = false
                30 -> currentFg = Color(0xFF000000)
                31 -> currentFg = Color(0xFFCC0000)
                32 -> currentFg = Color(0xFF00CC00)
                33 -> currentFg = Color(0xFFCCCC00)
                34 -> currentFg = Color(0xFF0000CC)
                35 -> currentFg = Color(0xFFCC00CC)
                36 -> currentFg = Color(0xFF00CCCC)
                37 -> currentFg = Color(0xFFCCCCCC)
                90 -> currentFg = Color(0xFF777777)
                91 -> currentFg = Color(0xFFFF5555)
                92 -> currentFg = Color(0xFF55FF55)
                93 -> currentFg = Color(0xFFFFFF55)
                94 -> currentFg = Color(0xFF5555FF)
                95 -> currentFg = Color(0xFFFF55FF)
                96 -> currentFg = Color(0xFF55FFFF)
                97 -> currentFg = Color(0xFFFFFFFF)
                39 -> currentFg = null
                40 -> currentBg = Color(0xFF000000)
                41 -> currentBg = Color(0xFFCC0000)
                42 -> currentBg = Color(0xFF00CC00)
                43 -> currentBg = Color(0xFFCCCC00)
                44 -> currentBg = Color(0xFF0000CC)
                45 -> currentBg = Color(0xFFCC00CC)
                46 -> currentBg = Color(0xFF00CCCC)
                47 -> currentBg = Color(0xFFCCCCCC)
                49 -> currentBg = null
                38, 48 -> {
                    // 38;5;n or 38;2;r;g;b
                    if (i + 2 < codes.size && codes[i + 1] == 5) {
                        val color = colorFrom256(codes[i + 2])
                        if (c == 38) currentFg = color else currentBg = color
                        i += 2
                    } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                        val color = Color((0xFF000000L or (codes[i + 2].toLong() shl 16) or (codes[i + 3].toLong() shl 8) or codes[i + 4].toLong()))
                        if (c == 38) currentFg = color else currentBg = color
                        i += 4
                    }
                }
            }
            i++
        }
        updateCurrentStyle()
    }

    private fun resetStyle() {
        currentFg = null
        currentBg = null
        isBold = false
        currentStyle = SpanStyle()
    }

    private fun updateCurrentStyle() {
        var style = SpanStyle()
        currentFg?.let { style = style.copy(color = it) }
        currentBg?.let { style = style.copy(background = it) }
        if (isBold) style = style.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        currentStyle = style
    }

    private fun colorFrom256(n: Int): Color {
        // Simplified 256 color cube
        return when {
            n < 16 -> when (n) {
                0 -> Color.Black; 1 -> Color(0xFF800000); 2 -> Color(0xFF008000); 3 -> Color(0xFF808000)
                4 -> Color(0xFF000080); 5 -> Color(0xFF800080); 6 -> Color(0xFF008080); 7 -> Color(0xFFC0C0C0)
                8 -> Color(0xFF808080); 9 -> Color(0xFFFF0000); 10 -> Color(0xFF00FF00); 11 -> Color(0xFFFFFF00)
                12 -> Color(0xFF0000FF); 13 -> Color(0xFFFF00FF); 14 -> Color(0xFF00FFFF); 15 -> Color(0xFFFFFFFF)
                else -> Color.Gray
            }
            n < 232 -> {
                val idx = n - 16
                val r = idx / 36
                val g = (idx % 36) / 6
                val b = idx % 6
                Color(0xFF000000L or ((r * 40 + 55).toLong() shl 16) or ((g * 40 + 55).toLong() shl 8) or (b * 40 + 55).toLong())
            }
            else -> {
                val gray = (n - 232) * 10 + 8
                Color(0xFF000000L or (gray.toLong() shl 16) or (gray.toLong() shl 8) or gray.toLong())
            }
        }
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
