package com.chris.m3usuite.player.internal.state

import androidx.compose.runtime.Immutable
import androidx.media3.common.PlaybackException
import com.chris.m3usuite.player.internal.domain.PlaybackType

/**
 * Immutable UI state for the internal player.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 2 STATUS: MODULAR UI STATE FOR SIP SESSION
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This state class is used by both:
 * - SIP InternalPlayerSession (Phase 2, non-runtime)
 * - Future modular InternalPlayerScreen (Phase 3+)
 *
 * **PHASE 3 UI MODULE CONSUMPTION:**
 * The following fields are ready for Phase 3 UI module integration:
 *
 * - `isResumingFromLegacy`: UI can show "Resuming..." indicator during seek
 * - `kidActive`: UI shows kid profile indicator in controls
 * - `kidBlocked`: UI shows blocking overlay with exit option
 * - `remainingKidsMinutes`: UI shows countdown timer for kid profiles
 * - `resumeStartMs`: UI can show "Resumed from X:XX" toast
 *
 * **FIELD STABILITY GUARANTEES:**
 * - All fields emit stable, predictable values
 * - No runtime changes until Phase 3 activation
 * - State transitions are tested in InternalPlayerSessionPhase2Test
 */
@Immutable
data class InternalPlayerUiState(
    val playbackType: PlaybackType = PlaybackType.VOD,

    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val isLooping: Boolean = false,
    val playbackError: PlaybackException? = null,
    val sleepTimerRemainingMs: Long? = null,

    // ════════════════════════════════════════════════════════════════════════════
    // Kids / Screen-Time - Phase 2 fields
    // ════════════════════════════════════════════════════════════════════════════
    //
    // Phase 3 UI consumption:
    // - kidActive: Show kid profile indicator in player controls
    // - kidBlocked: Show full-screen blocking overlay with exit option
    // - kidProfileId: Use for profile-specific UI customization
    // - remainingKidsMinutes: Show countdown timer in controls or overlay
    val kidActive: Boolean = false,
    val kidBlocked: Boolean = false,
    val kidProfileId: Long? = null,
    /**
     * Remaining screen time in minutes for kid profile.
     *
     * Phase 3 UI consumption:
     * - Show countdown timer in controls: "12 min remaining"
     * - Show warning overlay when < 5 min remaining
     * - Null when kidActive is false
     */
    val remainingKidsMinutes: Int? = null,

    // ════════════════════════════════════════════════════════════════════════════
    // Resume state - Phase 2 fields
    // ════════════════════════════════════════════════════════════════════════════
    //
    // Phase 3 UI consumption:
    // - isResumingFromLegacy: Show "Resuming..." indicator during initial seek
    // - resumeStartMs: Show toast "Resumed from X:XX" after seek completes
    /**
     * True when the player is in the process of resuming from a saved position.
     *
     * Phase 3 UI consumption:
     * - Show "Resuming..." indicator during initial seek animation
     * - Clear indicator when seek completes or playback starts
     */
    val isResumingFromLegacy: Boolean = false,
    /**
     * The position (in milliseconds) that was loaded from resume storage.
     *
     * Phase 3 UI consumption:
     * - Show toast "Resumed from X:XX" after seek completes
     * - Null when no resume position was loaded or playback started fresh
     */
    val resumeStartMs: Long? = null,

    // UI dialogs / overlays
    val showSettingsDialog: Boolean = false,
    val showTracksDialog: Boolean = false,
    val showSpeedDialog: Boolean = false,
    val showSleepTimerDialog: Boolean = false,
    val showDebugInfo: Boolean = false,

    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
) {
    val isLive: Boolean
        get() = playbackType == PlaybackType.LIVE

    val isSeries: Boolean
        get() = playbackType == PlaybackType.SERIES
}

enum class AspectRatioMode {
    FIT,
    FILL,
    ZOOM,
    STRETCH,
}

/**
 * High-level controller API for the player screen.
 *
 * The screen and the UI modules should only talk to this abstraction,
 * never directly to ExoPlayer or TDLib.
 */
data class InternalPlayerController(
    val onPlayPause: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onSeekBy: (Long) -> Unit,
    val onChangeSpeed: (Float) -> Unit,
    val onToggleLoop: () -> Unit,
    val onEnterPip: () -> Unit,
    val onToggleSettingsDialog: () -> Unit,
    val onToggleTracksDialog: () -> Unit,
    val onToggleSpeedDialog: () -> Unit,
    val onToggleSleepTimerDialog: () -> Unit,
    val onToggleDebugInfo: () -> Unit,
    val onCycleAspectRatio: () -> Unit,
)