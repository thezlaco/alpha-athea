package com.athea.app.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Number of command lines shown in a collapsed command bubble. */
const val PREVIEW_LINES = 3

/** Stable identity for a transcript element; unique within a session. */
sealed interface Block {
    val id: String
}

/** A command submitted by the user; rendered as a right-aligned bubble. */
@Immutable
data class CommandBlock(
    override val id: String,
    val text: String,
) : Block

/**
 * Terminal output produced after a submission; rendered left-aligned,
 * full width. [running] is true while its command is still executing.
 */
@Immutable
data class OutputBlock(
    override val id: String,
    val text: String,
    val running: Boolean = false,
    val exitCode: Int? = null,
) : Block

enum class DisplayMode { BLOCKS, RAW }

@Immutable
@Serializable
data class FavoriteCommand(
    val id: Long,
    val text: String,
)
