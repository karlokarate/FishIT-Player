package com.fishit.player.playback.domain.defaults

import com.fishit.player.playback.domain.LiveChannelInfo
import com.fishit.player.playback.domain.LivePlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default LivePlaybackController with no-op behavior.
 *
 * This is a stub implementation for Phase 1.
 * Real live TV integration will be added in Phase 3.
 */
class DefaultLivePlaybackController : LivePlaybackController {
    private val _currentChannel = MutableStateFlow<LiveChannelInfo?>(null)
    private val _availableChannels = MutableStateFlow<List<LiveChannelInfo>>(emptyList())

    override val currentChannel: StateFlow<LiveChannelInfo?> = _currentChannel.asStateFlow()
    override val availableChannels: StateFlow<List<LiveChannelInfo>> = _availableChannels.asStateFlow()

    override suspend fun nextChannel(): LiveChannelInfo? {
        // No-op in Phase 1
        return null
    }

    override suspend fun previousChannel(): LiveChannelInfo? {
        // No-op in Phase 1
        return null
    }

    override suspend fun switchToChannel(channelId: String): LiveChannelInfo? {
        // No-op in Phase 1
        return null
    }

    override suspend fun refreshEpg() {
        // No-op in Phase 1
    }
}
