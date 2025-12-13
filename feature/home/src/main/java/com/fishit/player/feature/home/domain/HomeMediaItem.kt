package com.fishit.player.feature.home.domain

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType

/**
 * Domain model for Home screen content items.
 *
 * This is a **feature-facing** model that decouples the Home UI from pipeline/data concerns.
 * Contains only non-secret identifiers and display fields needed for the Home screen.
 *
 * **Architecture:**
 * - Feature layer uses HomeMediaItem (domain model)
 * - Data layer maps RawMediaMetadata → HomeMediaItem
 * - NO RawMediaMetadata in feature layer
 *
 * @property id Stable unique identifier for this media item
 * @property title Display title
 * @property poster Poster image reference if available
 * @property placeholderThumbnail Placeholder thumbnail for lazy loading
 * @property backdrop Backdrop image reference if available
 * @property mediaType Type of media (MOVIE, SERIES_EPISODE, LIVE, etc.)
 * @property sourceType Source of the media (TELEGRAM, XTREAM, etc.)
 * @property resumePosition Resume position in milliseconds
 * @property duration Total duration in milliseconds
 * @property isNew Whether this is newly added content
 * @property year Release year if available
 * @property rating Content rating if available
 * @property navigationId Identifier used for navigation to detail screen
 * @property navigationSource Source type for navigation
 */
data class HomeMediaItem(
    val id: String,
    val title: String,
    val poster: ImageRef? = null,
    val placeholderThumbnail: ImageRef? = null,
    val backdrop: ImageRef? = null,
    val mediaType: MediaType,
    val sourceType: SourceType,
    val resumePosition: Long = 0L,
    val duration: Long = 0L,
    val isNew: Boolean = false,
    val year: Int? = null,
    val rating: Float? = null,
    // Navigation data
    val navigationId: String,
    val navigationSource: SourceType
) {
    /**
     * Calculate resume fraction for progress display.
     * Returns null if no valid resume data.
     */
    val resumeFraction: Float?
        get() = if (duration > 0 && resumePosition > 0) {
            (resumePosition.toFloat() / duration).coerceIn(0f, 1f)
        } else null
}
