package com.chris.m3usuite.tv.input

/**
 * Default TV screen input configurations.
 *
 * Provides baseline key→action mappings for all major screens based on current UX.
 * These configs define how each screen responds to TV remote key events.
 *
 * This is only configuration data; it will be used by TvInputController in Task 3.
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 4.2
 *
 * Phase 6 Task 2:
 * - Default configurations for PLAYER, LIBRARY, SETTINGS, PROFILE_GATE
 * - Basic mappings based on current UX patterns
 * - Does NOT wire to FocusKit or TvInputController (Task 3)
 *
 * @see TvScreenInputConfig for the data model
 * @see TvInputConfigDsl for the DSL syntax
 */
object DefaultTvScreenConfigs {
    /**
     * All default screen configurations.
     *
     * Each screen has specific mappings optimized for its UX:
     * - PLAYER: Seek controls, quick actions, play/pause
     * - LIBRARY: Page navigation via FF/RW, standard DPAD navigation
     * - SETTINGS: Standard DPAD navigation only
     * - PROFILE_GATE: DPAD navigation for profile selection
     *
     * Screens not explicitly configured will use empty configs (all keys unmapped).
     */
    val all: Map<TvScreenId, TvScreenInputConfig> =
        tvInputConfig {
            // ══════════════════════════════════════════════════════════════════
            // PLAYER SCREEN
            // ══════════════════════════════════════════════════════════════════
            // The player has the most complex mappings:
            // - FF/RW → 30-second seek
            // - DPAD_LEFT/RIGHT → 10-second seek (within controls)
            // - DPAD_UP → Focus quick actions panel
            // - MENU → Open quick actions
            // - PLAY_PAUSE → Toggle playback
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.PLAYER) {
                // Media keys → Seek
                on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                on(TvKeyRole.REWIND) mapsTo TvAction.SEEK_BACKWARD_30S

                // DPAD → Navigation within player (or seek when controls hidden)
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.FOCUS_QUICK_ACTIONS
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.FOCUS_TIMELINE
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT
                on(TvKeyRole.DPAD_CENTER) mapsTo TvAction.PLAY_PAUSE

                // Menu/System
                on(TvKeyRole.MENU) mapsTo TvAction.OPEN_QUICK_ACTIONS
                on(TvKeyRole.BACK) mapsTo TvAction.BACK
                on(TvKeyRole.PLAY_PAUSE) mapsTo TvAction.PLAY_PAUSE

                // Channel control (for live TV)
                on(TvKeyRole.CHANNEL_UP) mapsTo TvAction.CHANNEL_UP
                on(TvKeyRole.CHANNEL_DOWN) mapsTo TvAction.CHANNEL_DOWN

                // Info button → Quick actions (show metadata)
                on(TvKeyRole.INFO) mapsTo TvAction.OPEN_QUICK_ACTIONS
            }

