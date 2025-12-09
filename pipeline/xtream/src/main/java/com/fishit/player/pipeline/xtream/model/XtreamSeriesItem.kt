package com.fishit.player.pipeline.xtream.model

/**
 * Represents a TV series in the Xtream pipeline.
 *
 * This is a pipeline-internal DTO that will be converted to RawMediaMetadata
 * via toRawMediaMetadata() extension function.
 *
 * @property id Unique identifier for this series
 * @property name Display name of the series
 * @property cover URL to the series cover/poster
 * @property categoryId Category identifier this series belongs to
 * @property year Release year
 * @property rating Rating (0-10 scale)
 * @property plot Plot/description
 * @property cast Cast members
 * @property genre Genre string
 */
data class XtreamSeriesItem(
    val id: Int,
    val name: String,
    val cover: String? = null,
    val categoryId: String? = null,
    val year: String? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val cast: String? = null,
    val genre: String? = null,
)
