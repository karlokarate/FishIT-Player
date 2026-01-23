package com.fishit.player.core.model.filter

/**
 * Predefined genre filters for TV-optimized quick access.
 *
 * These are preset filter chips that map display names to keywords matching
 * genres in media metadata. Used for quick genre filtering in Home, Library,
 * and Live screens.
 *
 * ## Usage
 * ```kotlin
 * // In ViewModel
 * private val _selectedGenre = MutableStateFlow(PresetGenreFilter.ALL)
 *
 * // Filter items
 * fun filterItems(items: List<MediaItem>, genre: PresetGenreFilter): List<MediaItem> {
 *     if (genre == PresetGenreFilter.ALL) return items
 *     return items.filter { item ->
 *         genre.matchesGenres(item.genres)
 *     }
 * }
 * ```
 *
 * ## TV/DPAD Optimization
 * This enum is optimized for TV remote navigation:
 * - Limited number of options (not overwhelming)
 * - Clear display names (localization-ready)
 * - Common genre categories that cover most content
 *
 * @property displayName User-facing name for the filter chip
 * @property keywords List of genre keywords that match this filter (case-insensitive)
 */
enum class PresetGenreFilter(
    val displayName: String,
    val keywords: List<String>,
) {
    /** Show all content - no genre filtering */
    ALL("Alle", emptyList()),

    /** Action movies and series */
    ACTION("Action", listOf("action")),

    /** Horror and thriller content */
    HORROR("Horror", listOf("horror", "thriller")),

    /** Comedy content */
    COMEDY("Comedy", listOf("comedy", "komödie")),

    /** Drama content */
    DRAMA("Drama", listOf("drama")),

    /** Science Fiction content */
    SCIFI("Sci-Fi", listOf("sci-fi", "science fiction", "science-fiction")),

    /** Documentary content */
    DOCUMENTARY("Dokus", listOf("documentary", "dokumentation", "doku")),

    /** Kids and family content */
    KIDS("Kids", listOf("kids", "kinder", "animation", "family", "familie")),

    /** Romance content */
    ROMANCE("Romance", listOf("romance", "romantik", "love")),

    /** Crime and criminal content */
    CRIME("Crime", listOf("crime", "krimi", "criminal")),
    ;

    /**
     * Check if any of the provided genres match this filter.
     *
     * @param genres Set of genre strings from media metadata
     * @return true if any genre matches any keyword (case-insensitive), or if this is ALL
     */
    fun matchesGenres(genres: Set<String>): Boolean {
        if (this == ALL) return true
        if (keywords.isEmpty()) return true
        return genres.any { genre ->
            keywords.any { keyword ->
                genre.contains(keyword, ignoreCase = true)
            }
        }
    }

    /**
     * Convert this preset filter to a FilterCriterion for use with FilterConfig.
     *
     * @return GenreInclude criterion if not ALL, or null if ALL (no filter)
     */
    fun toFilterCriterion(): FilterCriterion.GenreInclude? {
        if (this == ALL || keywords.isEmpty()) return null
        return FilterCriterion.GenreInclude(
            genres = keywords.toSet(),
            isActive = true,
        )
    }

    companion object {
        /**
         * All available preset filters for UI display.
         */
        val all: List<PresetGenreFilter> = entries.toList()

        /**
         * Find a preset filter by keyword.
         *
         * @param keyword Genre keyword to match
         * @return Matching preset filter, or ALL if no match
         */
        fun fromKeyword(keyword: String): PresetGenreFilter =
            entries.find { filter ->
                filter.keywords.any { it.equals(keyword, ignoreCase = true) }
            } ?: ALL
    }
}
