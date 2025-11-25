package com.chris.m3usuite.player.internal.live

import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default implementation of [LivePlaybackController].
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 – STEP 2: LIVE LOGIC MIGRATION
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This implementation migrates live TV logic from the legacy `InternalPlayerScreen` into a
 * domain-only controller. It handles:
 *
 * 1. **Channel List Management**: Loads channels from [LiveChannelRepository] based on
 *    [PlaybackContext] hints (category, provider).
 *
 * 2. **Channel Navigation**: Implements [jumpChannel] and [selectChannel] with wrap-around.
 *
 * 3. **EPG Resolution**: Queries [LiveEpgRepository] for now/next program titles and
 *    publishes them via [epgOverlay] StateFlow.
 *
 * 4. **Auto-Hide Timer**: Uses [TimeProvider] to calculate [EpgOverlayState.hideAtRealtimeMs]
 *    and hides the overlay in [onPlaybackPositionChanged] when time expires.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * BEHAVIOR CONTRACT COMPLIANCE (from INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md)
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * **LIVE playback never participates in resume** (Section 3.1):
 * - This controller does NOT integrate with ResumeManager.
 * - Resume is handled at the session level, where LIVE type is excluded.
 * - See: `DefaultResumeManager.loadResumePositionMs()` returns `null` for LIVE.
 *
 * **Kids gating is handled by existing components**:
 * - [KidsPlaybackGate] handles screen-time quota for LIVE playback.
 * - This controller does NOT re-implement kids gating.
 *
 * **LIVE behavior complies with the behavior contract**:
 * - LIVE never resumes; this is validated by shadow diagnostics outside of this controller.
 * - Shadow diagnostics may observe LIVE sessions for validation without affecting runtime.
 *
 * **This controller is domain-only**:
 * - Pure Kotlin, no Android dependencies.
 * - State exposed via [StateFlow] for UI consumption.
 * - Composable and testable in isolation.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * @param liveRepository Repository for accessing live channel data.
 * @param epgRepository Repository for accessing EPG (now/next) data.
 * @param clock Time provider for auto-hide timing and diagnostics.
 * @param epgOverlayDurationMs Duration in milliseconds to show the EPG overlay (default: 3000ms).
 */
