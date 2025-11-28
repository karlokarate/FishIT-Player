package com.chris.m3usuite.player.internal.state

import androidx.compose.runtime.Immutable
import androidx.media3.common.PlaybackException
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.subtitles.SubtitlePreset
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
    val showCcMenuDialog: Boolean = false, // Phase 4 Group 4
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
    // Trickplay State - Phase 5 Group 3 fields
    // ════════════════════════════════════════════════════════════════════════════
    //
    // These fields support trickplay (fast-forward/rewind) behavior for SIP.
    // Contract: INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md Section 6
    /**
     * Whether trickplay mode is currently active.
     *
     * Phase 5 UI consumption:
     * - Show trickplay indicator overlay (e.g., "2x ►►")
     * - Prevent controls auto-hide while adjusting trickplay
     * - Exit on play/pause or OK button press
     *
     * **Legacy Reference:** L1467-1470 (ffStage/rwStage indicate trickplay direction)
     */
    val trickplayActive: Boolean = false,
    /**
     * Current trickplay speed multiplier.
     *
     * Values:
     * - 1.0f = normal playback (not in trickplay)
     * - 2.0f, 3.0f, 5.0f = fast-forward speeds
     * - -2.0f, -3.0f, -5.0f = rewind speeds (negative = reverse direction)
     *
     * Phase 5 UI consumption:
     * - Display speed label (e.g., "2x", "-3x")
     * - Icon direction based on sign (►► for positive, ◀◀ for negative)
     *
     * **Legacy Reference:** L1467 trickplaySpeeds = listOf(2f, 3f, 5f)
     */
    val trickplaySpeed: Float = 1f,
    /**
     * Whether the seek preview overlay is visible.
     *
     * Phase 5 UI consumption:
     * - Show target position indicator during seeking
     * - Auto-hide after 900ms (match legacy behavior)
     *
     * **Legacy Reference:** L1456-1459 seekPreviewVisible
     */
    val seekPreviewVisible: Boolean = false,
    /**
     * Target position in milliseconds for seek preview.
     *
     * Phase 5 UI consumption:
     * - Display target position in seek preview overlay
     * - Null when seek preview is not active
     *
     * **Legacy Reference:** L1489-1507 showSeekPreview()
     */
    val seekPreviewTargetMs: Long? = null,
    // ════════════════════════════════════════════════════════════════════════════
    // Controls Auto-Hide - Phase 5 Group 4 fields
    // ════════════════════════════════════════════════════════════════════════════
    //
    // These fields support controls auto-hide behavior for SIP.
    // Contract: INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md Section 7
    /**
     * Whether player controls are currently visible.
     *
     * Phase 5 UI consumption:
     * - Controls (transport bar, buttons) shown when true
     * - Controls hidden when false
     * - Toggled by tap or DPAD center
     *
     * **Legacy Reference:** L1347-1348 controlsVisible
     */
    val controlsVisible: Boolean = true,
    /**
     * Counter incremented on user activity to reset auto-hide timer.
     *
     * Phase 5 UI consumption:
     * - LaunchedEffect watches this to restart hide timer
     * - Any user activity increments this value
     *
     * **Legacy Reference:** L1347-1348 controlsTick
     */
    val controlsTick: Int = 0,
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
    /**
     * List of available subtitle tracks from the current media.
     *
     * Phase 4 UI consumption:
     * - Populate CC menu track selection buttons
     * - Determine CC button visibility (visible when non-empty)
     *
     * **Kid Mode Behavior:**
     * - Tracks are still detected but CC button is hidden
     *
     * **Updated by:**
     * - Session's onTracksChanged listener extracts text tracks from Media3
     */
    val availableSubtitleTracks: List<SubtitleTrack> = emptyList(),
) {
    val isLive: Boolean
        get() = playbackType == PlaybackType.LIVE

    val isSeries: Boolean
        get() = playbackType == PlaybackType.SERIES

    /**
     * Returns true if any blocking overlay/dialog is open.
     *
     * Phase 5 Group 4: Never-hide conditions
     * Contract Section 7.3: Controls must NOT auto-hide when these are open.
     *
     * Blocking overlays:
     * - CC menu (showCcMenuDialog)
     * - Settings dialog (showSettingsDialog)
     * - Tracks dialog (showTracksDialog)
     * - Speed dialog (showSpeedDialog)
     * - Sleep timer dialog (showSleepTimerDialog)
     * - Kid block overlay (kidBlocked)
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

enum class AspectRatioMode {
    FIT,
    FILL,
    ZOOM,
    STRETCH,
    ;

    /**
     * Returns the next AspectRatioMode in the cycling order.
     *
     * **Phase 5 Group 2: Aspect Ratio Cycling**
     * Contract Section 4.1: Deterministic mode cycling
     *
     * Cycling order: FIT → FILL → ZOOM → FIT
     * STRETCH is excluded from cycling (kept for legacy compatibility but not in main cycle)
     *
     * This matches the contract-specified cycle and legacy behavior (L1374-1379).
     */
    fun next(): AspectRatioMode =
        when (this) {
            FIT -> FILL
            FILL -> ZOOM
            ZOOM -> FIT
            STRETCH -> FIT // Fallback: STRETCH returns to FIT
        }
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
    /**
     * Updates the subtitle style (Phase 4).
     *
     * Called when user applies style changes from the CC menu.
     * Persists via SubtitleStyleManager.
     *
     * @param style The new subtitle style to apply.
     */
    val onUpdateSubtitleStyle: (SubtitleStyle) -> Unit = {},
    /**
     * Applies a subtitle preset (Phase 4).
     *
     * Called when user selects a preset button in the CC menu.
     * Converts preset to style and persists via SubtitleStyleManager.
     *
     * @param preset The preset to apply.
     */
    val onApplySubtitlePreset: (SubtitlePreset) -> Unit = {},
    // ════════════════════════════════════════════════════════════════════════════
    // Trickplay Callbacks - Phase 5 Group 3
    // ════════════════════════════════════════════════════════════════════════════
    /**
     * Starts trickplay (fast-forward or rewind).
     *
     * @param direction +1 for fast-forward, -1 for rewind
     */
    val onStartTrickplay: (direction: Int) -> Unit = {},
    /**
     * Stops trickplay and optionally applies the current position.
     *
     * @param applyPosition If true, seek to the current trickplay position
     */
    val onStopTrickplay: (applyPosition: Boolean) -> Unit = {},
    /**
     * Cycles trickplay speed (2x → 3x → 5x → 2x).
     *
     * Only effective when trickplay is active.
     */
    val onCycleTrickplaySpeed: () -> Unit = {},
    /**
     * Performs a step seek by the given delta.
     *
     * Used for seek preview (e.g., ±10s or ±30s jumps).
     *
     * @param deltaMs The time delta in milliseconds (positive or negative)
     */
    val onStepSeek: (deltaMs: Long) -> Unit = {},
    // ════════════════════════════════════════════════════════════════════════════
    // Controls Visibility Callbacks - Phase 5 Group 4
    // ════════════════════════════════════════════════════════════════════════════
    /**
     * Toggles controls visibility.
     *
     * Called on tap or DPAD center press.
     */
    val onToggleControlsVisibility: () -> Unit = {},
    /**
     * Shows controls and resets the auto-hide timer.
     *
     * Called on any user interaction.
     */
    val onUserInteraction: () -> Unit = {},
    /**
     * Hides controls immediately.
     *
     * Called by auto-hide timer when timeout expires.
     */
    val onHideControls: () -> Unit = {},
    // ════════════════════════════════════════════════════════════════════════════
    // Phase 7 – MiniPlayer Callbacks
    // ════════════════════════════════════════════════════════════════════════════
    /**
     * Enters the in-app MiniPlayer mode.
     *
     * Phase 7: This replaces the native PiP behavior for the UI PIP button.
     * The button no longer calls enterPictureInPictureMode() from app code.
     * Instead, it triggers MiniPlayerManager.enterMiniPlayer() which shows
     * the in-app MiniPlayer overlay.
     *
     * **Behavior:**
     * - Saves the current playback context (route, media ID, etc.)
     * - Closes the full player screen
     * - Shows the MiniPlayer overlay
     * - Playback continues seamlessly via shared PlaybackSession
     *
     * **Contract Reference:**
     * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 4.2
     */
    val onEnterMiniPlayer: () -> Unit = {},
)

/**
 * Direction for trickplay operations.
 *
 * Phase 5 Group 3: Trickplay behavior
 */
enum class TrickplayDirection(
    val multiplier: Int,
) {
    FORWARD(1),
    REWIND(-1),
}
