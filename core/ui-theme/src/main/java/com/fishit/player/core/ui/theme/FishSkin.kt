package com.fishit.player.core.ui.theme

import androidx.compose.runtime.Immutable

/**
 * Skin configuration for FishIT Player v2.
 *
 * Defines which visual profile to use (Classic vs Experience).
 */
@Immutable
data class FishSkin(
    val name: String,
    val dimens: FishDimens,
    val motion: FishMotion,
    val isExperience: Boolean = false
) {
    companion object {
        /**
         * Classic Skin (Default)
         *
         * - Calm, clean, information-rich
         * - Minimal motion
         * - Matte, subtle colors
         * - No parallax, no glow
         */
        val Classic = FishSkin(
            name = "Classic",
            dimens = FishDimensPresets.Classic,
            motion = FishMotionPresets.Classic,
            isExperience = false
        )

        /**
         * Experience Skin (Optional)
         *
         * - Enhanced depth with subtle parallax
         * - Ambient glows and color accents
         * - Richer transitions
         * - Cinematic and futuristic feel
         */
        val Experience = FishSkin(
            name = "Experience",
            dimens = FishDimensPresets.Experience,
            motion = FishMotionPresets.Experience,
            isExperience = true
        )
    }
}
