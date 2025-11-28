package com.chris.m3usuite.tv.input

import android.view.KeyEvent

/**
 * Maps Android KeyEvent keycodes to semantic TvKeyRole values.
 *
 * TvKeyMapper provides deterministic conversion from raw hardware key events
 * to logical key roles. Unknown or unsupported keycodes return null.
 *
 * This is the first step in the Phase 6 TV Input Pipeline:
 * ```
 * KeyEvent → TvKeyMapper → TvKeyRole → TvAction → Screen handlers
 * ```
 *
 * Integration Pattern:
 * - TvKeyDebouncer is placed at the GlobalTvInputHost layer
 * - TvKeyMapper receives already-debounced KeyEvents
 * - The mapper does NOT implement debouncing itself
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 3.1
 * Contract Reference: INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 6
 *
 * @see TvKeyRole for the semantic key role enum
 * @see TvAction for semantic application-level commands
 */
object TvKeyMapper {
    /**
     * Minimum repeat count to consider a key press as "long press".
     *
     * On Android TV/Fire TV, holding a key generates repeated ACTION_DOWN events
     * with increasing repeatCount. A repeatCount >= this threshold indicates a long press.
     *
     * Threshold of 3 provides approximately 450-600ms hold time on most devices
     * (typical key repeat rate is ~150-200ms).
     */
    private const val LONG_PRESS_REPEAT_THRESHOLD = 3

    /**
     * Map an Android KeyEvent keycode to a semantic TvKeyRole.
     *
     * @param keyCode The Android KeyEvent.KEYCODE_* value
     * @return The corresponding TvKeyRole, or null if unsupported
     */
    fun mapKeyCode(keyCode: Int): TvKeyRole? =
        when (keyCode) {
            // DPAD Navigation
            KeyEvent.KEYCODE_DPAD_UP -> TvKeyRole.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> TvKeyRole.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> TvKeyRole.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> TvKeyRole.DPAD_RIGHT
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> TvKeyRole.DPAD_CENTER

            // Media Playback Keys
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            -> TvKeyRole.PLAY_PAUSE

            // Fast Forward: FAST_FORWARD is standard, SKIP_FORWARD is used by some remotes
            // for chapter/track skip, STEP_FORWARD is frame-by-frame. All map to FAST_FORWARD
            // role for consistent user experience on TV remotes.
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            -> TvKeyRole.FAST_FORWARD

            // Rewind: Same logic as Fast Forward - all variations map to REWIND role.
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            -> TvKeyRole.REWIND

            // Menu & Navigation Keys
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS,
            -> TvKeyRole.MENU

            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            -> TvKeyRole.BACK

            // Channel Control Keys
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            -> TvKeyRole.CHANNEL_UP

            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            -> TvKeyRole.CHANNEL_DOWN

            // Information Keys
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_TV_DATA_SERVICE,
            -> TvKeyRole.INFO

            // Guide: Standard GUIDE key plus PROG_RED which is used by some European
            // TV remotes (e.g., DVB remotes) where the red colored button opens EPG/Guide.
            // This is a common convention on cable/satellite remotes.
            KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_PROG_RED,
            -> TvKeyRole.GUIDE

            // Number Keys
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_NUMPAD_0,
            -> TvKeyRole.NUM_0
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_NUMPAD_1,
            -> TvKeyRole.NUM_1
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_NUMPAD_2,
            -> TvKeyRole.NUM_2
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_NUMPAD_3,
            -> TvKeyRole.NUM_3
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_NUMPAD_4,
            -> TvKeyRole.NUM_4
            KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_NUMPAD_5,
            -> TvKeyRole.NUM_5
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_NUMPAD_6,
            -> TvKeyRole.NUM_6
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_NUMPAD_7,
            -> TvKeyRole.NUM_7
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_NUMPAD_8,
            -> TvKeyRole.NUM_8
            KeyEvent.KEYCODE_9,
            KeyEvent.KEYCODE_NUMPAD_9,
            -> TvKeyRole.NUM_9

            // Unknown/Unsupported keycodes
            else -> null
        }

    /**
     * Map an Android KeyEvent to a semantic TvKeyRole.
     *
     * @param event The Android KeyEvent
     * @return The corresponding TvKeyRole, or null if unsupported
     */
    fun map(event: KeyEvent): TvKeyRole? = mapKeyCode(event.keyCode)

    /**
     * Map a debounced KeyEvent to a TvKeyRole, with long-press detection.
     *
     * This is the preferred entry point when using TvKeyDebouncer.
     * The event should have already passed through debouncing at the
     * GlobalTvInputHost layer.
     *
     * ════════════════════════════════════════════════════════════════════════════════
     * PHASE 7 – Long-press PLAY Detection
     * ════════════════════════════════════════════════════════════════════════════════
     *
     * For PLAY_PAUSE keys, this method detects long-press using:
     * 1. KeyEvent.isLongPress() - set by the system for long press
     * 2. repeatCount >= LONG_PRESS_REPEAT_THRESHOLD - for held keys
     *
     * When long-press is detected on PLAY_PAUSE, returns PLAY_PAUSE_LONG instead.
     *
     * **Contract Reference:**
     * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 6
     *
     * @param event The already-debounced KeyEvent
     * @return The corresponding TvKeyRole, or null if unsupported
     */
    fun mapDebounced(event: KeyEvent): TvKeyRole? {
        val baseRole = map(event)

        // Check for long-press on PLAY_PAUSE keys
        if (baseRole == TvKeyRole.PLAY_PAUSE) {
            val isLongPress = event.isLongPress || event.repeatCount >= LONG_PRESS_REPEAT_THRESHOLD
            if (isLongPress) {
                return TvKeyRole.PLAY_PAUSE_LONG
            }
        }

        return baseRole
    }

    /**
     * Check if a keycode is supported by this mapper.
     *
     * @param keyCode The Android KeyEvent.KEYCODE_* value
     * @return True if the keycode can be mapped to a TvKeyRole
     */
    fun isSupported(keyCode: Int): Boolean = mapKeyCode(keyCode) != null
}
