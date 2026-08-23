package com.athea.app.engine

import android.system.Os
import android.system.OsConstants
import com.athea.app.core.terminal.EngineEvent
import com.athea.app.core.terminal.TerminalEngine
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * First concrete backend: the system shell attached to a PTY.
 * Deliberately invisible to the UI, which sees only [TerminalEngine].
 *
 * [rcPath] points at the app-provided shell rc file (passed to the child
 * through ENV) that installs OSC 133 integration marks.
 */
internal class NativeShellEngine(
    private val homeDir: String,
    private val rcPath: String,
) : TerminalEngine {

    private val _events = MutableSharedFlow<EngineEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<EngineEvent> = _events

    private val _isAlive = MutableStateFlow(false)
    override val isAlive: StateFlow<Boolean> = _isAlive

    private val started = AtomicBoolean(false)
    private val exitReported = AtomicBoolean(false)

    @Volatile private var masterFd: Int = -1
    @Volatile private var childPid: Int = -1

    override fun start(initialRows: Int, initialCols: Int): Boolean {
        if (!started.compareAndSet(false, true)) return isAlive.value
        return try {
            val handle = PtyBridge.createPty(initialRows, initialCols, homeDir, rcPath)
                ?: return false
            masterFd = handle[0]
            childPid = handle[1]
            _isAlive.value = true
            Thread({ readLoop(masterFd) }, "athea-pty-reader").start()
            Thread({ waitLoop(childPid) }, "athea-pty-waiter").start()
            true
        } catch (_: Exception) {
            _isAlive.value = false
            false
        }
    }

    override fun write(data: ByteArray) {
        val fd = masterFd
        if (fd < 0 || !_isAlive.value || data.isEmpty()) return
        try {
            PtyBridge.writePty(fd, data)
        } catch (_: Exception) {
            // Process died mid-write; the waiter thread will report the exit.
        }
    }

    override fun resize(rows: Int, cols: Int) {
        val fd = masterFd
        if (fd < 0) return
        try {
            PtyBridge.resizePty(fd, childPid, rows, cols)
        } catch (_: Exception) {
        }
    }

    override fun terminate() {
        val pid = childPid
        if (pid > 0 && _isAlive.value) {
            try {
                Os.kill(pid, OsConstants.SIGKILL)
            } catch (_: Exception) {
            }
        }
    }

    private fun readLoop(fd: Int) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (true) {
            val n = try {
                PtyBridge.readPty(fd, buffer)
            } catch (_: Exception) {
                -1
            }
            if (n <= 0) break
            _events.tryEmit(EngineEvent.Output(buffer.copyOf(n)))
        }
    }

    private fun waitLoop(pid: Int) {
        var code = -1
        try {
            code = PtyBridge.waitPid(pid)
        } catch (_: Exception) {
        }
        if (exitReported.compareAndSet(false, true)) {
            _isAlive.value = false
            _events.tryEmit(EngineEvent.Exited(code))
            val fd = masterFd
            if (fd >= 0) {
                try {
                    PtyBridge.closePty(fd)
                } catch (_: Exception) {
                }
            }
        }
    }

    private companion object {
        const val READ_BUFFER_SIZE = 8192
    }
}