            // ══════════════════════════════════════════════════════════════════
            // LIBRARY SCREEN
            // ══════════════════════════════════════════════════════════════════
            // The library uses FF/RW for page navigation:
            // - FF/RW → Page up/down in content lists
            // - DPAD → Standard FocusKit navigation
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.LIBRARY) {
                // Media keys → Page navigation
                on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.PAGE_DOWN
                on(TvKeyRole.REWIND) mapsTo TvAction.PAGE_UP

                // DPAD → Navigation
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.NAVIGATE_UP
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.NAVIGATE_DOWN
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT
                on(TvKeyRole.DPAD_CENTER) mapsTo null // Let FocusKit handle selection

                // Menu/System
                on(TvKeyRole.MENU) mapsTo null // No action on library menu
                on(TvKeyRole.BACK) mapsTo TvAction.BACK
            }

            // ══════════════════════════════════════════════════════════════════
            // SETTINGS SCREEN
            // ══════════════════════════════════════════════════════════════════
            // Settings uses standard DPAD navigation only:
            // - DPAD → Navigate settings list
            // - No special media key mappings
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.SETTINGS) {
                // DPAD → Navigation
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.NAVIGATE_UP
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.NAVIGATE_DOWN
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT
                on(TvKeyRole.DPAD_CENTER) mapsTo null // Let FocusKit handle selection

                // System
                on(TvKeyRole.BACK) mapsTo TvAction.BACK
            }

            // ══════════════════════════════════════════════════════════════════
            // PROFILE GATE SCREEN
            // ══════════════════════════════════════════════════════════════════
            // Profile gate uses DPAD for grid/PIN navigation:
            // - DPAD → Navigate profile grid or PIN keypad
            // - CENTER → Select profile
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.PROFILE_GATE) {
                // DPAD → Navigation in profile grid/PIN keypad
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.NAVIGATE_UP
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.NAVIGATE_DOWN
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT
                on(TvKeyRole.DPAD_CENTER) mapsTo null // Let FocusKit handle selection

                // System
                on(TvKeyRole.BACK) mapsTo TvAction.BACK

                // Number keys for PIN entry
                on(TvKeyRole.NUM_0) mapsTo null // Handled by PIN input component
                on(TvKeyRole.NUM_1) mapsTo null
                on(TvKeyRole.NUM_2) mapsTo null
                on(TvKeyRole.NUM_3) mapsTo null
                on(TvKeyRole.NUM_4) mapsTo null
                on(TvKeyRole.NUM_5) mapsTo null
                on(TvKeyRole.NUM_6) mapsTo null
                on(TvKeyRole.NUM_7) mapsTo null
                on(TvKeyRole.NUM_8) mapsTo null
                on(TvKeyRole.NUM_9) mapsTo null
            }

            // ══════════════════════════════════════════════════════════════════
            // START SCREEN (HOME)
            // ══════════════════════════════════════════════════════════════════
            // Start screen uses standard navigation similar to library:
            // - DPAD → Navigate content
            // - FF/RW → Page navigation
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.START) {
                // Media keys → Page navigation
                on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.PAGE_DOWN
                on(TvKeyRole.REWIND) mapsTo TvAction.PAGE_UP

                // DPAD → Navigation
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.NAVIGATE_UP
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.NAVIGATE_DOWN
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT
                on(TvKeyRole.DPAD_CENTER) mapsTo null // Let FocusKit handle selection

                // System
                on(TvKeyRole.BACK) mapsTo TvAction.BACK
            }

            // ══════════════════════════════════════════════════════════════════
            // DETAIL SCREEN
            // ══════════════════════════════════════════════════════════════════
            // Detail screen uses standard navigation:
            // - DPAD → Navigate detail content
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.DETAIL) {
                // DPAD → Navigation
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.NAVIGATE_UP
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.NAVIGATE_DOWN
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT
                on(TvKeyRole.DPAD_CENTER) mapsTo null // Let FocusKit handle selection

                // System
                on(TvKeyRole.BACK) mapsTo TvAction.BACK
            }
        }

    /**
     * Get the configuration for a specific screen.
     *
     * @param screenId The screen to get config for
     * @return The screen's configuration, or an empty config if not defined
     */
    fun forScreen(screenId: TvScreenId): TvScreenInputConfig = all.getOrEmpty(screenId)

    /**
     * Resolve an action for a screen with full filtering.
     *
     * This is a convenience method that:
     * 1. Gets the config for the screen
     * 2. Looks up the raw action for the role
     * 3. Applies Kids Mode filtering
     * 4. Applies overlay blocking
     *
     * @param screenId The screen ID
     * @param role The key role to resolve
     * @param ctx The screen context
     * @return The resolved action, or null if blocked/unmapped
     */
    fun resolve(
        screenId: TvScreenId,
        role: TvKeyRole,
        ctx: TvScreenContext,
    ): TvAction? = all.resolve(screenId, role, ctx)
}
