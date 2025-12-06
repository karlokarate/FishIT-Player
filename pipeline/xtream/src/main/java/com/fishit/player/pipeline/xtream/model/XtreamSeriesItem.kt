package com.fishit.player.pipeline.xtream.model

/**
 * Represents a TV series from an Xtream provider.
 *
 * @property id Unique identifier for the series
 * @property name Display title of the series
 * @property posterUrl URL to series poster/thumbnail image
 * @property backdropUrl Optional backdrop/banner image URL
 * @property description Optional series description or synopsis
 * @property rating Optional content rating
 * @property year Optional first air year
 * @property categoryId Optional category/genre ID
 * @property numberOfSeasons Total number of seasons available
 */
data class XtreamSeriesItem(
    val id: Long,
    val name: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val description: String? = null,
    val rating: String? = null,
    val year: Int? = null,
    val categoryId: Long? = null,
    val numberOfSeasons: Int = 0,
)
