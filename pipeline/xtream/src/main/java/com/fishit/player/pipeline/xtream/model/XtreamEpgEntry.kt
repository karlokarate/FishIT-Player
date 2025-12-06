package com.fishit.player.pipeline.xtream.model

/**
 * Represents an Electronic Program Guide (EPG) entry for a live TV channel.
 *
 * EPG entries provide schedule information for what's currently playing
 * and what will play next on live channels.
 *
 * @property id Unique identifier for the EPG entry
 * @property channelId ID of the channel this program is on
 * @property title Program title
 * @property description Optional program description
 * @property startTime Program start time (Unix timestamp in seconds)
 * @property endTime Program end time (Unix timestamp in seconds)
 * @property category Optional program category/genre
 */
data class XtreamEpgEntry(
    val id: String,
    val channelId: Long,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val category: String? = null,
)