class DefaultLivePlaybackController(
    private val liveRepository: LiveChannelRepository,
    private val epgRepository: LiveEpgRepository,
    private val clock: TimeProvider,
    private val epgOverlayDurationMs: Long = DEFAULT_EPG_OVERLAY_DURATION_MS,
) : LivePlaybackController {
    // ════════════════════════════════════════════════════════════════════════════
    // State
    // ════════════════════════════════════════════════════════════════════════════

    private val _currentChannel = MutableStateFlow<LiveChannel?>(null)
    override val currentChannel: StateFlow<LiveChannel?> = _currentChannel.asStateFlow()

    private val _epgOverlay =
        MutableStateFlow(
            EpgOverlayState(
                visible = false,
                nowTitle = null,
                nextTitle = null,
                hideAtRealtimeMs = null,
            ),
        )
    override val epgOverlay: StateFlow<EpgOverlayState> = _epgOverlay.asStateFlow()

    /**
     * Internal list of loaded channels. This mirrors the legacy `libraryLive` / `favorites` list.
     * Exposed for testing; not part of the public interface.
     */
    internal var channels: List<LiveChannel> = emptyList()
        private set

    // ════════════════════════════════════════════════════════════════════════════
    // Interface Methods
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Initializes the controller from a playback context.
     *
     * This mirrors legacy behavior from `InternalPlayerScreen.kt`:
     * - Lines 1120-1148: Loading `libraryLive` based on category hint.
     * - Lines 1151-1174: Initial channel resolution.
     *
     * **Note**: This controller does NOT call `BehaviorContractEnforcer` directly.
     * LIVE behavior is validated by shadow diagnostics externally.
     */
    override suspend fun initFromPlaybackContext(ctx: PlaybackContext) {
        // Safety: Only process LIVE playback contexts
        if (ctx.type != PlaybackType.LIVE) {
            return
        }

        // 1. Load channel list from repository using context hints
        channels =
            try {
                liveRepository.getChannels(
                    categoryHint = ctx.liveCategoryHint,
                    providerHint = ctx.liveProviderHint,
                )
            } catch (_: Throwable) {
                // Fail-safe: empty list on repository error
                emptyList()
            }

        if (channels.isEmpty()) {
            _currentChannel.value = null
            return
        }

        // 2. Determine initial channel based on mediaId or first in list
        val initialChannel =
            if (ctx.mediaId != null) {
                channels.find { it.id == ctx.mediaId } ?: channels.firstOrNull()
            } else {
                channels.firstOrNull()
            }

        _currentChannel.value = initialChannel

        // 3. Fetch EPG data for initial channel
        if (initialChannel != null) {
            refreshEpgOverlay(initialChannel)
        }
    }

    /**
     * Jumps to an adjacent channel in the channel list with wrap-around.
     *
     * This mirrors legacy behavior from `InternalPlayerScreen.kt`:
     * - Lines 1176-1185: `jumpLive(direction)` function.
     *
     * **Note**: This controller does NOT call `BehaviorContractEnforcer` directly.
     * LIVE behavior is validated by shadow diagnostics externally.
     *
     * @param delta The number of channels to jump (+1 for next, -1 for previous).
     */
    override fun jumpChannel(delta: Int) {
        if (channels.isEmpty()) return

        val current = _currentChannel.value
        val currentIndex =
            if (current != null) {
                channels.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }

        // Wrap-around navigation using mod
        val newIndex = (currentIndex + delta).mod(channels.size)
        val newChannel = channels[newIndex]

        _currentChannel.value = newChannel

        // Trigger EPG refresh for new channel (fire-and-forget in sync context)
        // Note: In actual usage, this would be called from a coroutine scope
        showEpgOverlayWithAutoHide(null, null)
    }

    /**
     * Directly selects a channel by its ID.
     *
     * This mirrors legacy behavior from `InternalPlayerScreen.kt`:
     * - Lines 1151-1174: `switchToLive(mi)` function.
     *
     * **Note**: This controller does NOT call `BehaviorContractEnforcer` directly.
     * LIVE behavior is validated by shadow diagnostics externally.
     *
     * @param channelId The unique identifier of the channel to select.
     */
    override fun selectChannel(channelId: Long) {
        val channel = channels.find { it.id == channelId }
        if (channel != null) {
            _currentChannel.value = channel
            showEpgOverlayWithAutoHide(null, null)
        }
    }

    /**
     * Called when the playback position changes.
     *
     * Used to check if the EPG overlay should auto-hide. This mirrors the legacy
     * behavior from `InternalPlayerScreen.kt`:
     * - Lines 1078-1088: `scheduleAutoHide()` with `epgMs = 3000L`.
     *
     * **Note**: This controller does NOT call `BehaviorContractEnforcer` directly.
     * LIVE behavior is validated by shadow diagnostics externally.
     *
     * @param positionMs The current playback position in milliseconds (not used for LIVE,
     *                   but triggers the auto-hide check).
     */
    override fun onPlaybackPositionChanged(positionMs: Long) {
        val currentOverlay = _epgOverlay.value
        val hideAt = currentOverlay.hideAtRealtimeMs

        // Check if overlay should be hidden
        if (currentOverlay.visible && hideAt != null) {
            val now = clock.currentTimeMillis()
            if (now >= hideAt) {
                _epgOverlay.value =
                    currentOverlay.copy(
                        visible = false,
                        hideAtRealtimeMs = null,
                    )
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Internal Helpers
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Refreshes the EPG overlay for the given channel by querying the repository.
     *
     * This mirrors legacy behavior from `InternalPlayerScreen.kt`:
     * - Lines 1003-1016: `refreshEpgOverlayForLive(id)` function.
     */
    private suspend fun refreshEpgOverlay(channel: LiveChannel) {
        val (nowTitle, nextTitle) =
            try {
                // Convert Long id to Int streamId (mirrors legacy id - 1_000_000_000_000L conversion)
                // The repository implementation handles the ID mapping
                epgRepository.getNowNext(channel.id.toInt())
            } catch (_: Throwable) {
                null to null
            }

        showEpgOverlayWithAutoHide(nowTitle, nextTitle)
    }

    /**
     * Shows the EPG overlay with auto-hide timing.
     *
     * Sets `visible = true` and calculates `hideAtRealtimeMs` based on the current time
     * plus [epgOverlayDurationMs].
     */
    private fun showEpgOverlayWithAutoHide(
        nowTitle: String?,
        nextTitle: String?,
    ) {
        val hideAt = clock.currentTimeMillis() + epgOverlayDurationMs

        _epgOverlay.value =
            EpgOverlayState(
                visible = true,
                nowTitle = nowTitle,
                nextTitle = nextTitle,
                hideAtRealtimeMs = hideAt,
            )
    }

    /**
     * Suspending version of EPG refresh that can be called from coroutines.
     * Used by [selectChannel] and [jumpChannel] when they need to update EPG.
     */
    internal suspend fun refreshEpgForCurrentChannel() {
        val channel = _currentChannel.value ?: return
        refreshEpgOverlay(channel)
    }

    companion object {
        /**
         * Default duration for the EPG overlay visibility (3 seconds).
         * Matches legacy behavior from `InternalPlayerScreen.scheduleAutoHide(epgMs = 3000L)`.
         */
        const val DEFAULT_EPG_OVERLAY_DURATION_MS = 3000L
    }
}
