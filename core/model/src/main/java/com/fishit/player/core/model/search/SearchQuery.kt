package com.fishit.player.core.model.search

import com.fishit.player.core.model.filter.FilterConfig
import com.fishit.player.core.model.sort.ContentType

/**
 * Search query configuration for advanced content search.
 */
data class SearchQuery(
    /** Text query (searches title, plot, cast, director) */
    val text: String = "",

    /** Optional content type filter */
    val contentType: ContentType? = null,

    /** Additional filters to apply */
    val filters: FilterConfig = FilterConfig.NONE,

    /** Maximum results to return */
    val limit: Int = 50,
) {
    /** Whether the search is empty */
    val isEmpty: Boolean
        get() = text.isBlank() && contentType == null && !filters.hasActiveFilters

    /** Normalized search text (lowercase, trimmed) */
    val normalizedText: String
        get() = text.trim().lowercase()

    companion object {
        /** Empty search query */
        val EMPTY = SearchQuery()

        /** Create a simple text search */
        fun textOnly(query: String) = SearchQuery(text = query)

        /** Create a search for a specific content type */
        fun forType(type: ContentType, query: String = "") = SearchQuery(
            text = query,
            contentType = type,
        )
    }
}

/**
 * A search result item with relevance score.
 */
data class SearchResult<T>(
    val item: T,
    val relevanceScore: Float = 1.0f,
    val matchedFields: Set<String> = emptySet(),
)

/**
 * Search history entry.
 */
data class SearchHistoryEntry(
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val resultCount: Int = 0,
)

/**
 * Search suggestion for autocomplete.
 */
sealed interface SearchSuggestion {
    val text: String
    val type: SuggestionType

    enum class SuggestionType {
        HISTORY,
        POPULAR,
        TITLE,
        GENRE,
        YEAR,
        ACTOR,
        DIRECTOR,
    }

    data class History(
        override val text: String,
        val timestamp: Long,
    ) : SearchSuggestion {
        override val type = SuggestionType.HISTORY
    }

    data class Title(
        override val text: String,
        val workKey: String,
    ) : SearchSuggestion {
        override val type = SuggestionType.TITLE
    }

    data class Genre(
        override val text: String,
        val count: Int,
    ) : SearchSuggestion {
        override val type = SuggestionType.GENRE
    }

    data class Year(
        override val text: String,
        val year: Int,
    ) : SearchSuggestion {
        override val type = SuggestionType.YEAR
    }

    data class Person(
        override val text: String,
        val role: SuggestionType,
    ) : SearchSuggestion {
        override val type = role
    }
}
