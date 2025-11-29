package com.chris.m3usuite.player.internal.state

import androidx.compose.runtime.Immutable
import androidx.media3.common.PlaybackException
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.subtitles.SubtitleStyle
import com.chris.m3usuite.player.internal.subtitles.SubtitleTrack

/**
 * ════════════════════════════════════════════════════════════════════════════════
 * Phase 8 Task 5: Hot/Cold State Split for Compose Performance
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * **COLD STATE** - Rarely changing fields that describe playback context and metadata.
 *
 * These fields change infrequently:
 * - playbackType: Set once when media is loaded
 * - aspectRatioMode: Only changes on user action
 * - subtitleStyle: Only changes on user action
 * - Live TV metadata: Changes only on channel switch
 * - Kid mode state: Changes only on profile switch
 *
 * **Design Rationale:**
 * - COLD state can be safely observed by large layout composables
 * - Changes to COLD state are infrequent enough that recomposition cost is acceptable
 * - Metadata, titles, thumbnails, and static layout config go here
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 9.1
 * - INTERNAL_PLAYER_PHASE8_CHECKLIST.md Group 7
 *
 * @property playbackType Type of content being played (VOD, SERIES, LIVE)
 * @property aspectRatioMode Current aspect ratio mode
 * @property playbackSpeed Current playback speed (NOT trickplay speed)
 * @property isLooping Whether loop mode is enabled
 * @property playbackError Current playback error, if any
 * @property sleepTimerRemainingMs Remaining sleep timer time
 * @property kidActive Whether kid mode is active
 * @property kidBlocked Whether playback is blocked due to kid quota
 * @property kidProfileId The ID of the current kid profile
 * @property remainingKidsMinutes Remaining screen time for kid profile
 * @property subtitleStyle Current subtitle styling
 * @property selectedSubtitleTrack Currently selected subtitle track
 * @property availableSubtitleTracks Available subtitle tracks
 * @property liveChannelName Current live channel name
 * @property liveNowTitle Current program title (live)
 * @property liveNextTitle Next program title (live)
 * @property epgOverlayVisible Whether EPG overlay is visible
 * @property showSettingsDialog Whether settings dialog is open
 * @property showTracksDialog Whether tracks dialog is open
 * @property showSpeedDialog Whether speed dialog is open
 * @property showSleepTimerDialog Whether sleep timer dialog is open
 * @property showCcMenuDialog Whether CC menu is open
 * @property showDebugInfo Whether debug overlay is visible
 */
@Immutable
data class PlayerColdState(
    // Content metadata
    val playbackType: PlaybackType = PlaybackType.VOD,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val playbackSpeed: Float = 1f,
    val isLooping: Boolean = false,
    val playbackError: PlaybackException? = null,
    val sleepTimerRemainingMs: Long? = null,

    // Kid mode state
    val kidActive: Boolean = false,
    val kidBlocked: Boolean = false,
    val kidProfileId: Long? = null,
    val remainingKidsMinutes: Int? = null,

    // Resume state
    val isResumingFromLegacy: Boolean = false,
    val resumeStartMs: Long? = null,

    // Subtitle state
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val selectedSubtitleTrack: SubtitleTrack? = null,
    val availableSubtitleTracks: List<SubtitleTrack> = emptyList(),

    // Live TV state
    val liveChannelName: String? = null,
    val liveNowTitle: String? = null,
    val liveNextTitle: String? = null,
    val epgOverlayVisible: Boolean = false,

    // Dialog visibility
    val showSettingsDialog: Boolean = false,
    val showTracksDialog: Boolean = false,
    val showSpeedDialog: Boolean = false,
    val showSleepTimerDialog: Boolean = false,
    val showCcMenuDialog: Boolean = false,
    val showDebugInfo: Boolean = false,
) {
    /** Whether content is live TV. */
    val isLive: Boolean
        get() = playbackType == PlaybackType.LIVE

    /** Whether content is series. */
    val isSeries: Boolean
        get() = playbackType == PlaybackType.SERIES

    /**
     * Returns true if any blocking overlay/dialog is open.
     * Matches [InternalPlayerUiState.hasBlockingOverlay].
     */
    val hasBlockingOverlay: Boolean
        get() = showCcMenuDialog ||
            showSettingsDialog ||
            showTracksDialog ||
            showSpeedDialog ||
            showSleepTimerDialog ||
            kidBlocked

    companion object {
        /**
         * Extracts COLD state fields from full [InternalPlayerUiState].
         *
         * This is used to derive the COLD state when collecting from a ViewModel
         * that manages the full state.
         */
        fun fromFullState(state: InternalPlayerUiState): PlayerColdState = PlayerColdState(
            playbackType = state.playbackType,
            aspectRatioMode = state.aspectRatioMode,
            playbackSpeed = state.playbackSpeed,
            isLooping = state.isLooping,
            playbackError = state.playbackError,
            sleepTimerRemainingMs = state.sleepTimerRemainingMs,
            kidActive = state.kidActive,
            kidBlocked = state.kidBlocked,
            kidProfileId = state.kidProfileId,
            remainingKidsMinutes = state.remainingKidsMinutes,
            isResumingFromLegacy = state.isResumingFromLegacy,
            resumeStartMs = state.resumeStartMs,
            subtitleStyle = state.subtitleStyle,
            selectedSubtitleTrack = state.selectedSubtitleTrack,
            availableSubtitleTracks = state.availableSubtitleTracks,
            liveChannelName = state.liveChannelName,
            liveNowTitle = state.liveNowTitle,
            liveNextTitle = state.liveNextTitle,
            epgOverlayVisible = state.epgOverlayVisible,
            showSettingsDialog = state.showSettingsDialog,
            showTracksDialog = state.showTracksDialog,
            showSpeedDialog = state.showSpeedDialog,
            showSleepTimerDialog = state.showSleepTimerDialog,
            showCcMenuDialog = state.showCcMenuDialog,
            showDebugInfo = state.showDebugInfo,
        )
    }
}
