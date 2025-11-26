package com.chris.m3usuite.player.internal.subtitles

import kotlinx.coroutines.flow.StateFlow

/**
 * Centralized subtitle style management for the SIP Internal Player.
 *
 * Contract Reference: `INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` Section 5
 *
 * **Contract Rules:**
 * - Styles persist per profile (not globally)
 * - Updates propagate immediately through StateFlows
 * - Both SIP Player and SettingsScreen use the same manager instance
 * - In Kid Mode, manager values remain valid but are ignored by player
 *
 * **Thread Safety:**
 * All methods are suspend functions and safe for concurrent access.
 */
interface SubtitleStyleManager {
    /**
     * Current subtitle style.
     * Emits immediately when style is updated via [updateStyle] or [applyPreset].
     */
    val currentStyle: StateFlow<SubtitleStyle>

    /**
     * Current preset selection.
     * Emits immediately when preset is applied via [applyPreset].
     */
    val currentPreset: StateFlow<SubtitlePreset>

    /**
     * Updates the current subtitle style.
     * Persists to DataStore for the current profile.
     *
     * @param style New subtitle style to apply
     */
    suspend fun updateStyle(style: SubtitleStyle)

    /**
     * Applies a predefined preset.
     * Converts preset to style and persists.
     *
     * @param preset Preset to apply
     */
    suspend fun applyPreset(preset: SubtitlePreset)

    /**
     * Resets subtitle style to contract defaults.
     * Applies DEFAULT preset.
     */
    suspend fun resetToDefault()
}
