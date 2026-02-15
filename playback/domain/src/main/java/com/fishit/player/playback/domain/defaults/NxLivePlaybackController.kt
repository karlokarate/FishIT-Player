package com.fishit.player.playback.domain.defaults

import com.fishit.player.core.model.repository.NxEpgRepository
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.toUriString
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.playback.domain.EpgEntry
import com.fishit.player.playback.domain.LiveChannelInfo
import com.fishit.player.playback.domain.LivePlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NX-backed LivePlaybackController with on-demand EPG fetching.
 *
 * ## EPG On-Demand Pattern
 *
 * EPG data is fetched **only when needed**:
 * 1. When [switchToChannel] is called (player opens a live channel)
 * 2. When [refreshEpg] is called explicitly (e.g., periodic refresh or user request)
 *
 * This avoids unnecessary API calls during channel listing.
 *
 * ## Architecture
 *
 * ```
 * Player UI → LivePlaybackController.switchToChannel(channelId)
 *           → NxEpgRepository.getNowNext(channelWorkKey)
 *           → EPG data displayed in player overlay
 * ```
 *
 * ## Future: EPG Prefetch Worker
 *
 * For favorites, a background worker should periodically prefetch EPG:
 * ```
 * EpgPrefetchWorker → NxWorkUserStateRepository.observeFavorites()
 *                   → NxEpgRepository.upsertBatch(epgEntries)
 * ```
 *
 * @see NxEpgRepository for EPG data access
 * @see NxWorkRepository for channel data
 */
@Singleton
class NxLivePlaybackController
    @Inject
    constructor(
        private val workRepository: NxWorkRepository,
        private val epgRepository: NxEpgRepository,
    ) : LivePlaybackController {
        companion object {
            private const val TAG = "NxLivePlaybackController"
            private const val CHANNELS_LIMIT = 500
        }

        private val _currentChannel = MutableStateFlow<LiveChannelInfo?>(null)
        private val _availableChannels = MutableStateFlow<List<LiveChannelInfo>>(emptyList())

        // Track current channel index for prev/next navigation
        private var currentChannelIndex: Int = -1
        private var channelWorkKeys: List<String> = emptyList()

        override val currentChannel: StateFlow<LiveChannelInfo?> = _currentChannel.asStateFlow()
        override val availableChannels: StateFlow<List<LiveChannelInfo>> = _availableChannels.asStateFlow()

        override suspend fun nextChannel(): LiveChannelInfo? {
            if (channelWorkKeys.isEmpty()) {
                loadChannelList()
            }
            if (channelWorkKeys.isEmpty()) return null

            currentChannelIndex = (currentChannelIndex + 1) % channelWorkKeys.size
            val nextKey = channelWorkKeys[currentChannelIndex]
            return switchToChannel(nextKey)
        }

        override suspend fun previousChannel(): LiveChannelInfo? {
            if (channelWorkKeys.isEmpty()) {
                loadChannelList()
            }
            if (channelWorkKeys.isEmpty()) return null

            currentChannelIndex =
                if (currentChannelIndex <= 0) {
                    channelWorkKeys.size - 1
                } else {
                    currentChannelIndex - 1
                }
            val prevKey = channelWorkKeys[currentChannelIndex]
            return switchToChannel(prevKey)
        }

        /**
         * Switch to a specific channel and fetch EPG data on-demand.
         *
         * This is the primary entry point for live playback. When called:
         * 1. Loads channel metadata from NX_Work
         * 2. Fetches now/next EPG data from NxEpgRepository
         * 3. Updates currentChannel StateFlow
         *
         * @param channelId The work key of the live channel
         * @return LiveChannelInfo with EPG data, or null if not found
         */
        override suspend fun switchToChannel(channelId: String): LiveChannelInfo? {
            return try {
                UnifiedLog.d(TAG) { "Switching to channel: $channelId" }

                // Load channel metadata
                val work = workRepository.get(channelId)
                if (work == null || work.type != WorkType.LIVE_CHANNEL) {
                    UnifiedLog.w(TAG) { "Channel not found or not a live channel: $channelId" }
                    return null
                }

                // On-demand EPG fetch
                val nowNext =
                    try {
                        epgRepository.getNowNext(channelId)
                    } catch (e: Exception) {
                        UnifiedLog.d(TAG) { "No EPG data for $channelId: ${e.message}" }
                        null
                    }

                val channelInfo =
                    LiveChannelInfo(
                        channelId = channelId,
                        name = work.displayTitle,
                        logoUrl = work.poster?.toUriString(),
                        currentProgram = nowNext?.now?.toEpgEntry(),
                        nextProgram = nowNext?.next?.toEpgEntry(),
                    )

                // Update state
                _currentChannel.value = channelInfo

                // Update index for prev/next navigation
                val index = channelWorkKeys.indexOf(channelId)
                if (index >= 0) {
                    currentChannelIndex = index
                }

                UnifiedLog.d(TAG) {
                    "Switched to ${work.displayTitle}, EPG: ${nowNext?.now?.title ?: "none"}"
                }

                channelInfo
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to switch to channel: $channelId" }
                null
            }
        }

        /**
         * Refresh EPG data for the current channel.
         *
         * Called periodically or when user requests refresh.
         */
        override suspend fun refreshEpg() {
            val current = _currentChannel.value ?: return

            try {
                val nowNext = epgRepository.getNowNext(current.channelId)
                _currentChannel.value =
                    current.copy(
                        currentProgram = nowNext.now?.toEpgEntry(),
                        nextProgram = nowNext.next?.toEpgEntry(),
                    )
                UnifiedLog.d(TAG) { "EPG refreshed for ${current.name}" }
            } catch (e: Exception) {
                UnifiedLog.d(TAG) { "EPG refresh failed for ${current.name}: ${e.message}" }
            }
        }

        /**
         * Load available channels for prev/next navigation.
         *
         * Uses observeByType().first() since NxWorkRepository provides Flow-based API.
         * For on-demand loading we just need the current snapshot, not continuous updates.
         */
        private suspend fun loadChannelList() {
            try {
                // Use observeByType().first() to get current channel list snapshot
                val channels = workRepository.observeByType(WorkType.LIVE_CHANNEL, limit = CHANNELS_LIMIT).first()
                channelWorkKeys = channels.map { it.workKey }

                // Also update available channels (without EPG for performance)
                _availableChannels.value =
                    channels.map { work ->
                        LiveChannelInfo(
                            channelId = work.workKey,
                            name = work.displayTitle,
                            logoUrl = work.poster?.toUriString(),
                            currentProgram = null, // Not fetched for list
                            nextProgram = null,
                        )
                    }

                UnifiedLog.d(TAG) { "Loaded ${channelWorkKeys.size} channels" }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to load channel list" }
            }
        }

        /**
         * Convert NxEpgRepository.EpgEntry to playback domain EpgEntry.
         */
        private fun NxEpgRepository.EpgEntry.toEpgEntry(): EpgEntry =
            EpgEntry(
                title = title,
                startTime = startMs,
                endTime = endMs,
                description = description,
            )
    }
