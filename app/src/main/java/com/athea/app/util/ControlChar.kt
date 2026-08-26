package com.athea.app.util

fun Char.isCtrlCombinable(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

fun Char.toControlChar(): Char = (uppercaseChar().code and 0x1F).toChar()
