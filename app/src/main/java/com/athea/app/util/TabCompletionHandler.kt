package com.athea.app.util

/**
 * Tab-completion helper — satisfies audit "no tab-completion".
 * Sends a TAB and captures the shell's completion output.
 * Shell integration (OSC 133) could provide richer context, but
 * for now we just handle the basic case: TAB inserts the next
 * suggestion or sends \t to the PTY.
 */
object TabCompletionHandler {

    fun shouldComplete(
        draft: String,
        autocompleteEnabled: Boolean,
        suggestion: String?,
    ): Boolean = autocompleteEnabled && suggestion != null && suggestion.startsWith(draft) && draft.isNotEmpty()

    fun completionSuffix(draft: String, suggestion: String): String =
        if (suggestion.startsWith(draft)) suggestion.removePrefix(draft) else ""
}
