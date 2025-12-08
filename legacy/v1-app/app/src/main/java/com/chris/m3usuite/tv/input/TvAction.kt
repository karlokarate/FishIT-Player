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
 * - Library/Browse: Content browsing actions (open details, fast scroll)
 * - Detail Screen: Detail screen actions (episode navigation)
 * - Settings: Settings screen actions
 * - Profile Gate: Profile selection actions
 * - Mini Player/PIP: Picture-in-picture actions
 * - Pagination: Page navigation in lists
 * - Focus: Focus management (move focus to specific zones)
 * - Navigation: DPAD navigation within current focus zone
 * - Channel: Live TV channel control
 * - System: System-level actions (back, exit to home, global search)
 *
 * Contract Reference:
 * - INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 3.1
 * - GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md
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

    /**
     * Open the player menu (options: aspect, CC, speed, sleep timer, etc.).
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: MENU → Player options
     */
    OPEN_PLAYER_MENU,

    // ══════════════════════════════════════════════════════════════════
    // LIBRARY/BROWSE ACTIONS
    // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: HOME/BROWSE/LIBRARY SCREENS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Open details for the focused content item.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: CENTER → Open details
     */
    OPEN_DETAILS,

    /**
     * Enter Row Fast Scroll Mode (forward).
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: FF → Enter Row Fast Scroll Mode
     */
    ROW_FAST_SCROLL_FORWARD,

    /**
     * Row Fast Scroll Mode (backward).
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: RW → Row Fast Scroll backwards
     */
    ROW_FAST_SCROLL_BACKWARD,

    /**
     * Start playback for the focused item using resume logic.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: PLAY/PAUSE → Start focused item (resume logic)
     */
    PLAY_FOCUSED_RESUME,

    /**
     * Open filter/sort menu.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: MENU (short press) → Filters/Sort
     */
    OPEN_FILTER_SORT,

    // ══════════════════════════════════════════════════════════════════
    // DETAIL SCREEN ACTIONS
    // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: DETAIL SCREEN
    // ══════════════════════════════════════════════════════════════════

    /**
     * Navigate to next episode.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: FF → Next episode
     */
    NEXT_EPISODE,

    /**
     * Navigate to previous episode.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: RW → Previous episode
     */
    PREVIOUS_EPISODE,

    /**
     * Open detail actions menu (Trailer, Add to list, etc.).
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: MENU → Detail actions
     */
    OPEN_DETAIL_MENU,

    // ══════════════════════════════════════════════════════════════════
    // SETTINGS SCREEN ACTIONS
    // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: SETTINGS SCREEN
    // ══════════════════════════════════════════════════════════════════

    /**
     * Activate the focused setting option.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: CENTER → Activate option
     */
    ACTIVATE_FOCUSED_SETTING,

    /**
     * Switch to next settings tab.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: FF → Switch settings tabs (future)
     */
    SWITCH_SETTINGS_TAB_NEXT,

    /**
     * Switch to previous settings tab.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: RW → Switch settings tabs (future)
     */
    SWITCH_SETTINGS_TAB_PREVIOUS,

    /**
     * Open advanced settings (Xtream, Telegram login, etc.).
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: MENU → Advanced Settings
     */
    OPEN_ADVANCED_SETTINGS,

    // ══════════════════════════════════════════════════════════════════
    // PROFILE GATE ACTIONS
    // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: PROFILE GATE SCREEN
    // ══════════════════════════════════════════════════════════════════

    /**
     * Select the focused profile.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: CENTER → Select profile
     */
    SELECT_PROFILE,

    /**
     * Open profile options menu.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: MENU → Profile options
     */
    OPEN_PROFILE_OPTIONS,

    // ══════════════════════════════════════════════════════════════════
    // MINI PLAYER / PIP ACTIONS
    // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: GLOBAL PIP / MINIPLAYER MODE
    // ══════════════════════════════════════════════════════════════════

    /**
     * Seek forward in mini-player/PIP.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: FF → Seek in mini-player
     */
    PIP_SEEK_FORWARD,

    /**
     * Seek backward in mini-player/PIP.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: RW → Seek in mini-player
     */
    PIP_SEEK_BACKWARD,

    /**
     * Toggle play/pause in mini-player/PIP.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: PLAY/PAUSE → Toggle playback
     */
    PIP_TOGGLE_PLAY_PAUSE,

    /**
     * Enter PIP resize mode.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: MENU (long press) → Enter PIP Resize Mode
     */
    PIP_ENTER_RESIZE_MODE,

    /**
     * Confirm PIP size/position.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: CENTER (resize mode) → Confirm size/position
     */
    PIP_CONFIRM_RESIZE,

    /**
     * Move PIP window left (resize mode).
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: DPAD (resize mode) → Move
     */
    PIP_MOVE_LEFT,

    /**
     * Move PIP window right (resize mode).
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: DPAD (resize mode) → Move
     */
    PIP_MOVE_RIGHT,

    /**
     * Move PIP window up (resize mode).
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: DPAD (resize mode) → Move
     */
    PIP_MOVE_UP,

    /**
     * Move PIP window down (resize mode).
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: DPAD (resize mode) → Move
     */
    PIP_MOVE_DOWN,

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

    /**
     * Exit to home screen (global double BACK behavior).
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: Global double BACK = Exit to Home
     *
     * Note: Detection of double-BACK is handled at the GlobalTvInputHost layer.
     * This action is dispatched when double-BACK threshold is met.
     */
    EXIT_TO_HOME,

    /**
     * Open global search.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: MENU (long press) → Global Search
     */
    OPEN_GLOBAL_SEARCH,

    // ══════════════════════════════════════════════════════════════════
    // PHASE 7 – MINI PLAYER FOCUS TOGGLE
    // Contract Reference: INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 6
    // ══════════════════════════════════════════════════════════════════

    /**
     * Toggle focus between MiniPlayer and primary UI.
     * Triggered by long-press PLAY when MiniPlayer is visible.
     *
     * **Behavior:**
     * - If MiniPlayer has focus → move focus to PRIMARY_UI
     * - If PRIMARY_UI has focus → move focus to MINI_PLAYER
     *
     * **Contract Reference:**
     * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 6
     */
    TOGGLE_MINI_PLAYER_FOCUS,
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
                this == SEEK_BACKWARD_30S ||
                this == PLAY_FOCUSED_RESUME

        /**
         * Check if this action opens an overlay/menu.
         */
        fun TvAction.isOverlayAction(): Boolean =
            this == OPEN_CC_MENU ||
                this == OPEN_ASPECT_MENU ||
                this == OPEN_QUICK_ACTIONS ||
                this == OPEN_LIVE_LIST ||
                this == OPEN_PLAYER_MENU ||
                this == OPEN_FILTER_SORT ||
                this == OPEN_DETAIL_MENU ||
                this == OPEN_ADVANCED_SETTINGS ||
                this == OPEN_PROFILE_OPTIONS ||
                this == OPEN_GLOBAL_SEARCH

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
        fun TvAction.isFocusAction(): Boolean =
            this == FOCUS_QUICK_ACTIONS ||
                this == FOCUS_TIMELINE ||
                this == TOGGLE_MINI_PLAYER_FOCUS

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
         * Check if this action is a PIP-specific action.
         */
        fun TvAction.isPipAction(): Boolean =
            this == PIP_SEEK_FORWARD ||
                this == PIP_SEEK_BACKWARD ||
                this == PIP_TOGGLE_PLAY_PAUSE ||
                this == PIP_ENTER_RESIZE_MODE ||
                this == PIP_CONFIRM_RESIZE ||
                this == PIP_MOVE_LEFT ||
                this == PIP_MOVE_RIGHT ||
                this == PIP_MOVE_UP ||
                this == PIP_MOVE_DOWN

        /**
         * Check if this action is a detail screen action.
         */
        fun TvAction.isDetailAction(): Boolean =
            this == OPEN_DETAILS ||
                this == NEXT_EPISODE ||
                this == PREVIOUS_EPISODE ||
                this == OPEN_DETAIL_MENU

        /**
         * Check if this action is a settings screen action.
         */
        fun TvAction.isSettingsAction(): Boolean =
            this == ACTIVATE_FOCUSED_SETTING ||
                this == SWITCH_SETTINGS_TAB_NEXT ||
                this == SWITCH_SETTINGS_TAB_PREVIOUS ||
                this == OPEN_ADVANCED_SETTINGS

        /**
         * Check if this action is a profile gate action.
         */
        fun TvAction.isProfileGateAction(): Boolean =
            this == SELECT_PROFILE ||
                this == OPEN_PROFILE_OPTIONS

        /**
         * Check if this action is a library/browse action.
         */
        fun TvAction.isLibraryAction(): Boolean =
            this == OPEN_DETAILS ||
                this == ROW_FAST_SCROLL_FORWARD ||
                this == ROW_FAST_SCROLL_BACKWARD ||
                this == PLAY_FOCUSED_RESUME ||
                this == OPEN_FILTER_SORT

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
