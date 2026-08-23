package com.athea.app.engine

/**
 * Thin JNI bridge to the native PTY pair. All methods are blocking and
 * must stay in sync with src/main/cpp/pty.c.
 */
internal object PtyBridge {

    init {
        System.loadLibrary("athea")
    }

    /** Returns [masterFd, childPid], or null on failure. */
    external fun createPty(rows: Int, cols: Int, homePath: String, rcPath: String): IntArray?

    external fun writePty(fd: Int, data: ByteArray): Boolean

    /** Blocking read. Returns the number of bytes, or -1 on EOF/error. */
    external fun readPty(fd: Int, buffer: ByteArray): Int

    external fun resizePty(fd: Int, pid: Int, rows: Int, cols: Int)

    external fun closePty(fd: Int)
}
