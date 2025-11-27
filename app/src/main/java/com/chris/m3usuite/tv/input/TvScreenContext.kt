package com.chris.m3usuite.tv.input

/**
 * Context information for TV input handling on a specific screen.
 *
 * TvScreenContext provides the context needed by TvInputController to
 * correctly route and process key events. Each screen provides its own
 * context describing its state and requirements.
 *
 * This is a pure data class with no behavior. The actual input processing
 * logic lives in TvInputController (to be implemented in Task 3).
 *
 * Contract Reference:
 * - INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 4.2, 5.1
 * - GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md
 *
 * @property screenId The unique identifier for the current screen
 * @property isPlayerScreen True if this is the internal player screen
 * @property isLive True if playing live TV content (affects seek behavior)
 * @property isKidProfile True if a kid profile is active (restricts certain actions)
 * @property hasBlockingOverlay True if a blocking overlay is currently shown
 *
 * @see TvScreenId for screen identifiers
 * @see TvAction for semantic actions
 */
data class TvScreenContext(
    /** The unique identifier for the current screen */
    val screenId: TvScreenId,
    /** True if this is the internal player screen */
    val isPlayerScreen: Boolean = false,
    /** True if playing live TV content (affects seek behavior) */
    val isLive: Boolean = false,
    /** True if a kid profile is active (restricts certain actions) */
    val isKidProfile: Boolean = false,
    /** True if a blocking overlay is currently shown */
    val hasBlockingOverlay: Boolean = false,
) {
    companion object {
        /**
         * Create a context for the player screen.
         *
         * @param isLive True if playing live TV content
         * @param isKidProfile True if a kid profile is active
         * @param hasBlockingOverlay True if a blocking overlay is shown
         */
        fun player(
            isLive: Boolean = false,
            isKidProfile: Boolean = false,
            hasBlockingOverlay: Boolean = false,
        ): TvScreenContext =
            TvScreenContext(
                screenId = TvScreenId.PLAYER,
                isPlayerScreen = true,
                isLive = isLive,
                isKidProfile = isKidProfile,
                hasBlockingOverlay = hasBlockingOverlay,
            )

        /**
         * Create a context for the library screen.
         *
         * @param isKidProfile True if a kid profile is active
         * @param hasBlockingOverlay True if a blocking overlay is shown
         */
        fun library(
            isKidProfile: Boolean = false,
            hasBlockingOverlay: Boolean = false,
        ): TvScreenContext =
            TvScreenContext(
                screenId = TvScreenId.LIBRARY,
                isPlayerScreen = false,
                isLive = false,
                isKidProfile = isKidProfile,
                hasBlockingOverlay = hasBlockingOverlay,
            )

        /**
         * Create a context for the start/home screen.
         * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: HOME / BROWSE / LIBRARY SCREENS
         *
         * @param isKidProfile True if a kid profile is active
         */
        fun start(isKidProfile: Boolean = false): TvScreenContext =
            TvScreenContext(
                screenId = TvScreenId.START,
                isPlayerScreen = false,
                isLive = false,
                isKidProfile = isKidProfile,
                hasBlockingOverlay = false,
            )

        /**
         * Create a context for the settings screen.
         *
         * @param isKidProfile True if a kid profile is active
         */
        fun settings(isKidProfile: Boolean = false): TvScreenContext =
            TvScreenContext(
                screenId = TvScreenId.SETTINGS,
                isPlayerScreen = false,
                isLive = false,
                isKidProfile = isKidProfile,
                hasBlockingOverlay = false,
            )

        /**
         * Create a context for the profile gate screen.
         */
        fun profileGate(): TvScreenContext =
            TvScreenContext(
                screenId = TvScreenId.PROFILE_GATE,
                isPlayerScreen = false,
                isLive = false,
                isKidProfile = false,
                hasBlockingOverlay = true, // Profile gate is a blocking overlay
            )

        /**
         * Create a context for a detail screen.
         *
         * @param isKidProfile True if a kid profile is active
         */
        fun detail(isKidProfile: Boolean = false): TvScreenContext =
            TvScreenContext(
                screenId = TvScreenId.DETAIL,
                isPlayerScreen = false,
                isLive = false,
                isKidProfile = isKidProfile,
                hasBlockingOverlay = false,
            )

        /**
         * Create a context for the mini-player / PIP mode.
         * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: GLOBAL PIP / MINIPLAYER MODE
         *
         * @param isKidProfile True if a kid profile is active
         */
        fun miniPlayer(isKidProfile: Boolean = false): TvScreenContext =
            TvScreenContext(
                screenId = TvScreenId.MINI_PLAYER,
                isPlayerScreen = true, // Mini-player is still a player context
                isLive = false,
                isKidProfile = isKidProfile,
                hasBlockingOverlay = false,
            )

        /**
         * Create a context for a blocking overlay.
         *
         * @param screenId The overlay's screen ID
         * @param isKidProfile True if a kid profile is active
         */
        fun blockingOverlay(
            screenId: TvScreenId,
            isKidProfile: Boolean = false,
        ): TvScreenContext =
            TvScreenContext(
                screenId = screenId,
                isPlayerScreen = false,
                isLive = false,
                isKidProfile = isKidProfile,
                hasBlockingOverlay = true,
            )

        /**
         * Create a default context for an unknown screen.
         */
        fun unknown(): TvScreenContext =
            TvScreenContext(
                screenId = TvScreenId.UNKNOWN,
                isPlayerScreen = false,
                isLive = false,
                isKidProfile = false,
                hasBlockingOverlay = false,
            )
    }
}

/**
 * Extension function to build TvScreenContext from SIP InternalPlayerUiState.
 *
 * Phase 6 Task 3: This function converts the player's UI state into a
 * TvScreenContext that can be used by the TV input pipeline.
 *
 * The mapping is:
 * - screenId = PLAYER (always, since this is for the player)
 * - isPlayerScreen = true
 * - isLive = state.isLive (computed property: playbackType == PlaybackType.LIVE)
 * - isKidProfile = state.kidActive
 * - hasBlockingOverlay = state.hasBlockingOverlay (computed property)
 *
 * @receiver The InternalPlayerUiState from which to derive context
 * @return A TvScreenContext representing the player's current state
 */
fun com.chris.m3usuite.player.internal.state.InternalPlayerUiState.toTvScreenContext(): TvScreenContext =
    TvScreenContext(
        screenId = TvScreenId.PLAYER,
        isPlayerScreen = true,
        isLive = this.isLive,
        isKidProfile = this.kidActive,
        hasBlockingOverlay = this.hasBlockingOverlay,
    )
