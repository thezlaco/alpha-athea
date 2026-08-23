package com.athea.app.core.terminal

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Events produced by a running terminal backend. */
sealed interface EngineEvent {
    /** Raw bytes read from the process output. */
    data class Output(val data: ByteArray) : EngineEvent

    /**
     * Process terminated. [exitCode] follows shell conventions:
     * non-negative = exit code, negative = killed by signal N.
     */
    data class Exited(val exitCode: Int) : EngineEvent
}

/**
 * Fundamental boundary between the Athea UI and any terminal backend.
 * The UI depends only on this interface; concrete engines are replaceable
 * without touching a single screen.
 */
interface TerminalEngine {
    val events: SharedFlow<EngineEvent>
    val isAlive: StateFlow<Boolean>

    /** Spawns the backend process. Returns false if it could not be started. */
    fun start(initialRows: Int, initialCols: Int): Boolean

    fun write(data: ByteArray)

    fun resize(rows: Int, cols: Int)

    /** Forcefully stops the backend process. */
    fun terminate()
}
