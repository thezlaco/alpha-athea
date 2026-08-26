package com.athea.app.engine

import android.system.Os
import android.system.OsConstants
import com.athea.app.core.terminal.EngineEvent
import com.athea.app.core.terminal.TerminalEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.athea.app.util.dropOldestSharedFlow
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

    private val _events = dropOldestSharedFlow<EngineEvent>(extraBufferCapacity = 256)
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
            com.athea.app.util.AtheaLog.log(
                "shell",
                "start: home=$homeDir rc=$rcPath rows=$initialRows cols=$initialCols",
            )
            val handle = PtyBridge.createPty(initialRows, initialCols, homeDir, rcPath)
            if (handle == null) {
                com.athea.app.util.AtheaLog.error("shell", "createPty returned null")
                return false
            }
            masterFd = handle[0]
            childPid = handle[1]
            _isAlive.value = true
            com.athea.app.util.AtheaLog.log("shell", "spawned pid=$childPid fd=$masterFd")
            Thread({ readLoop(masterFd) }, "athea-pty-reader").start()
            Thread({ waitLoop(childPid) }, "athea-pty-waiter").start()
            true
        } catch (e: Exception) {
            com.athea.app.util.AtheaLog.error("shell", "start failed", e)
            _isAlive.value = false
            false
        }
    }

    override fun write(data: ByteArray) {
        val fd = masterFd
        if (fd < 0 || !_isAlive.value || data.isEmpty()) {
            com.athea.app.util.AtheaLog.log(
                "shell",
                "write skipped: fd=$fd alive=${_isAlive.value} size=${data.size}",
            )
            return
        }
        try {
            com.athea.app.util.AtheaLog.log(
                "shell",
                "write size=${data.size} head=" + data.decodeToString().take(80),
            )
            PtyBridge.writePty(fd, data)
        } catch (e: Exception) {
            com.athea.app.util.AtheaLog.error("shell", "write failed", e)
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
        var total = 0L
        while (true) {
            val n = try {
                PtyBridge.readPty(fd, buffer)
            } catch (e: Exception) {
                com.athea.app.util.AtheaLog.error("shell", "read failed", e)
                -1
            }
            if (n <= 0) {
                com.athea.app.util.AtheaLog.log("shell", "read loop ended, totalBytes=$total")
                break
            }
            total += n
            if (total <= READ_BUFFER_SIZE * 2L) {
                // Log only the first chunks in detail; heavy output would
                // drown the ring buffer otherwise.
                com.athea.app.util.AtheaLog.log(
                    "shell",
                    "read n=$n head=" + buffer.copyOf(n).decodeToString().take(80),
                )
            }
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
            com.athea.app.util.AtheaLog.log("shell", "waitpid done, code=$code")
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
