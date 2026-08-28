package com.athea.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory diagnostic ring buffer. Everything around the shell pipeline
 * (spawn, write, read, parse, journal) reports here, so failures can be
 * exported from Settings and inspected instead of being guessed at.
 */
object AtheaLog {

    private const val CAPACITY = com.athea.app.ui.theme.Ui.logCapacity
    private val lines = ArrayDeque<String>()
    private val lock = Any()
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String) {
        enqueue("${format.format(Date())} [$tag] $message")
        android.util.Log.println(android.util.Log.INFO, "Athea", message)
    }

    fun error(tag: String, message: String, error: Throwable? = null) {
        enqueue(
            "${format.format(Date())} [$tag] ERROR: $message" +
                (error?.let { " :: ${it::class.java.simpleName}: ${it.message}" } ?: "")
        )
        android.util.Log.e("Athea", message, error)
    }

    private fun enqueue(line: String) = synchronized(lock) {
        lines.addLast(line)
        while (lines.size > CAPACITY) lines.removeFirst()
    }

    fun dump(): String = synchronized(lock) { lines.joinToString("\n") }

    fun clear() = synchronized(lock) { lines.clear() }
}
