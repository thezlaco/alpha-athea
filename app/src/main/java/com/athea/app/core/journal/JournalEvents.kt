package com.athea.app.core.journal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything that ever happened in a session, in order. The journal is
 * the single source of truth: block views and the raw stream are pure
 * projections replayed from these events.
 */
@Serializable
sealed class JournalEvent {

    /** A command submitted from the editor. [seq] is unique per session. */
    @Serializable
    @SerialName("cmd")
    data class CommandSubmitted(
        val seq: Long,
        val text: String,
        val timestampMs: Long,
    ) : JournalEvent()

    /**
     * Raw bytes as they arrived from the engine (base64). Stored before
     * parsing so parser improvements apply retroactively on replay.
     */
    @Serializable
    @SerialName("out")
    data class OutputArrived(
        val base64: String,
    ) : JournalEvent()

    /** A real command boundary reported by a shell integration mark. */
    @Serializable
    @SerialName("fin")
    data class CommandFinished(
        val exitCode: Int?,
    ) : JournalEvent()
}
