package com.athea.app.util

fun String.trimCommand(): String = trimEnd('\n')

fun String.normalizeCommand(): String = trim().trimEnd('\n')
