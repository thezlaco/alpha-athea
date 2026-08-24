package com.athea.app.data

import kotlinx.serialization.Serializable

/** Action a key-row button performs. */
enum class KeyKind { INSERT, SEND, CTRL }

/** One user-configurable button of the key row. */
@Serializable
data class CustomKey(
    val label: String,
    val payload: String,
    val kind: KeyKind = KeyKind.SEND,
)

@Serializable
data class KeysIndex(
    val items: List<CustomKey> = emptyList(),
)
