package com.athea.app.util

import java.io.File
import java.io.IOException

/** Crash-safe: temp file first, atomic rename second. */
fun File.writeTextAtomic(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        tmp.delete()
        writeText(text)
    }
}

fun File.writeBytesAtomic(bytes: ByteArray) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(this)) {
        tmp.delete()
        writeBytes(bytes)
    }
}
