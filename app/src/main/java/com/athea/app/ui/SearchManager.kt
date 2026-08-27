package com.athea.app.ui

import com.athea.app.core.model.CommandBlock
import com.athea.app.core.model.OutputBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Extracted search logic from MainViewModel — handles query, match collection
 * and index navigation. Keeps the ViewModel thinner.
 */
class SearchManager {

    private val _search = MutableStateFlow<SearchState?>(null)
    val search: StateFlow<SearchState?> = _search

    fun enterSearch() {
        _search.value = SearchState()
    }

    fun exitSearch() {
        _search.value = null
    }

    fun updateQuery(query: String, sessions: List<SessionUi>, currentId: Long?) {
        val id = currentId ?: return
        val needle = query.lowercase()
        val matches = sessions.firstOrNull { it.id == id }
            ?.blocks
            ?.mapNotNull { view ->
                val haystack = when (val b = view.block) {
                    is CommandBlock -> b.text
                    is OutputBlock -> b.text
                }
                if (haystack.lowercase().contains(needle)) view.block.id else null
            }.orEmpty()
        _search.value = (_search.value ?: SearchState()).copy(
            query = query,
            matchBlockIds = if (query.isBlank()) emptyList() else matches,
            index = 0
        )
    }

    fun nextMatch(): String? {
        val s = _search.value ?: return null
        if (s.matchBlockIds.isEmpty()) return null
        val next = (s.index + 1) % s.matchBlockIds.size
        _search.value = s.copy(index = next)
        return s.matchBlockIds[next]
    }
}
