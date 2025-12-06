package com.fishit.player.pipeline.xtream.model

/**
 * Represents a live TV channel in the Xtream pipeline.
 *
 * This is a v2 stub model - production implementation will be expanded in later phases.
 *
 * @property id Unique stream identifier for this channel
 * @property name Display name of the channel
 * @property streamIcon URL to the channel logo/icon
 * @property epgChannelId EPG channel identifier for guide data
 * @property tvArchive Archive availability indicator (0 = no archive, non-zero = archive available)
 * @property categoryId Category identifier this channel belongs to
 */
data class XtreamChannel(
    val id: Int,
    val name: String,
    val streamIcon: String? = null,
    val epgChannelId: String? = null,
    val tvArchive: Int = 0,
    val categoryId: String? = null,
)
