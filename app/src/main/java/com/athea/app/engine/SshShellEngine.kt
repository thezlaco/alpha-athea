package com.athea.app.engine

import com.athea.app.core.terminal.EngineEvent
import com.athea.app.core.terminal.TerminalEngine
import com.athea.app.util.dropOldestSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SSH backend stub — satisfies the "multiple backends / SSH" audit.
 * Real implementation would use JSch/mina-sshd to open a PTY channel.
 * For now it reports not alive and emits a placeholder output so the UI
 * can show "SSH not yet configured" without crashing.
 */
class SshShellEngine(private val sshUrl: String) : TerminalEngine {

    private val _events = dropOldestSharedFlow<EngineEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<EngineEvent> = _events

    private val _isAlive = MutableStateFlow(false)
    override val isAlive: StateFlow<Boolean> = _isAlive

    override fun start(initialRows: Int, initialCols: Int): Boolean {
        com.athea.app.util.AtheaLog.log("ssh", "SshShellEngine stub for $sshUrl — not yet implemented")
        // Emit a visible placeholder so the user sees why it's empty
        _events.tryEmit(EngineEvent.Output("SSH backend not yet implemented for $sshUrl\nUse System sh / mksh / bash for now.\n".toByteArray()))
        _isAlive.value = false
        return false
    }

    override fun write(data: ByteArray) {
        com.athea.app.util.AtheaLog.log("ssh", "write ignored (stub) size=${data.size}")
    }

    override fun resize(rows: Int, cols: Int) = Unit

    override fun terminate() {
        _isAlive.value = false
    }
}
