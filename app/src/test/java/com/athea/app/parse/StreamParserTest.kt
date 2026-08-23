package com.athea.app.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamParserTest {

    private fun feedAll(vararg chunks: String): List<StreamEvent> {
        val parser = StreamParser()
        return chunks.flatMap { parser.feed(it.toByteArray(Charsets.UTF_8)) }
    }

    private fun texts(events: List<StreamEvent>): String =
        events.filterIsInstance<StreamEvent.Text>().joinToString("") { it.value }

    @Test
    fun `plain text passes through`() {
        val events = feedAll("hello world\n")
        assertEquals(1, events.size)
        assertEquals("hello world\n", texts(events))
    }

    @Test
    fun `color sequences are stripped`() {
        val events = feedAll("\u001B[31mred\u001B[0m plain\n")
        assertEquals("red plain\n", texts(events))
    }

    @Test
    fun `escape split across chunks is handled`() {
        val events = feedAll("\u001B", "[31mred\n")
        assertEquals("red\n", texts(events))
    }

    @Test
    fun `carriage return keeps final line state`() {
        val events = feedAll("10%\r50%\r100%\nnext\n")
        assertEquals("100%\nnext\n", texts(events))
    }

    @Test
    fun `backspace deletes last character`() {
        val events = feedAll("abc\b\bd\n")
        assertEquals("ad\n", texts(events))
    }

    @Test
    fun `utf-8 multibyte split across chunks survives`() {
        val parser = StreamParser()
        val first = parser.feed(byteArrayOf(0xC3.toByte()))
        val second = parser.feed(
            byteArrayOf(0xA9.toByte()) + "x\n".toByteArray()
        )
        assertEquals(0, first.size)
        assertEquals("éx\n", texts(second))
    }

    @Test
    fun `finish mark reports exit code`() {
        val events = feedAll("\u001B]133;D;2\u0007rest\n")
        assertEquals(
            listOf<StreamEvent>(
                StreamEvent.CommandEnd(2),
                StreamEvent.Text("rest\n"),
            ),
            events,
        )
    }

    @Test
    fun `mark split across chunks is recognized`() {
        val parser = StreamParser()
        val first = parser.feed("\u001B]133;D;".toByteArray())
        val second = parser.feed("2\u0007rest\n".toByteArray())
        assertEquals(0, first.size)
        assertEquals(
            listOf<StreamEvent>(
                StreamEvent.CommandEnd(2),
                StreamEvent.Text("rest\n"),
            ),
            second,
        )
    }

    @Test
    fun `output begin mark is emitted`() {
        val events = feedAll("\u001B]133;C\u0007out\n")
        assertEquals(
            listOf<StreamEvent>(
                StreamEvent.OutputBegin,
                StreamEvent.Text("out\n"),
            ),
            events,
        )
    }

    @Test
    fun `prompt region is suppressed`() {
        val parser = StreamParser()
        val first = parser.feed(
            "\u001B]133;A\u0007\u001B]133;B\u0007$ ls\n".toByteArray()
        )
        val second = parser.feed("\u001B]133;C\u0007res\n".toByteArray())
        assertTrue(first.isEmpty())
        assertEquals(
            listOf<StreamEvent>(
                StreamEvent.OutputBegin,
                StreamEvent.Text("res\n"),
            ),
            second,
        )
    }

    @Test
    fun `unrelated osc sequences are swallowed`() {
        val events = feedAll("\u001B]0;window title\u0007hi\n")
        assertEquals("hi\n", texts(events))
    }

    @Test
    fun `st prompt without terminator cannot hide output forever`() {
        val parser = StreamParser()
        val first = parser.feed("\u001B]133;A\u0007\u001B]133;B\u0007".toByteArray())
        assertTrue(first.isEmpty())
        val second = parser.feed("x".repeat(600).toByteArray())
        val third = parser.feed("\u001B]133;C\u0007ok\n".toByteArray())
        // 512 characters are suppressed, the rest must surface.
        assertEquals("x".repeat(600 - 512), texts(second))
        assertTrue(third.any { it is StreamEvent.OutputBegin })
        assertEquals("ok\n", texts(third))
    }

    @Test
    fun `text before a mark keeps chronological order`() {
        val events = feedAll("tail\u001B]133;D;0\u0007")
        assertEquals(
            listOf<StreamEvent>(
                StreamEvent.Text("tail"),
                StreamEvent.CommandEnd(0),
            ),
            events,
        )
    }
}
