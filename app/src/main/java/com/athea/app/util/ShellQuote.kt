package com.athea.app.util

/** Single-quote shell escaping — every byte intact inside `'...'`. */
fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

/** Wrap multiline draft as `eval '...'` so it executes as one command. */
fun String.shellEval(): String =
    if (contains('\n')) "eval ${shellQuote()}\n" else "$this\n"
