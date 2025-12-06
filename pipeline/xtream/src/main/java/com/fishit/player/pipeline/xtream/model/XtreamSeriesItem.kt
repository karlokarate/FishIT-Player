package com.fishit.player.pipeline.xtream.model

/**
 * Represents a TV series in the Xtream pipeline.
 *
 * This is a v2 stub model - production implementation will be expanded in later phases.
 *
 * @property id Unique identifier for this series
 * @property name Display name of the series
 * @property cover URL to the series cover/poster
 * @property categoryId Category identifier this series belongs to
 */
data class XtreamSeriesItem(
    val id: Int,
    val name: String,
    val cover: String? = null,
    val categoryId: String? = null,
)
