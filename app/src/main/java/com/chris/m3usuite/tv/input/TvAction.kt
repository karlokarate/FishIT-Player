package com.chris.m3usuite.tv.input

/**
 * Semantic application-level actions for TV input handling.
 *
 * TvAction represents the real intent of a user interaction, decoupled from
 * the specific hardware key that triggered it. Different screens can map
 * the same TvKeyRole to different TvActions.
 *
 * This is Level 2 of the Phase 6 TV Input Pipeline:
 * ```
 * KeyEvent → TvKeyRole → TvAction → Screen handlers
 * ```
 *
 * Actions are grouped by category for clarity:
 * - Playback: Controls video playback (play/pause, seek)
 * - Menu/Overlay: Opens menus and overlays (CC, aspect ratio, quick actions)
 * - Pagination: Page navigation in lists
 * - Focus: Focus management (move focus to specific zones)
 * - Navigation: DPAD navigation within current focus zone
 * - Channel: Live TV channel control
 * - System: System-level actions (back)
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 3.1
 *
 * @see TvKeyRole for physical key roles
 * @see TvScreenId for screen identifiers
 */
enum class TvAction {
    // ══════════════════════════════════════════════════════════════════
    // PLAYBACK ACTIONS
    // ══════════════════════════════════════════════════════════════════

    /** Toggle playback state (play/pause) */
    PLAY_PAUSE,

    /** Seek forward 10 seconds (step seek) */
    SEEK_FORWARD_10S,

    /** Seek forward 30 seconds (fast seek) */
    SEEK_FORWARD_30S,

    /** Seek backward 10 seconds (step seek) */
    SEEK_BACKWARD_10S,

    /** Seek backward 30 seconds (fast seek) */
    SEEK_BACKWARD_30S,

    // ══════════════════════════════════════════════════════════════════
    // MENU/OVERLAY ACTIONS
    // ══════════════════════════════════════════════════════════════════

    /** Open closed captions (CC) menu */
    OPEN_CC_MENU,

    /** Open aspect ratio menu */
    OPEN_ASPECT_MENU,

    /** Open quick actions panel */
    OPEN_QUICK_ACTIONS,

    /** Open live channel list overlay */
    OPEN_LIVE_LIST,

    // ══════════════════════════════════════════════════════════════════
    // PAGINATION ACTIONS
    // ══════════════════════════════════════════════════════════════════

    /** Page up in lists (jump to previous page) */
    PAGE_UP,

    /** Page down in lists (jump to next page) */
    PAGE_DOWN,

    // ══════════════════════════════════════════════════════════════════
    // FOCUS ACTIONS
    // ══════════════════════════════════════════════════════════════════

    /** Move focus to the quick actions panel */
    FOCUS_QUICK_ACTIONS,

    /** Move focus to the timeline/seek bar */
    FOCUS_TIMELINE,

    // ══════════════════════════════════════════════════════════════════
    // NAVIGATION ACTIONS
    // ══════════════════════════════════════════════════════════════════

    /** Navigate up within current focus zone */
    NAVIGATE_UP,

    /** Navigate down within current focus zone */
    NAVIGATE_DOWN,

    /** Navigate left within current focus zone */
    NAVIGATE_LEFT,

    /** Navigate right within current focus zone */
    NAVIGATE_RIGHT,

    // ══════════════════════════════════════════════════════════════════
    // CHANNEL ACTIONS
    // ══════════════════════════════════════════════════════════════════

    /** Switch to next channel (live TV) */
    CHANNEL_UP,

    /** Switch to previous channel (live TV) */
    CHANNEL_DOWN,

    // ══════════════════════════════════════════════════════════════════
    // SYSTEM ACTIONS
    // ══════════════════════════════════════════════════════════════════

    /** Go back / close overlay / exit screen */
    BACK,
    ;

    companion object {
        /**
         * Check if this action is a playback control action.
         */
        fun TvAction.isPlaybackAction(): Boolean =
            this == PLAY_PAUSE ||
                this == SEEK_FORWARD_10S ||
                this == SEEK_FORWARD_30S ||
                this == SEEK_BACKWARD_10S ||
                this == SEEK_BACKWARD_30S

        /**
         * Check if this action opens an overlay/menu.
         */
        fun TvAction.isOverlayAction(): Boolean =
            this == OPEN_CC_MENU ||
                this == OPEN_ASPECT_MENU ||
                this == OPEN_QUICK_ACTIONS ||
                this == OPEN_LIVE_LIST

        /**
         * Check if this action is a navigation action.
         */
        fun TvAction.isNavigationAction(): Boolean =
            this == NAVIGATE_UP ||
                this == NAVIGATE_DOWN ||
                this == NAVIGATE_LEFT ||
                this == NAVIGATE_RIGHT

        /**
         * Check if this action is a focus management action.
         */
        fun TvAction.isFocusAction(): Boolean = this == FOCUS_QUICK_ACTIONS || this == FOCUS_TIMELINE

        /**
         * Check if this action is a seek action.
         */
        fun TvAction.isSeekAction(): Boolean =
            this == SEEK_FORWARD_10S ||
                this == SEEK_FORWARD_30S ||
                this == SEEK_BACKWARD_10S ||
                this == SEEK_BACKWARD_30S

        /**
         * Check if this action is a channel control action.
         */
        fun TvAction.isChannelAction(): Boolean = this == CHANNEL_UP || this == CHANNEL_DOWN

        /**
         * Get the seek delta in milliseconds for seek actions.
         *
         * @return The seek delta (positive for forward, negative for backward), or null if not a seek action
         */
        fun TvAction.getSeekDeltaMs(): Long? =
            when (this) {
                SEEK_FORWARD_10S -> 10_000L
                SEEK_FORWARD_30S -> 30_000L
                SEEK_BACKWARD_10S -> -10_000L
                SEEK_BACKWARD_30S -> -30_000L
                else -> null
            }
    }
}
