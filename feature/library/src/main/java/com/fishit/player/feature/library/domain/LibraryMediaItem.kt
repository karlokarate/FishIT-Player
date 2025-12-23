package com.fishit.player.feature.library.domain

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType

/**
 * Domain model for Library screen content items.
 *
 * This is a **feature-facing** model that decouples the Library UI from pipeline/data concerns.
 * Contains only non-secret identifiers and display fields needed for the Library screen.
 *
 * **Architecture:**
 * - Feature layer uses LibraryMediaItem (domain model)
 * - Data layer maps RawMediaMetadata → LibraryMediaItem
 * - NO RawMediaMetadata in feature layer
 *
 * @property id Stable unique identifier for this media item
 * @property title Display title
 * @property poster Poster image reference if available
 * @property backdrop Backdrop image reference if available
 * @property mediaType Type of media (MOVIE, SERIES_EPISODE, etc.)
 * @property sourceType Source of the media (TELEGRAM, XTREAM, etc.)
 * @property year Release year if available
 * @property rating Content rating if available (scale 0.0-10.0)
 * @property categoryId Category identifier for filtering
 * @property categoryName Category display name
 * @property genres List of genres
 * @property description Short description/overview
 * @property navigationId Identifier used for navigation to detail screen
 * @property navigationSource Source type for navigation
 */
data class LibraryMediaItem(
    val id: String,
    val title: String,
    val poster: ImageRef? = null,
    val backdrop: ImageRef? = null,
    val mediaType: MediaType,
    val sourceType: SourceType,
    val year: Int? = null,
    val rating: Float? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val genres: List<String> = emptyList(),
    val description: String? = null,
    // Navigation data
    val navigationId: String,
    val navigationSource: SourceType
)
