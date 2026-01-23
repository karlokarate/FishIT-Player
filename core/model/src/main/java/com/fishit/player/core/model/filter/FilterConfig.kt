package com.fishit.player.core.model.filter

/**
 * Filter type for content filtering.
 */
enum class FilterType {
    /** Hide adult content */
    ADULT,

    /** Filter by genre */
    GENRE,

    /** Filter by category (from source) */
    CATEGORY,

    /** Filter by year range */
    YEAR_RANGE,

    /** Filter by rating minimum */
    RATING_MINIMUM,

    /** Filter by source type (Xtream, Telegram, etc.) */
    SOURCE_TYPE,
}

/**
 * A single filter criterion.
 */
sealed interface FilterCriterion {
    val type: FilterType
    val isActive: Boolean

    /**
     * Hide adult content filter.
     */
    data class HideAdult(
        override val isActive: Boolean = true,
    ) : FilterCriterion {
        override val type: FilterType = FilterType.ADULT
    }

    /**
     * Include only specific genres.
     *
     * Empty set means no genre filter (show all).
     */
    data class GenreInclude(
        val genres: Set<String>,
        override val isActive: Boolean = true,
    ) : FilterCriterion {
        override val type: FilterType = FilterType.GENRE
    }

    /**
     * Exclude specific genres.
     *
     * Excluded genres will be hidden regardless of other filters.
     */
    data class GenreExclude(
        val genres: Set<String>,
        override val isActive: Boolean = true,
    ) : FilterCriterion {
        override val type: FilterType = FilterType.GENRE
    }

    /**
     * Filter by category ID(s).
     *
     * Category is source-specific metadata from Xtream/Telegram.
     */
    data class Category(
        val categoryIds: Set<String>,
        override val isActive: Boolean = true,
    ) : FilterCriterion {
        override val type: FilterType = FilterType.CATEGORY
    }

    /**
     * Filter by year range.
     */
    data class YearRange(
        val minYear: Int? = null,
        val maxYear: Int? = null,
        override val isActive: Boolean = true,
    ) : FilterCriterion {
        override val type: FilterType = FilterType.YEAR_RANGE
    }

    /**
     * Filter by minimum rating.
     *
     * @param minRating Minimum rating on 0-10 scale
     */
    data class RatingMinimum(
        val minRating: Double,
        override val isActive: Boolean = true,
    ) : FilterCriterion {
        override val type: FilterType = FilterType.RATING_MINIMUM
    }

    /**
     * Filter by source type.
     */
    data class SourceType(
        val sourceTypes: Set<String>,
        override val isActive: Boolean = true,
    ) : FilterCriterion {
        override val type: FilterType = FilterType.SOURCE_TYPE
    }
}

/**
 * Combined filter configuration.
 *
 * Holds all active filters. Filters are AND-combined (all must match).
 */
data class FilterConfig(
    val criteria: List<FilterCriterion> = emptyList(),
) {
    /** Whether any filter is active */
    val hasActiveFilters: Boolean
        get() = criteria.any { it.isActive }

    /** Count of active filters */
    val activeFilterCount: Int
        get() = criteria.count { it.isActive }

    /** Check if adult content should be hidden */
    val hideAdult: Boolean
        get() = criteria
            .filterIsInstance<FilterCriterion.HideAdult>()
            .any { it.isActive }

    /** Get excluded genres */
    val excludedGenres: Set<String>
        get() = criteria
            .filterIsInstance<FilterCriterion.GenreExclude>()
            .filter { it.isActive }
            .flatMap { it.genres }
            .toSet()

    companion object {
        /** No filters applied */
        val NONE = FilterConfig()

        /** Default filter config (hide adult) */
        val DEFAULT = FilterConfig(
            criteria = listOf(FilterCriterion.HideAdult(isActive = true)),
        )

        /** Create a filter that hides adult content */
        fun hideAdultOnly() = FilterConfig(
            criteria = listOf(FilterCriterion.HideAdult(isActive = true)),
        )
    }

    /**
     * Add or replace a filter criterion.
     */
    fun withCriterion(criterion: FilterCriterion): FilterConfig {
        val newCriteria = criteria.filterNot { it.type == criterion.type } + criterion
        return copy(criteria = newCriteria)
    }

    /**
     * Remove a filter by type.
     */
    fun withoutType(type: FilterType): FilterConfig {
        return copy(criteria = criteria.filterNot { it.type == type })
    }
}
