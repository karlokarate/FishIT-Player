package com.chris.m3usuite.tv.input

/**
 * Semantic key roles for TV remote buttons.
 *
 * TvKeyRole provides a logical abstraction over raw Android KeyEvent codes,
 * normalizing various hardware keys from different remotes (Fire TV, Android TV,
 * etc.) into stable semantic roles.
 *
 * This is Level 1 of the Phase 6 TV Input Pipeline:
 * ```
 * KeyEvent → TvKeyRole → TvAction → Screen handlers
 * ```
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 3.1
 * Contract Reference: INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 6
 *
 * @see TvKeyMapper for converting KeyEvent to TvKeyRole
 * @see TvAction for semantic application-level commands
 */
enum class TvKeyRole {
    // DPAD Navigation
    /** Navigate up */
    DPAD_UP,

    /** Navigate down */
    DPAD_DOWN,

    /** Navigate left */
    DPAD_LEFT,

    /** Navigate right */
    DPAD_RIGHT,

    /** Confirm/select (center button, ENTER) */
    DPAD_CENTER,

    // Playback Media Keys
    /** Toggle play/pause (short press) */
    PLAY_PAUSE,

    /**
     * Long-press play/pause.
     *
     * ════════════════════════════════════════════════════════════════════════════════
     * PHASE 7 – Long-press PLAY for MiniPlayer Focus Toggle
     * ════════════════════════════════════════════════════════════════════════════════
     *
     * When MiniPlayer is visible, long-press PLAY triggers TOGGLE_MINI_PLAYER_FOCUS
     * to switch focus between PRIMARY_UI and MINI_PLAYER zones.
     *
     * **Contract Reference:**
     * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 6
     */
    PLAY_PAUSE_LONG,

    /** Fast forward / skip forward */
    FAST_FORWARD,

    /** Rewind / skip backward */
    REWIND,

    // Menu & Navigation
    /** Menu/options button */
    MENU,

    /** Back/return button */
    BACK,

    // Channel Control
    /** Channel up / next channel */
    CHANNEL_UP,

    /** Channel down / previous channel */
    CHANNEL_DOWN,

    // Information
    /** Info button (show details/metadata) */
    INFO,

    /** Guide button (EPG/program guide) */
    GUIDE,

    // Number Keys (for direct channel input)
    NUM_0,
    NUM_1,
    NUM_2,
    NUM_3,
    NUM_4,
    NUM_5,
    NUM_6,
    NUM_7,
    NUM_8,
    NUM_9,
    ;

    companion object {
        /**
         * Check if this role represents a DPAD navigation key.
         */
        fun TvKeyRole.isDpad(): Boolean =
            this == DPAD_UP || this == DPAD_DOWN || this == DPAD_LEFT || this == DPAD_RIGHT || this == DPAD_CENTER

        /**
         * Check if this role represents a media playback key.
         */
        fun TvKeyRole.isMediaKey(): Boolean = this == PLAY_PAUSE || this == PLAY_PAUSE_LONG || this == FAST_FORWARD || this == REWIND

        /**
         * Check if this role represents a number key.
         */
        fun TvKeyRole.isNumberKey(): Boolean =
            this == NUM_0 ||
                this == NUM_1 ||
                this == NUM_2 ||
                this == NUM_3 ||
                this == NUM_4 ||
                this == NUM_5 ||
                this == NUM_6 ||
                this == NUM_7 ||
                this == NUM_8 ||
                this == NUM_9

        /**
         * Get the numeric value for a number key role.
         *
         * @return The digit 0-9, or null if not a number key
         */
        fun TvKeyRole.toDigit(): Int? =
            when (this) {
                NUM_0 -> 0
                NUM_1 -> 1
                NUM_2 -> 2
                NUM_3 -> 3
                NUM_4 -> 4
                NUM_5 -> 5
                NUM_6 -> 6
                NUM_7 -> 7
                NUM_8 -> 8
                NUM_9 -> 9
                else -> null
            }
    }
}
