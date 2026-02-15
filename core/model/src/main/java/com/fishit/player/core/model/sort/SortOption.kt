package com.fishit.player.core.model.sort

/**
 * Sort direction for content lists.
 */
enum class SortDirection {
    ASCENDING,
    DESCENDING,
}

/**
 * Sort field options for content lists.
 *
 * Different content types support different sort fields.
 * Use [SortField.applicableTo] to check compatibility.
 */
enum class SortField {
    /** Sort by title (A-Z or Z-A) */
    TITLE,

    /** Sort by release year */
    YEAR,

    /** Sort by rating (highest first typical) */
    RATING,

    /** Sort by recently added (createdAt timestamp) */
    RECENTLY_ADDED,

    /** Sort by recently updated (updatedAt timestamp) */
    RECENTLY_UPDATED,

    /** Sort by duration (runtime) */
    DURATION,

    /** Sort by genre (alphabetically) */
    GENRE,
    ;

    /**
     * Default direction for this sort field.
     * TITLE/GENRE default to ascending, others to descending.
     */
    val defaultDirection: SortDirection
        get() =
            when (this) {
                TITLE, GENRE -> SortDirection.ASCENDING
                YEAR, RATING, RECENTLY_ADDED, RECENTLY_UPDATED, DURATION -> SortDirection.DESCENDING
            }

    /**
     * Display name resource key for UI.
     * Implementation should map this to localized strings.
     */
    val displayKey: String
        get() =
            when (this) {
                TITLE -> "sort_title"
                YEAR -> "sort_year"
                RATING -> "sort_rating"
                RECENTLY_ADDED -> "sort_recently_added"
                RECENTLY_UPDATED -> "sort_recently_updated"
                DURATION -> "sort_duration"
                GENRE -> "sort_genre"
            }
}

/**
 * Content type for sort applicability checks.
 */
enum class ContentType {
    MOVIE,
    SERIES,
    EPISODE,
    LIVE_CHANNEL,
    ALL,
}

/**
 * Sort configuration combining field and direction.
 */
data class SortOption(
    val field: SortField,
    val direction: SortDirection = field.defaultDirection,
) {
    companion object {
        /** Default sort option: Title A-Z */
        val DEFAULT = SortOption(SortField.TITLE, SortDirection.ASCENDING)

        /** Recently added (newest first) */
        val RECENTLY_ADDED = SortOption(SortField.RECENTLY_ADDED, SortDirection.DESCENDING)

        /** Get available sort fields for a content type */
        fun availableFieldsFor(contentType: ContentType): List<SortField> =
            when (contentType) {
                ContentType.MOVIE ->
                    listOf(
                        SortField.TITLE,
                        SortField.YEAR,
                        SortField.RATING,
                        SortField.RECENTLY_ADDED,
                        SortField.DURATION,
                        SortField.GENRE,
                    )
                ContentType.SERIES ->
                    listOf(
                        SortField.TITLE,
                        SortField.YEAR,
                        SortField.RATING,
                        SortField.RECENTLY_UPDATED,
                        SortField.GENRE,
                    )
                ContentType.EPISODE ->
                    listOf(
                        SortField.TITLE,
                        SortField.RECENTLY_ADDED,
                    )
                ContentType.LIVE_CHANNEL ->
                    listOf(
                        SortField.TITLE,
                        SortField.RECENTLY_UPDATED,
                    )
                ContentType.ALL -> SortField.entries
            }
    }
}
