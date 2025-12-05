package com.fishit.player.playback.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * EPG entry for live channels.
 */
data class EpgEntry(
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val description: String? = null
)

/**
 * Live channel information.
 */
data class LiveChannelInfo(
    val channelId: String,
    val name: String,
    val logoUrl: String?,
    val currentProgram: EpgEntry?,
    val nextProgram: EpgEntry?
)

/**
 * Controller for live TV playback behavior.
 *
 * Manages channel switching, EPG display, and live-specific controls.
 */
interface LivePlaybackController {

    /**
     * Current channel info as a StateFlow.
     */
    val currentChannel: StateFlow<LiveChannelInfo?>

    /**
     * Available channels for quick switching.
     */
    val availableChannels: StateFlow<List<LiveChannelInfo>>

    /**
     * Switches to the next channel.
     */
    suspend fun nextChannel(): LiveChannelInfo?

    /**
     * Switches to the previous channel.
     */
    suspend fun previousChannel(): LiveChannelInfo?

    /**
     * Switches to a specific channel.
     */
    suspend fun switchToChannel(channelId: String): LiveChannelInfo?

    /**
     * Refreshes EPG data for current channel.
     */
    suspend fun refreshEpg()
}
