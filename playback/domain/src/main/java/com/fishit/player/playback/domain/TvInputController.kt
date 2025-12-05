package com.fishit.player.playback.domain

/**
 * TV remote input actions.
 */
enum class TvInputAction {
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    DPAD_CENTER,
    BACK,
    PLAY_PAUSE,
    MEDIA_PLAY,
    MEDIA_PAUSE,
    MEDIA_STOP,
    MEDIA_FAST_FORWARD,
    MEDIA_REWIND,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    CHANNEL_UP,
    CHANNEL_DOWN,
    INFO,
    MENU
}

/**
 * Result of handling a TV input action.
 */
sealed class TvInputResult {
    /** The action was consumed by the player. */
    data object Consumed : TvInputResult()

    /** The action should be passed through to the system/activity. */
    data object PassThrough : TvInputResult()

    /** The action triggers a specific player behavior. */
    data class Action(val description: String) : TvInputResult()
}

/**
 * Controller for TV remote / DPAD input handling.
 *
 * Maps TV input events to player actions based on current state.
 */
interface TvInputController {

    /**
     * Whether TV input mode is active (running on TV device).
     */
    val isTvMode: Boolean

    /**
     * Handles a TV input action.
     *
     * @param action The input action.
     * @param isControlsVisible Whether player controls are currently visible.
     * @return Result indicating how the action was handled.
     */
    fun handleInput(action: TvInputAction, isControlsVisible: Boolean): TvInputResult

    /**
     * Seek step in milliseconds for left/right DPAD when seeking.
     */
    val seekStepMs: Long
}
