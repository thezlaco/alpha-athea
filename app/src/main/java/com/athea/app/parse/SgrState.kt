package com.athea.app.parse

import androidx.compose.ui.graphics.Color

/**
 * Current SGR (Select Graphic Rendition) attributes.
 *
 * Both projections of the byte stream need to interpret colour and attribute
 * codes, and they need to interpret them *identically* - a 256-colour index
 * must resolve to the same RGB whether it ends up as a chat span or a grid
 * cell. So the interpretation lives here once, and each consumer maps the
 * resulting attributes to its own representation:
 *
 *  - [StreamParser] reads foreground/background/bold for chat spans.
 *  - The screen projection reads every attribute, because a cell in a
 *    terminal grid genuinely does have underline and reverse video.
 *
 * [reset] and [applySgr] mirror the exact behaviour the chat parser has
 * always had, so extracting this changes nothing that already renders.
 */
class SgrState {

    var foreground: Color? = null
        private set
    var background: Color? = null
        private set
    var bold: Boolean = false
        private set
    var dim: Boolean = false
        private set
    var italic: Boolean = false
        private set
    var underline: Boolean = false
        private set
    var reverse: Boolean = false
        private set
    var strikethrough: Boolean = false
        private set

    /** Returns to the unstyled state. */
    fun reset() {
        foreground = null
        background = null
        bold = false
        dim = false
        italic = false
        underline = false
        reverse = false
        strikethrough = false
    }

    /**
     * Applies one SGR sequence body, e.g. `"1;31"` or `"38;5;196"`. An empty
     * body is the SGR shorthand for a full reset (`ESC[m`).
     */
    fun applySgr(parameters: String) {
        if (parameters.isEmpty()) {
            reset()
            return
        }
        val codes = parameters.split(';').mapNotNull { it.toIntOrNull() }
        var i = 0
        while (i < codes.size) {
            when (val c = codes[i]) {
                0 -> reset()
                1 -> bold = true
                2 -> dim = true
                3 -> italic = true
                4 -> underline = true
                7 -> reverse = true
                9 -> strikethrough = true
                21, 22 -> {
                    bold = false
                    dim = false
                }
                23 -> italic = false
                24 -> underline = false
                27 -> reverse = false
                29 -> strikethrough = false
                30 -> foreground = Color(0xFF000000)
                31 -> foreground = Color(0xFFCC0000)
                32 -> foreground = Color(0xFF00CC00)
                33 -> foreground = Color(0xFFCCCC00)
                34 -> foreground = Color(0xFF0000CC)
                35 -> foreground = Color(0xFFCC00CC)
                36 -> foreground = Color(0xFF00CCCC)
                37 -> foreground = Color(0xFFCCCCCC)
                90 -> foreground = Color(0xFF777777)
                91 -> foreground = Color(0xFFFF5555)
                92 -> foreground = Color(0xFF55FF55)
                93 -> foreground = Color(0xFFFFFF55)
                94 -> foreground = Color(0xFF5555FF)
                95 -> foreground = Color(0xFFFF55FF)
                96 -> foreground = Color(0xFF55FFFF)
                97 -> foreground = Color(0xFFFFFFFF)
                39 -> foreground = null
                40 -> background = Color(0xFF000000)
                41 -> background = Color(0xFFCC0000)
                42 -> background = Color(0xFF00CC00)
                43 -> background = Color(0xFFCCCC00)
                44 -> background = Color(0xFF0000CC)
                45 -> background = Color(0xFFCC00CC)
                46 -> background = Color(0xFF00CCCC)
                47 -> background = Color(0xFFCCCCCC)
                49 -> background = null
                38, 48 -> {
                    // 38;5;n for the 256-colour cube, 38;2;r;g;b for truecolour.
                    if (i + 2 < codes.size && codes[i + 1] == 5) {
                        val color = fromPalette256(codes[i + 2])
                        if (c == 38) foreground = color else background = color
                        i += 2
                    } else if (i + 4 < codes.size && codes[i + 1] == 2) {
                        val color = Color(
                            0xFF000000L or
                                (codes[i + 2].toLong() shl 16) or
                                (codes[i + 3].toLong() shl 8) or
                                codes[i + 4].toLong(),
                        )
                        if (c == 38) foreground = color else background = color
                        i += 4
                    }
                }
            }
            i++
        }
    }

    companion object {
        /** xterm's six intensity levels for the 6x6x6 colour cube. */
        private val CUBE = intArrayOf(0, 95, 135, 175, 215, 255)

        /**
         * Resolves an xterm-256 palette index: 0-15 system colours, 16-231 the
         * 6x6x6 cube, 232-255 the greyscale ramp.
         */
        fun fromPalette256(n: Int): Color = when {
            n < 16 -> when (n) {
                0 -> Color.Black
                1 -> Color(0xFF800000)
                2 -> Color(0xFF008000)
                3 -> Color(0xFF808000)
                4 -> Color(0xFF000080)
                5 -> Color(0xFF800080)
                6 -> Color(0xFF008080)
                7 -> Color(0xFFC0C0C0)
                8 -> Color(0xFF808080)
                9 -> Color(0xFFFF0000)
                10 -> Color(0xFF00FF00)
                11 -> Color(0xFFFFFF00)
                12 -> Color(0xFF0000FF)
                13 -> Color(0xFFFF00FF)
                14 -> Color(0xFF00FFFF)
                15 -> Color(0xFFFFFFFF)
                else -> Color.Gray
            }

            n < 232 -> {
                val idx = n - 16
                val r = idx / 36
                val g = (idx % 36) / 6
                val b = idx % 6
                // The exact xterm cube levels. The common 55 + 40 * c
                // approximation is off by design at the low end: it renders
                // cube index 196 as #FF3737 instead of #FF0000. TUI programs
                // lean on the 256-colour palette heavily, so the grid needs
                // the real values.
                Color(
                    0xFF000000L or
                        (CUBE[r].toLong() shl 16) or
                        (CUBE[g].toLong() shl 8) or
                        CUBE[b].toLong(),
                )
            }

            else -> {
                val gray = (n - 232) * 10 + 8
                Color(
                    0xFF000000L or
                        (gray.toLong() shl 16) or
                        (gray.toLong() shl 8) or
                        gray.toLong(),
                )
            }
        }
    }
}
