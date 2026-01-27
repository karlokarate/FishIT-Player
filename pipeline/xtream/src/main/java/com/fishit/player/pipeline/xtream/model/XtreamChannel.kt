package com.fishit.player.pipeline.xtream.model

import java.util.Objects

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
    /**
     * Adult content flag from provider.
     *
     * Xtream provides this from API (is_adult field as "1" or "0" string).
     */
    val isAdult: Boolean = false,
) {
    /**
     * Compute fingerprint hash for incremental sync change detection.
     *
     * **Fields included:**
     * - id: Primary key
     * - name: Channel name changes
     * - categoryId: Category reassignment
     * - streamIcon: Logo changes
     * - epgChannelId: EPG mapping changes
     *
     * **Design:** docs/v2/INCREMENTAL_SYNC_DESIGN.md Section 7
     */
    fun fingerprint(): Int = Objects.hash(
        id,
        name,
        categoryId,
        streamIcon,
        epgChannelId,
    )
}
