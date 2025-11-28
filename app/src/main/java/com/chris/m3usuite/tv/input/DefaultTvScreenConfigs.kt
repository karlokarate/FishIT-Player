package com.chris.m3usuite.tv.input

/**
 * Default TV screen input configurations.
 *
 * Provides baseline key→action mappings for all major screens based on the
 * GLOBAL_TV_REMOTE_BEHAVIOR_MAP specification. These configs define how each
 * screen responds to TV remote key events.
 *
 * This is only configuration data; it will be used by TvInputController.
 *
 * Contract Reference:
 * - INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 4.2
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 6
 * - docs/GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md
 *
 * Phase 6 Task 4:
 * - Aligned configurations with GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md
 * - PLAYER, LIBRARY, START, DETAIL, SETTINGS, PROFILE_GATE, MINI_PLAYER
 *
 * Phase 7 Task 2:
 * - Added PLAY_PAUSE_LONG → TOGGLE_MINI_PLAYER_FOCUS mapping
 *
 * @see TvScreenInputConfig for the data model
 * @see TvInputConfigDsl for the DSL syntax
 */
object DefaultTvScreenConfigs {
    /**
     * All default screen configurations.
     *
     * Each screen has specific mappings per GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md:
     * - PLAYER: Playback mode with seek, play/pause, quick actions
     * - LIBRARY/START: Content browsing with details, fast scroll, filter/sort
     * - DETAIL: Detail screen with episode navigation, play/resume
     * - SETTINGS: Settings navigation with tab switching (future)
     * - PROFILE_GATE: Profile selection with options menu
     * - MINI_PLAYER: PIP mode with seek, resize, move
     *
     * Phase 7 Addition:
     * - PLAY_PAUSE_LONG → TOGGLE_MINI_PLAYER_FOCUS on LIBRARY, START screens
     *
     * Screens not explicitly configured will use empty configs (all keys unmapped).
     */
    val all: Map<TvScreenId, TvScreenInputConfig> =
        tvInputConfig {
            // ══════════════════════════════════════════════════════════════════
            // PLAYER SCREEN (Playback Mode default)
            // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: PLAYER SCREEN - Context A
            // ══════════════════════════════════════════════════════════════════
            // - DPAD_CENTER → PLAY_PAUSE (exception: playback mode)
            // - PLAY_PAUSE → PLAY_PAUSE
            // - DPAD_LEFT/RIGHT → SEEK_BACKWARD_10S / SEEK_FORWARD_10S
            // - DPAD_UP → OPEN_QUICK_ACTIONS (QuickActions panel)
            // - DPAD_DOWN → FOCUS_TIMELINE (reveal controls/timeline)
            // - FF / RW → SEEK_FORWARD_30S / SEEK_BACKWARD_30S
            // - MENU → OPEN_PLAYER_MENU (player options)
            // - BACK → BACK (later participates in double BACK behavior)
            // - PLAY_PAUSE_LONG → TOGGLE_MINI_PLAYER_FOCUS (Phase 7)
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.PLAYER) {
                // Center → Play/Pause (per spec: CENTER → Play/Pause toggle in playback mode)
                on(TvKeyRole.DPAD_CENTER) mapsTo TvAction.PLAY_PAUSE

                // Play/Pause media key
                on(TvKeyRole.PLAY_PAUSE) mapsTo TvAction.PLAY_PAUSE

                // Long-press PLAY → Toggle MiniPlayer focus (Phase 7)
                on(TvKeyRole.PLAY_PAUSE_LONG) mapsTo TvAction.TOGGLE_MINI_PLAYER_FOCUS

                // DPAD Left/Right → 10s step seek (per spec)
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.SEEK_BACKWARD_10S
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.SEEK_FORWARD_10S

                // DPAD Up → Open QuickActions (per spec)
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.FOCUS_QUICK_ACTIONS

                // DPAD Down → Reveal controls/timeline (per spec)
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.FOCUS_TIMELINE

                // FF/RW → 30s seek (per spec)
                on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
                on(TvKeyRole.REWIND) mapsTo TvAction.SEEK_BACKWARD_30S

                // Menu → Player options (per spec)
                on(TvKeyRole.MENU) mapsTo TvAction.OPEN_PLAYER_MENU

                // Back → BACK (will later participate in double BACK behavior)
                on(TvKeyRole.BACK) mapsTo TvAction.BACK

                // Channel control (for live TV)
                on(TvKeyRole.CHANNEL_UP) mapsTo TvAction.CHANNEL_UP
                on(TvKeyRole.CHANNEL_DOWN) mapsTo TvAction.CHANNEL_DOWN

                // Info button → Quick actions (show metadata)
                on(TvKeyRole.INFO) mapsTo TvAction.OPEN_QUICK_ACTIONS
            }

