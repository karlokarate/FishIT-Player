package com.fishit.player.pipeline.xtream.model

/**
 * Represents a live TV channel from an Xtream provider.
 *
 * @property id Unique identifier for the channel
 * @property name Display name of the channel
 * @property streamUrl Direct URL to the live stream
 * @property logoUrl Optional channel logo URL
 * @property epgChannelId Optional EPG channel ID for program guide matching
 * @property categoryId Optional category ID
 * @property streamType Stream format type (e.g., "hls", "ts", "mpegts")
 */
data class XtreamChannel(
    val id: Long,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val epgChannelId: String? = null,
    val categoryId: Long? = null,
    val streamType: String? = null,
)
