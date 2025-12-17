package com.fishit.player.pipeline.xtream.model

/**
 * Represents a live TV channel in the Xtream pipeline.
 *
 * Contains all relevant fields from `get_live_streams` API response.
 *
 * @property id Unique stream identifier for this channel
 * @property name Display name of the channel (may contain Unicode decoration symbols)
 * @property streamIcon URL to the channel logo/icon
 * @property epgChannelId EPG channel identifier for guide data
 * @property tvArchive Archive availability indicator (0 = no archive, non-zero = archive available)
 * @property tvArchiveDuration Catchup/timeshift duration in days
 * @property categoryId Category identifier this channel belongs to
 * @property added Unix epoch timestamp when channel was added to provider catalog
 */
data class XtreamChannel(
        val id: Int,
        val name: String,
        val streamIcon: String? = null,
        val epgChannelId: String? = null,
        val tvArchive: Int = 0,
        val tvArchiveDuration: Int = 0,
        val categoryId: String? = null,
        val added: Long? = null,
)