            // ══════════════════════════════════════════════════════════════════
            // LIBRARY SCREEN
            // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: HOME / BROWSE / LIBRARY SCREENS
            // ══════════════════════════════════════════════════════════════════
            // - DPAD_LEFT/RIGHT/UP/DOWN → NAVIGATE_* actions (tile-by-tile, row switch)
            // - CENTER → OPEN_DETAILS
            // - FF / RW → ROW_FAST_SCROLL_FORWARD / ROW_FAST_SCROLL_BACKWARD
            // - PLAY_PAUSE → PLAY_FOCUSED_RESUME (resume-point playback)
            // - PLAY_PAUSE_LONG → TOGGLE_MINI_PLAYER_FOCUS (Phase 7)
            // - MENU (short) → OPEN_FILTER_SORT
            // - MENU (long) → OPEN_GLOBAL_SEARCH (TODO: long-press handled elsewhere)
            // - BACK → BACK
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.LIBRARY) {
                // DPAD → Navigation (tile-by-tile, row switch)
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.NAVIGATE_UP
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.NAVIGATE_DOWN
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT

                // Center → Open details (per spec)
                on(TvKeyRole.DPAD_CENTER) mapsTo TvAction.OPEN_DETAILS

                // FF/RW → Row Fast Scroll Mode (per spec)
                on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.ROW_FAST_SCROLL_FORWARD
                on(TvKeyRole.REWIND) mapsTo TvAction.ROW_FAST_SCROLL_BACKWARD

                // Play/Pause → Start focused item with resume logic (per spec)
                on(TvKeyRole.PLAY_PAUSE) mapsTo TvAction.PLAY_FOCUSED_RESUME

                // Long-press PLAY → Toggle MiniPlayer focus (Phase 7)
                on(TvKeyRole.PLAY_PAUSE_LONG) mapsTo TvAction.TOGGLE_MINI_PLAYER_FOCUS

                // Menu (short) → Filters/Sort (per spec)
                // TODO: Long press MENU → OPEN_GLOBAL_SEARCH is handled at host layer
                on(TvKeyRole.MENU) mapsTo TvAction.OPEN_FILTER_SORT

                // Back → BACK
                on(TvKeyRole.BACK) mapsTo TvAction.BACK
            }

            // ══════════════════════════════════════════════════════════════════
            // START SCREEN (HOME)
            // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: HOME / BROWSE / LIBRARY SCREENS
            // Same behavior as LIBRARY
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.START) {
                // DPAD → Navigation
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.NAVIGATE_UP
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.NAVIGATE_DOWN
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT

                // Center → Open details (per spec)
                on(TvKeyRole.DPAD_CENTER) mapsTo TvAction.OPEN_DETAILS

                // FF/RW → Row Fast Scroll Mode (per spec)
                on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.ROW_FAST_SCROLL_FORWARD
                on(TvKeyRole.REWIND) mapsTo TvAction.ROW_FAST_SCROLL_BACKWARD

                // Play/Pause → Start focused item with resume logic (per spec)
                on(TvKeyRole.PLAY_PAUSE) mapsTo TvAction.PLAY_FOCUSED_RESUME

                // Long-press PLAY → Toggle MiniPlayer focus (Phase 7)
                on(TvKeyRole.PLAY_PAUSE_LONG) mapsTo TvAction.TOGGLE_MINI_PLAYER_FOCUS

                // Menu (short) → Filters/Sort (per spec)
                // TODO: Long press MENU → OPEN_GLOBAL_SEARCH is handled at host layer
                on(TvKeyRole.MENU) mapsTo TvAction.OPEN_FILTER_SORT

                // Back → BACK
                on(TvKeyRole.BACK) mapsTo TvAction.BACK
            }

            // ══════════════════════════════════════════════════════════════════
            // DETAIL SCREEN
            // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: DETAIL SCREEN
            // ══════════════════════════════════════════════════════════════════
            // - CENTER / PLAY_PAUSE → PLAY_FOCUSED_RESUME (Play/resume)
            // - FF / RW → NEXT_EPISODE / PREVIOUS_EPISODE
            // - MENU → OPEN_DETAIL_MENU (Detail actions)
            // - DPAD_NAV → NAVIGATE_* for the UI
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.DETAIL) {
                // DPAD → Navigation (episode list, buttons, metadata)
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.NAVIGATE_UP
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.NAVIGATE_DOWN
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT

                // Center → Play/resume (per spec)
                on(TvKeyRole.DPAD_CENTER) mapsTo TvAction.PLAY_FOCUSED_RESUME

                // Play/Pause → Play/resume (per spec)
                on(TvKeyRole.PLAY_PAUSE) mapsTo TvAction.PLAY_FOCUSED_RESUME

                // FF/RW → Next/previous episode (per spec)
                on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.NEXT_EPISODE
                on(TvKeyRole.REWIND) mapsTo TvAction.PREVIOUS_EPISODE

                // Menu → Detail actions (Trailer, Add to list, etc.) (per spec)
                on(TvKeyRole.MENU) mapsTo TvAction.OPEN_DETAIL_MENU

                // Back → BACK
                on(TvKeyRole.BACK) mapsTo TvAction.BACK
            }

            // ══════════════════════════════════════════════════════════════════
            // SETTINGS SCREEN
            // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: SETTINGS SCREEN
            // ══════════════════════════════════════════════════════════════════
            // - CENTER → ACTIVATE_FOCUSED_SETTING
            // - DPAD_NAV → NAVIGATE_*
            // - PLAY_PAUSE → no-op (no binding)
            // - FF / RW → SWITCH_SETTINGS_TAB_NEXT/PREVIOUS (preparation)
            // - MENU → OPEN_ADVANCED_SETTINGS
            // - BACK → BACK
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.SETTINGS) {
                // DPAD → Navigation
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.NAVIGATE_UP
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.NAVIGATE_DOWN
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT

                // Center → Activate option (per spec)
                on(TvKeyRole.DPAD_CENTER) mapsTo TvAction.ACTIVATE_FOCUSED_SETTING

                // Play/Pause → no-op (per spec: no binding)
                // Explicitly not mapped

                // FF/RW → Switch settings tabs (preparation for future)
                on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SWITCH_SETTINGS_TAB_NEXT
                on(TvKeyRole.REWIND) mapsTo TvAction.SWITCH_SETTINGS_TAB_PREVIOUS

                // Menu → Advanced Settings (per spec)
                on(TvKeyRole.MENU) mapsTo TvAction.OPEN_ADVANCED_SETTINGS

                // Back → BACK
                on(TvKeyRole.BACK) mapsTo TvAction.BACK
            }

            // ══════════════════════════════════════════════════════════════════
            // PROFILE GATE SCREEN
            // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: PROFILE GATE SCREEN
            // ══════════════════════════════════════════════════════════════════
            // - CENTER → SELECT_PROFILE
            // - DPAD_NAV → NAVIGATE_*
            // - MENU → OPEN_PROFILE_OPTIONS
            // - BACK → BACK
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.PROFILE_GATE) {
                // DPAD → Navigation in profile grid/PIN keypad
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.NAVIGATE_UP
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.NAVIGATE_DOWN
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.NAVIGATE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.NAVIGATE_RIGHT

                // Center → Select profile (per spec)
                on(TvKeyRole.DPAD_CENTER) mapsTo TvAction.SELECT_PROFILE

                // Menu → Profile options (per spec)
                on(TvKeyRole.MENU) mapsTo TvAction.OPEN_PROFILE_OPTIONS

                // Back → BACK
                on(TvKeyRole.BACK) mapsTo TvAction.BACK

                // Number keys for PIN entry (handled by PIN input component)
                on(TvKeyRole.NUM_0) mapsTo null
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
            // MINI PLAYER / PIP MODE
            // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP: GLOBAL PIP / MINIPLAYER MODE
            // ══════════════════════════════════════════════════════════════════
            // - FF/RW → PIP_SEEK_FORWARD / PIP_SEEK_BACKWARD (Seek in PIP)
            // - PLAY_PAUSE → PIP_TOGGLE_PLAY_PAUSE
            // - MENU → PIP_ENTER_RESIZE_MODE (long press → resize mode)
            // - DPAD → PIP_MOVE_* (in resize mode) or NAVIGATE_* (normal mode)
            // - CENTER → PIP_CONFIRM_RESIZE (in resize mode)
            // - BACK → BACK
            //
            // Note: Resize mode is a sub-context. The host layer will switch
            // actions based on whether resize mode is active.
            // ══════════════════════════════════════════════════════════════════
            screen(TvScreenId.MINI_PLAYER) {
                // FF/RW → Seek in mini-player (per spec)
                on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.PIP_SEEK_FORWARD
                on(TvKeyRole.REWIND) mapsTo TvAction.PIP_SEEK_BACKWARD

                // Play/Pause → Toggle PIP playback (per spec)
                on(TvKeyRole.PLAY_PAUSE) mapsTo TvAction.PIP_TOGGLE_PLAY_PAUSE

                // Menu → Enter PIP Resize Mode (per spec: long press)
                // Note: Short press can also toggle resize mode entry
                on(TvKeyRole.MENU) mapsTo TvAction.PIP_ENTER_RESIZE_MODE

                // DPAD → Move PIP (in resize mode) or navigate background app
                // Default to move operations; background navigation handled elsewhere
                on(TvKeyRole.DPAD_UP) mapsTo TvAction.PIP_MOVE_UP
                on(TvKeyRole.DPAD_DOWN) mapsTo TvAction.PIP_MOVE_DOWN
                on(TvKeyRole.DPAD_LEFT) mapsTo TvAction.PIP_MOVE_LEFT
                on(TvKeyRole.DPAD_RIGHT) mapsTo TvAction.PIP_MOVE_RIGHT

                // Center → Confirm size/position (in resize mode)
                on(TvKeyRole.DPAD_CENTER) mapsTo TvAction.PIP_CONFIRM_RESIZE

                // Back → BACK
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
