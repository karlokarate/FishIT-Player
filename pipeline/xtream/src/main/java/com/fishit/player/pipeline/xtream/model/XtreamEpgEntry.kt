package com.fishit.player.pipeline.xtream.model

/**
 * Represents an Electronic Program Guide (EPG) entry for an Xtream channel.
 *
 * This is a v2 stub model - production implementation will be expanded in later phases.
 *
 * @property channelId The channel this EPG entry belongs to
 * @property epgId Unique identifier for this EPG entry
 * @property title Program title
 * @property description Program description
 * @property startTime Start time in Unix timestamp (seconds)
 * @property endTime End time in Unix timestamp (seconds)
 */
data class XtreamEpgEntry(
    val channelId: String,
    val epgId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
)
