package com.chris.m3usuite.player.internal.state

import androidx.compose.runtime.Immutable
import androidx.media3.common.PlaybackException
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.subtitles.SubtitleStyle
import com.chris.m3usuite.player.internal.subtitles.SubtitleTrack

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
     *
     * **Logical constraint (not enforced by type system):**
     * - Should be null when kidActive is false
     * - Session layer is responsible for maintaining this invariant
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
    // ════════════════════════════════════════════════════════════════════════════
    // Shadow Mode - Phase 3 fields
    // ════════════════════════════════════════════════════════════════════════════
    //
    // These fields are for modular screen/session diagnostics only.
    // Not consumed by runtime UI.
    // Will be used by Phase 3–4 overlay debugging and verification workflows.
    /**
     * Indicates whether shadow-mode observation is active.
     *
     * When true, the modular session is running in parallel with the legacy
     * player, observing the same inputs but never controlling playback.
     *
     * **NOT FOR RUNTIME USE:**
     * - Not consumed by runtime UI
     * - Used for Phase 3–4 diagnostics and verification
     * - Can be toggled via developer settings (future)
     */
    val shadowActive: Boolean = false,
    /**
     * Debug string representing the shadow session's current state.
     *
     * Format varies by implementation but typically includes:
     * - Position and duration
     * - Playback state flags
     * - Kid gate status
     * - Resume state
     *
     * Example: "pos=60000|dur=120000|playing=true|kid=false"
     *
     * **NOT FOR RUNTIME USE:**
     * - Not consumed by runtime UI
     * - Used for Phase 3–4 overlay debugging
     * - Null when shadow mode is inactive or not emitting debug info
     */
    val shadowStateDebug: String? = null,
    // ════════════════════════════════════════════════════════════════════════════
    // Parity Comparison - Phase 3 Step 2 fields
    // ════════════════════════════════════════════════════════════════════════════
    //
    // These fields exist to enable parity comparison between
    // legacy and shadow sessions (Phase 3–4).
    // They MUST NOT drive any runtime UI behavior yet.
    /**
     * Current playback position in milliseconds for parity comparison.
     *
     * **NOT FOR RUNTIME USE:**
     * - Used for Phase 3–4 shadow vs legacy comparison
     * - Separate from `positionMs` to avoid runtime changes
     * - Null when comparison is not active
     */
    val currentPositionMs: Long? = null,
    /**
     * Total duration in milliseconds for parity comparison.
     *
     * **NOT FOR RUNTIME USE:**
     * - Used for Phase 3–4 shadow vs legacy comparison
     * - Separate from `durationMs` to avoid runtime changes
     * - Null when comparison is not active
     */
    val comparisonDurationMs: Long? = null,
    // ════════════════════════════════════════════════════════════════════════════
    // Live-TV - Phase 3 Step 3.A fields
    // ════════════════════════════════════════════════════════════════════════════
    //
    // These fields support Live-TV playback for the SIP UI.
    // They are not wired to any behavior yet (Step 3.A: pure data class extension).
    /**
     * Name of the current live channel.
     *
     * Phase 3 UI consumption (future):
     * - Display in player controls overlay
     * - Show in EPG overlay
     * - Null when not playing live content
     */
    val liveChannelName: String? = null,
    /**
     * Current program title (now playing).
     *
     * Phase 3 UI consumption (future):
     * - Display in EPG overlay as "Now: <title>"
     * - Null when EPG data is unavailable or not playing live content
     */
    val liveNowTitle: String? = null,
    /**
     * Next program title (upcoming).
     *
     * Phase 3 UI consumption (future):
     * - Display in EPG overlay as "Next: <title>"
     * - Null when EPG data is unavailable or not playing live content
     */
    val liveNextTitle: String? = null,
    /**
     * Whether the EPG overlay is visible.
     *
     * Phase 3 UI consumption (future):
     * - Controls visibility of EPG overlay in player UI
     * - Toggleable by user action during live playback
     */
    val epgOverlayVisible: Boolean = false,
    // ════════════════════════════════════════════════════════════════════════════
    // Subtitle & CC - Phase 4 fields
    // ════════════════════════════════════════════════════════════════════════════
    //
    // These fields support subtitle styling and track selection for the SIP UI.
    // Contract: INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md
    /**
     * Current subtitle style applied to the player.
     *
     * Phase 4 UI consumption:
     * - Applied to Media3 subtitleView via CaptionStyleCompat
     * - Previewed in CC menu during style changes
     * - Shown in SettingsScreen preview box
     *
     * **Kid Mode Behavior:**
     * - Style is stored and updated normally
     * - BUT subtitles are not rendered (enforced by player session)
     */
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /**
     * Currently selected subtitle track.
     *
     * Phase 4 UI consumption:
     * - Show in CC menu tracks list (highlighted)
     * - Show in tracks dialog
     *
     * **Kid Mode Behavior:**
     * - Always null for kid profiles (no subtitle tracks selected)
     *
     * **LIVE Behavior:**
     * - Subtitle tracks may be available but are not persisted
     */
    val selectedSubtitleTrack: SubtitleTrack? = null,
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
    /**
     * Jumps to an adjacent live channel (Phase 3 Step 3.D).
     *
     * Only meaningful for LIVE playback. For VOD/SERIES, this is a no-op.
     *
     * @param delta The number of channels to jump (+1 for next, -1 for previous).
     */
    val onJumpLiveChannel: (delta: Int) -> Unit = {},
    /**
     * Toggles the CC/Subtitle menu (Phase 4).
     *
     * Opens the CC menu dialog for subtitle track selection and style customization.
     */
    val onToggleCcMenu: () -> Unit = {},
    /**
     * Selects a subtitle track (Phase 4).
     *
     * @param track The track to select, or null to disable subtitles.
     */
    val onSelectSubtitleTrack: (SubtitleTrack?) -> Unit = {},
)
