package com.fishit.player.miniplayer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * MiniPlayer display mode.
 *
 * ════════════════════════════════════════════════════════════════════════════════ PHASE 5 –
 * MiniPlayer Domain Model
 * ════════════════════════════════════════════════════════════════════════════════
 */
enum class MiniPlayerMode {
    /**
     * Normal playback mode.
     * - Play/Pause via PLAY/PAUSE
     * - Seek via FF/RW
     * - DPAD → background UI navigation (unless focused)
     */
    NORMAL,

    /**
     * Resize/move mode.
     * - FF/RW → resize width
     * - DPAD → move position
     * - CENTER → confirm
     * - BACK → cancel
     */
    RESIZE,
}

/**
 * MiniPlayer anchor position on screen.
 *
 * ════════════════════════════════════════════════════════════════════════════════ PHASE 5 –
 * MiniPlayer Domain Model
 * ════════════════════════════════════════════════════════════════════════════════
 */
enum class MiniPlayerAnchor {
    /** Top-left corner of the screen */
    TOP_LEFT,

    /** Top-right corner of the screen */
    TOP_RIGHT,

    /** Bottom-left corner of the screen */
    BOTTOM_LEFT,

    /** Bottom-right corner of the screen (default) */
    BOTTOM_RIGHT,

    /** Center-top of the screen (horizontal center, near top) */
    CENTER_TOP,

    /** Center-bottom of the screen (horizontal center, near bottom) */
    CENTER_BOTTOM,
}

/** Default MiniPlayer size (16:9 aspect ratio, suitable for most content). */
val DEFAULT_MINI_SIZE = DpSize(320.dp, 180.dp)

/** Minimum MiniPlayer size (aspect ratio preserved). */
val MIN_MINI_SIZE = DpSize(160.dp, 90.dp)

/** Maximum MiniPlayer size (aspect ratio preserved). */
val MAX_MINI_SIZE = DpSize(640.dp, 360.dp)

/** Size delta for resize operations (coarse adjustment via FF/RW). */
val RESIZE_SIZE_DELTA = DpSize(40.dp, 22.5.dp)

/** Position delta for move operations (fine adjustment via DPAD). */
const val MOVE_POSITION_DELTA = 20f

/**
 * Safe margin from screen edges in dp. MiniPlayer should never overlap this margin after
 * confirm/cancel.
 */
val SAFE_MARGIN_DP = 16.dp

/**
 * Threshold (in dp) for snapping to center anchors. If the MiniPlayer center is within this
 * distance of screen center, it snaps to center anchor.
 */
val CENTER_SNAP_THRESHOLD_DP = 80.dp

/**
 * State representation for the In-App MiniPlayer.
 *
 * ════════════════════════════════════════════════════════════════════════════════ PHASE 5 –
 * MiniPlayer Domain Model
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This data class represents the complete state of the MiniPlayer overlay:
 * - Visibility and display mode
 * - Position (anchor and optional offset)
 * - Size
 * - Return navigation context (where to go when resuming full player)
 *
 * **Key Principles:**
 * - Immutable state: All changes create new instances
 * - Navigation context: Stores where the user came from for proper back navigation
 * - UI-independent: No Compose UI dependencies beyond unit types
 *
 * @property visible Whether the MiniPlayer overlay is currently shown
 * @property mode Current display mode (NORMAL or RESIZE)
 * @property anchor Corner anchor position on screen
 * @property size Current size of the MiniPlayer
 * @property position Optional precise position offset (for drag/move)
 * @property returnRoute Navigation route to return to when exiting MiniPlayer
 * @property returnMediaId Media ID to highlight when returning to library
 * @property returnRowIndex Row index to scroll to when returning
 * @property returnItemIndex Item index within row to focus when returning
 * @property previousSize Size before entering resize mode (for cancel restoration)
 * @property previousPosition Position before entering resize mode (for cancel restoration)
 * @property hasShownFirstTimeHint Whether the first-time hint has been shown
 */
@Immutable
data class MiniPlayerState(
        val visible: Boolean = false,
        val mode: MiniPlayerMode = MiniPlayerMode.NORMAL,
        val anchor: MiniPlayerAnchor = MiniPlayerAnchor.BOTTOM_RIGHT,
        val size: DpSize = DEFAULT_MINI_SIZE,
        val position: Offset? = null,
        val returnRoute: String? = null,
        val returnMediaId: Long? = null,
        val returnRowIndex: Int? = null,
        val returnItemIndex: Int? = null,
        val previousSize: DpSize? = null,
        val previousPosition: Offset? = null,
        val hasShownFirstTimeHint: Boolean = false,
) {
    companion object {
        /** Default/initial state */
        val INITIAL = MiniPlayerState()
    }
}
