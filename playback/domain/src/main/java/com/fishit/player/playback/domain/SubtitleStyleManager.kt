package com.fishit.player.playback.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Subtitle/CC style preferences.
 */
data class SubtitleStyle(
    val fontSize: Float = 1.0f,
    val fontColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundColor: Int = 0x80000000.toInt(),
    val edgeType: Int = 0,
    val edgeColor: Int = 0xFF000000.toInt()
)

/**
 * Manages subtitle/closed caption styling preferences.
 *
 * Provides a reactive flow of style changes that the player UI observes.
 */
interface SubtitleStyleManager {

    /**
     * Current subtitle style as a StateFlow.
     */
    val style: StateFlow<SubtitleStyle>

    /**
     * Updates the subtitle style.
     */
    suspend fun updateStyle(style: SubtitleStyle)

    /**
     * Resets to default style.
     */
    suspend fun resetToDefault()
}
