package com.fishit.player.core.library.domain

import kotlinx.coroutines.flow.Flow

/**
 * Sort field for library content.
 */
enum class LibrarySortField {
    TITLE,
    YEAR,
    RATING,
    RECENTLY_ADDED,
    RECENTLY_UPDATED,
    DURATION,
}

/**
 * Sort direction.
 */
enum class LibrarySortDirection {
    ASCENDING,
    DESCENDING,
}

/**
 * Combined sort option.
 */
data class LibrarySortOption(
    val field: LibrarySortField = LibrarySortField.TITLE,
    val direction: LibrarySortDirection = LibrarySortDirection.ASCENDING,
) {
    companion object {
        val DEFAULT = LibrarySortOption()
        val RECENTLY_ADDED = LibrarySortOption(
            field = LibrarySortField.RECENTLY_ADDED,
            direction = LibrarySortDirection.DESCENDING,
        )
    }
}

/**
 * Filter configuration for library content.
 */
data class LibraryFilterConfig(
    val hideAdult: Boolean = true,
    val minRating: Double? = null,
    val yearRange: IntRange? = null,
    val includeGenres: Set<String>? = null,
    val excludeGenres: Set<String>? = null,
) {
    val hasActiveFilters: Boolean
        get() = hideAdult ||
            minRating != null ||
            yearRange != null ||
            includeGenres != null ||
            excludeGenres != null

    companion object {
        val DEFAULT = LibraryFilterConfig(hideAdult = true)
        val NONE = LibraryFilterConfig(hideAdult = false)
    }
}

/**
 * Query options combining sort and filter.
 */
data class LibraryQueryOptions(
    val sort: LibrarySortOption = LibrarySortOption.DEFAULT,
    val filter: LibraryFilterConfig = LibraryFilterConfig.DEFAULT,
    val limit: Int = 200,
)

// Repository interface for Library screen content.
//
// Architecture:
// - Contract lives in core modules
// - Implementations live in infra modules
interface LibraryContentRepository {
    fun observeVod(categoryId: String? = null): Flow<List<LibraryMediaItem>>

    fun observeSeries(categoryId: String? = null): Flow<List<LibraryMediaItem>>

    fun observeVodCategories(): Flow<List<LibraryCategory>>

    fun observeSeriesCategories(): Flow<List<LibraryCategory>>

    suspend fun search(
        query: String,
        limit: Int = 50,
    ): List<LibraryMediaItem>

    // ──────────────────────────────────────────────────────────────────────
    // Advanced Query (Sort/Filter)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Observe VOD items with sort and filter options.
     *
     * @param categoryId Optional category filter
     * @param options Sort and filter configuration
     * @return Flow of filtered and sorted items
     */
    fun observeVodWithOptions(
        categoryId: String? = null,
        options: LibraryQueryOptions = LibraryQueryOptions(),
    ): Flow<List<LibraryMediaItem>>

    /**
     * Observe Series items with sort and filter options.
     *
     * @param categoryId Optional category filter
     * @param options Sort and filter configuration
     * @return Flow of filtered and sorted items
     */
    fun observeSeriesWithOptions(
        categoryId: String? = null,
        options: LibraryQueryOptions = LibraryQueryOptions(),
    ): Flow<List<LibraryMediaItem>>

    /**
     * Get all unique genres available in the library.
     *
     * @return Set of unique genre names
     */
    suspend fun getAllGenres(): Set<String>

    /**
     * Get the year range of available content.
     *
     * @return Pair of (minYear, maxYear) or null if no content
     */
    suspend fun getYearRange(): Pair<Int, Int>?
}

data class LibraryCategory(
    val id: String,
    val name: String,
    val itemCount: Int = 0,
)
