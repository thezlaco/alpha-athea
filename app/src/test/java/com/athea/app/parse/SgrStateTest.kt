package com.athea.app.parse

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SgrStateTest {

    @Test
    fun `empty body is a full reset`() {
        val sgr = SgrState()
        sgr.applySgr("1;31")
        assertEquals(Color(0xFFCC0000), sgr.foreground)
        assertTrue(sgr.bold)

        sgr.applySgr("")
        assertNull(sgr.foreground)
        assertFalse(sgr.bold)
    }

    @Test
    fun `zero code resets too`() {
        val sgr = SgrState()
        sgr.applySgr("4;32")
        sgr.applySgr("0")
        assertNull(sgr.foreground)
        assertFalse(sgr.underline)
    }

    @Test
    fun `chat attributes round trip`() {
        val sgr = SgrState()
        sgr.applySgr("1;31;44")
        assertEquals(Color(0xFFCC0000), sgr.foreground)
        assertEquals(Color(0xFF0000CC), sgr.background)
        assertTrue(sgr.bold)

        // 22 clears weight without touching colour, which is the pair that
        // terminates a bold run in real output.
        sgr.applySgr("22")
        assertFalse(sgr.bold)
        assertEquals(Color(0xFFCC0000), sgr.foreground)
    }

    @Test
    fun `grid only attributes are tracked`() {
        val sgr = SgrState()
        sgr.applySgr("3;4;7;9")
        assertTrue(sgr.italic)
        assertTrue(sgr.underline)
        assertTrue(sgr.reverse)
        assertTrue(sgr.strikethrough)

        sgr.applySgr("23;24;27;29")
        assertFalse(sgr.italic)
        assertFalse(sgr.underline)
        assertFalse(sgr.reverse)
        assertFalse(sgr.strikethrough)
    }

    @Test
    fun `256 colour cube and greyscale resolve`() {
        val sgr = SgrState()
        sgr.applySgr("38;5;196")
        // Index 196 is the top of the cube: r=5 -> 255, g=b=0.
        assertEquals(Color(0xFFFF0000), sgr.foreground)

        sgr.applySgr("48;5;232")
        // First greyscale step.
        assertEquals(Color(0xFF080808), sgr.background)

        sgr.applySgr("38;5;15")
        assertEquals(Color(0xFFFFFFFF), sgr.foreground)
    }

    @Test
    fun `truecolour is resolved`() {
        val sgr = SgrState()
        sgr.applySgr("38;2;18;52;86")
        assertEquals(Color(0xFF123456), sgr.foreground)
    }

    @Test
    fun `default foreground clears without touching background`() {
        val sgr = SgrState()
        sgr.applySgr("31;42")
        sgr.applySgr("39")
        assertNull(sgr.foreground)
        assertEquals(Color(0xFF00CC00), sgr.background)
    }

    @Test
    fun `unparseable codes are ignored rather than fatal`() {
        val sgr = SgrState()
        sgr.applySgr("38;5")
        sgr.applySgr("nonsense;31")
        assertEquals(Color(0xFFCC0000), sgr.foreground)
    }
}
