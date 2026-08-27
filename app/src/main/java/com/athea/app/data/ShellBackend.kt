package com.athea.app.data

/** Available shell backends. System sh is always present, others are optional. */
enum class ShellBackend(val shellPath: String, val displayName: String) {
    SYSTEM_SH("/system/bin/sh", "System sh"),
    MKSH("/system/bin/mksh", "mksh"),
    BASH("/system/bin/bash", "bash"),
    CUSTOM("", "Custom");

    companion object {
        fun fromPath(path: String): ShellBackend =
            values().firstOrNull { it.shellPath == path } ?: CUSTOM
    }
}
