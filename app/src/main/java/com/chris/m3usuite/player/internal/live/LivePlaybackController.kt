package com.chris.m3usuite.player.internal.live

import com.chris.m3usuite.player.internal.domain.PlaybackContext
import kotlinx.coroutines.flow.StateFlow

/**
 * Controller interface for live TV playback behavior.
 *
 * This interface is intentionally:
 * - **Pure Kotlin**: No Android or UI dependencies.
 * - **Domain-only**: Handles channel navigation, EPG state, and playback position tracking.
 * - **Testable**: All dependencies are injectable interfaces.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * BEHAVIOR CONTRACT COMPLIANCE (from INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md)
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * **LIVE playback never participates in resume** (Section 3.1):
 * - LIVE content has no stored resume position.
 * - ResumeManager returns null for LIVE type.
 * - This controller does NOT integrate with ResumeManager.
 *
 * **Kids gating and position enforcement are handled by existing components**:
 * - KidsPlaybackGate handles screen-time quota for all playback types including LIVE.
 * - BehaviorContractEnforcer (future) validates runtime behavior.
 * - This controller does NOT re-implement these concerns.
 *
 * **LivePlaybackController MUST remain domain-only**:
 * - No direct ExoPlayer manipulation.
 * - No UI/Compose references.
 * - No Android Context dependencies.
 * - State is exposed via [StateFlow] for UI consumption.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 */
interface LivePlaybackController {

    /**
     * Initializes the controller from a playback context.
     *
     * This should be called when a live playback session starts.
     * It loads the channel list, resolves the initial channel, and
     * fetches EPG data if available.
     *
     * @param ctx The playback context containing live hints (category, provider, etc.).
     */
    suspend fun initFromPlaybackContext(ctx: PlaybackContext)

    /**
     * Jumps to an adjacent channel in the channel list.
     *
     * @param delta The number of channels to jump (+1 for next, -1 for previous).
     */
    fun jumpChannel(delta: Int)

    /**
     * Directly selects a channel by its ID.
     *
     * @param channelId The unique identifier of the channel to select.
     */
    fun selectChannel(channelId: Long)

    /**
     * Called when the playback position changes.
     *
     * This is used to track position for EPG overlay timing and
     * auto-hide logic. For LIVE content, this represents wall-clock
     * time progress, not VOD seek position.
     *
     * @param positionMs The current playback position in milliseconds.
     */
    fun onPlaybackPositionChanged(positionMs: Long)

    /**
     * The currently selected live channel, or null if no channel is selected.
     */
    val currentChannel: StateFlow<LiveChannel?>

    /**
     * The current state of the EPG overlay.
     */
    val epgOverlay: StateFlow<EpgOverlayState>
}

/**
 * Repository interface for accessing live channel data.
 *
 * This is a domain abstraction over the underlying data layer (ObxLive, etc.).
 * The implementation will bridge to existing repositories and ObjectBox entities.
 *
 * **Phase 3 - Step 2**: Implement this interface to wrap XtreamObxRepository/ObxLive queries.
 *
 * **Note on ID types**: LiveChannel.id uses `Long` for forward compatibility, while
 * ObxLive.streamId is `Int`. Implementations should cast appropriately.
 */
interface LiveChannelRepository {

    /**
     * Retrieves live channels filtered by category and/or provider.
     *
     * @param categoryHint Optional category filter.
     * @param providerHint Optional provider filter.
     * @return List of live channels matching the criteria.
     */
    suspend fun getChannels(
        categoryHint: String? = null,
        providerHint: String? = null,
    ): List<LiveChannel>

    /**
     * Retrieves a single channel by ID.
     *
     * @param channelId The channel's unique identifier.
     * @return The channel, or null if not found.
     */
    suspend fun getChannel(channelId: Long): LiveChannel?
}

/**
 * Repository interface for accessing EPG (Electronic Program Guide) data.
 *
 * This is a domain abstraction over the existing EpgRepository.
 * The implementation will delegate to the existing EpgRepository class.
 *
 * **Phase 3 - Step 2**: Implement this interface to wrap EpgRepository calls.
 *
 * **Note on ID types**: The `streamId` parameter is `Int` to match the existing
 * EpgRepository API and ObxLive.streamId field. The LiveChannel.id is `Long` for
 * forward compatibility. Implementations should handle the mapping appropriately.
 */
interface LiveEpgRepository {

    /**
     * Fetches the now/next program titles for a channel.
     *
     * @param streamId The channel's stream ID (Int, matching ObxLive.streamId).
     * @return Pair of (nowTitle, nextTitle), with nulls for unavailable data.
     */
    suspend fun getNowNext(streamId: Int): Pair<String?, String?>
}

/**
 * Abstraction for time/clock operations.
 *
 * This enables deterministic testing of time-dependent behavior
 * (e.g., EPG overlay auto-hide timing).
 */
interface TimeProvider {

    /**
     * Returns the current realtime in milliseconds (like System.currentTimeMillis()).
     */
    fun currentTimeMillis(): Long
}

/**
 * Default [TimeProvider] implementation that delegates to [System.currentTimeMillis].
 */
object SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
