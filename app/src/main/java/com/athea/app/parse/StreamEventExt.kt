package com.athea.app.parse

import com.athea.app.transcript.TranscriptBuilder

fun StreamEvent.applyTo(builder: TranscriptBuilder) {
    when (this) {
        is StreamEvent.Text -> builder.applyOutput(value)
        is StreamEvent.OutputBegin -> Unit
        is StreamEvent.CommandEnd -> builder.applyCommandEnd(exitCode)
    }
}
