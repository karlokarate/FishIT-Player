package com.chris.m3usuite.tv.input

/**
 * Screen identifiers for the TV input system.
 *
 * Every screen in the app receives a unique stable ID that can be used
 * for per-screen TV input configuration. This allows different screens
 * to handle the same key role differently (e.g., DPAD_LEFT seeks in player
 * but navigates in browse screens).
 *
 * Contract Reference:
 * - INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 4.1
 * - GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md
 *
 * @see TvScreenContext for per-screen input context
 * @see TvAction for semantic actions
 */
enum class TvScreenId {
    /** Start/launch screen */
    START,

    /** Main library/home screen */
    LIBRARY,

    /** Internal media player screen */
    PLAYER,

    /** Settings screen */
    SETTINGS,

    /** Detail screen (VOD/Series/Live) */
    DETAIL,

    /** Profile selection/gate screen */
    PROFILE_GATE,

    /**
     * Mini-player / Picture-in-Picture mode.
     * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: GLOBAL PIP / MINIPLAYER MODE
     */
    MINI_PLAYER,

    /** Live channel list overlay */
    LIVE_LIST,

    /** Closed captions menu overlay */
    CC_MENU,

    /** Aspect ratio menu overlay */
    ASPECT_MENU,

    /** Search screen */
    SEARCH,

    /** Telegram content browser */
    TELEGRAM_BROWSER,

    /** Quick actions panel (player overlay) */
    QUICK_ACTIONS,

    /** EPG/Guide overlay */
    EPG_GUIDE,

    /** Error dialog overlay */
    ERROR_DIALOG,

    /** Sleep timer dialog */
    SLEEP_TIMER,

    /** Speed selection dialog */
    SPEED_DIALOG,

    /** Subtitle/tracks selection dialog */
    TRACKS_DIALOG,

    /** Unknown/default screen */
    UNKNOWN,
    ;

    /**
     * Check if this screen is a dialog/overlay rather than a full screen.
     */
    fun isOverlay(): Boolean =
        this == LIVE_LIST ||
            this == CC_MENU ||
            this == ASPECT_MENU ||
            this == QUICK_ACTIONS ||
            this == EPG_GUIDE ||
            this == ERROR_DIALOG ||
            this == SLEEP_TIMER ||
            this == SPEED_DIALOG ||
            this == TRACKS_DIALOG

    /**
     * Check if this screen is a blocking overlay that restricts input.
     *
     * When a blocking overlay is active, only navigation within the overlay
     * and BACK (to close) are allowed.
     */
    fun isBlockingOverlay(): Boolean =
        this == CC_MENU ||
            this == ASPECT_MENU ||
            this == LIVE_LIST ||
            this == SLEEP_TIMER ||
            this == SPEED_DIALOG ||
            this == TRACKS_DIALOG ||
            this == ERROR_DIALOG ||
            this == PROFILE_GATE
}
