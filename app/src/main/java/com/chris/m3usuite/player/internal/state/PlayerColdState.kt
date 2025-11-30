package com.chris.m3usuite.player.internal.state

import androidx.compose.runtime.Immutable
import androidx.media3.common.PlaybackException
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.subtitles.SubtitleStyle
import com.chris.m3usuite.player.internal.subtitles.SubtitleTrack

/**
 * Cold (rarely updating) playback state.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 5: Compose & FocusKit Performance Hardening
 * Contract: INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 9
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This data class contains metadata and settings that rarely change during playback:
 * - Playback type (VOD/SERIES/LIVE)
 * - Subtitle style and track selection
 * - Aspect ratio mode
 * - Kid profile state
 * - Dialog visibility states
 * - Live channel information
 * - Error state
 *
 * **Why This Exists:**
 * Large layout Composables should observe cold state so they don't recompose on every
 * position update. Only small, focused Composables (ProgressBar, BufferingIndicator)
 * should observe hot state.
 *
 * **Usage Pattern:**
 * ```kotlin
 * @Composable
 * fun MainControlsLayout(coldState: PlayerColdState, onAction: () -> Unit) {
 *     // Layout only recomposes when metadata/style changes
 *     // NOT on every position tick
 * }
 * ```
 *
 * **Contract Reference:**
 * - Section 9.1: Cold paths (context, subtitleStyle, aspectRatioMode) in large layout Composables
 */
@Immutable
data class PlayerColdState(
    /** Type of content being played (VOD, SERIES, LIVE). */
    val playbackType: PlaybackType = PlaybackType.VOD,
    /** Current playback speed (1.0 = normal). */
    val playbackSpeed: Float = 1f,
    /** Whether looping is enabled. */
    val isLooping: Boolean = false,
    /** Current playback error, if any. */
    val playbackError: PlaybackException? = null,
    /** Sleep timer remaining time in milliseconds. */
    val sleepTimerRemainingMs: Long? = null,
    // ════════════════════════════════════════════════════════════════════════════
    // Kids / Screen-Time
    // ════════════════════════════════════════════════════════════════════════════
    /** Whether a kid profile is active. */
    val kidActive: Boolean = false,
    /** Whether playback is blocked due to screen time limit. */
    val kidBlocked: Boolean = false,
    /** Profile ID of the kid profile. */
    val kidProfileId: Long? = null,
    /** Remaining screen time in minutes for kid profile. */
    val remainingKidsMinutes: Int? = null,
    // ════════════════════════════════════════════════════════════════════════════
    // Resume state
    // ════════════════════════════════════════════════════════════════════════════
    /** True when resuming from a saved position. */
    val isResumingFromLegacy: Boolean = false,
    /** The position that was loaded from resume storage. */
    val resumeStartMs: Long? = null,
    // ════════════════════════════════════════════════════════════════════════════
    // UI dialogs / overlays
    // ════════════════════════════════════════════════════════════════════════════
    /** Whether the settings dialog is visible. */
    val showSettingsDialog: Boolean = false,
    /** Whether the tracks dialog is visible. */
    val showTracksDialog: Boolean = false,
    /** Whether the speed dialog is visible. */
    val showSpeedDialog: Boolean = false,
    /** Whether the sleep timer dialog is visible. */
    val showSleepTimerDialog: Boolean = false,
    /** Whether the CC menu dialog is visible. */
    val showCcMenuDialog: Boolean = false,
    /** Whether debug info overlay is visible. */
    val showDebugInfo: Boolean = false,
    /** Current aspect ratio mode. */
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    // ════════════════════════════════════════════════════════════════════════════
    // Live-TV
    // ════════════════════════════════════════════════════════════════════════════
    /** Name of the current live channel. */
    val liveChannelName: String? = null,
    /** Current program title (now playing). */
    val liveNowTitle: String? = null,
    /** Next program title. */
    val liveNextTitle: String? = null,
    /** Whether the EPG overlay is visible. */
    val epgOverlayVisible: Boolean = false,
    // ════════════════════════════════════════════════════════════════════════════
    // Subtitles
    // ════════════════════════════════════════════════════════════════════════════
    /** Current subtitle style. */
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /** Currently selected subtitle track. */
    val selectedSubtitleTrack: SubtitleTrack? = null,
    /** Available subtitle tracks. */
    val availableSubtitleTracks: List<SubtitleTrack> = emptyList(),
) {
    val isLive: Boolean
        get() = playbackType == PlaybackType.LIVE

    val isSeries: Boolean
        get() = playbackType == PlaybackType.SERIES

    /**
     * Returns true if any blocking overlay/dialog is open.
     * Controls must NOT auto-hide when these are open.
     */
    val hasBlockingOverlay: Boolean
        get() =
            showCcMenuDialog ||
                showSettingsDialog ||
                showTracksDialog ||
                showSpeedDialog ||
                showSleepTimerDialog ||
                kidBlocked
}

/**
 * Extension function to extract cold state from InternalPlayerUiState.
 *
 * This allows existing code to continue using InternalPlayerUiState while
 * gradually migrating performance-critical paths to use PlayerColdState.
 */
fun InternalPlayerUiState.toColdState(): PlayerColdState =
    PlayerColdState(
        playbackType = playbackType,
        playbackSpeed = playbackSpeed,
        isLooping = isLooping,
        playbackError = playbackError,
        sleepTimerRemainingMs = sleepTimerRemainingMs,
        kidActive = kidActive,
        kidBlocked = kidBlocked,
        kidProfileId = kidProfileId,
        remainingKidsMinutes = remainingKidsMinutes,
        isResumingFromLegacy = isResumingFromLegacy,
        resumeStartMs = resumeStartMs,
        showSettingsDialog = showSettingsDialog,
        showTracksDialog = showTracksDialog,
        showSpeedDialog = showSpeedDialog,
        showSleepTimerDialog = showSleepTimerDialog,
        showCcMenuDialog = showCcMenuDialog,
        showDebugInfo = showDebugInfo,
        aspectRatioMode = aspectRatioMode,
        liveChannelName = liveChannelName,
        liveNowTitle = liveNowTitle,
        liveNextTitle = liveNextTitle,
        epgOverlayVisible = epgOverlayVisible,
        subtitleStyle = subtitleStyle,
        selectedSubtitleTrack = selectedSubtitleTrack,
        availableSubtitleTracks = availableSubtitleTracks,
    )
