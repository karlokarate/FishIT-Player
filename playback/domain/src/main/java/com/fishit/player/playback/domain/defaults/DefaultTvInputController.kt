package com.fishit.player.playback.domain.defaults

import com.fishit.player.playback.domain.TvInputAction
import com.fishit.player.playback.domain.TvInputController
import com.fishit.player.playback.domain.TvInputResult

/**
 * Default TvInputController for TV remote handling.
 *
 * This is a stub implementation for Phase 1.
 * Full TV input handling will be added in Phase 6.
 */
class DefaultTvInputController(
    override val isTvMode: Boolean = false,
) : TvInputController {
    override val seekStepMs: Long = 10_000L // 10 seconds

    override fun handleInput(
        action: TvInputAction,
        isControlsVisible: Boolean,
    ): TvInputResult =
        when (action) {
            TvInputAction.PLAY_PAUSE,
            TvInputAction.MEDIA_PLAY,
            TvInputAction.MEDIA_PAUSE,
            -> TvInputResult.Consumed

            TvInputAction.DPAD_CENTER -> {
                if (isControlsVisible) {
                    TvInputResult.Consumed
                } else {
                    TvInputResult.Action("show_controls")
                }
            }

            TvInputAction.DPAD_LEFT -> TvInputResult.Action("seek_backward")
            TvInputAction.DPAD_RIGHT -> TvInputResult.Action("seek_forward")

            TvInputAction.BACK -> {
                if (isControlsVisible) {
                    TvInputResult.Action("hide_controls")
                } else {
                    TvInputResult.PassThrough
                }
            }

            else -> TvInputResult.PassThrough
        }
}
