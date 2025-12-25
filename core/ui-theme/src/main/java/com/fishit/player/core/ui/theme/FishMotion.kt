package com.fishit.player.core.ui.theme

import androidx.compose.runtime.Immutable

/**
 * Motion tokens for animations and transitions.
 *
 * Classic Skin uses minimal motion, Experience Skin is more expressive.
 */
@Immutable
data class FishMotion(
    // ═══════════════════════════════════════════════════════════════════
    // Focus Animations
    // ═══════════════════════════════════════════════════════════════════
    val focusScaleDurationMs: Int = 150,
    val focusGlowDurationMs: Int = 200,
    // ═══════════════════════════════════════════════════════════════════
    // Overlay Transitions
    // ═══════════════════════════════════════════════════════════════════
    val overlayFadeInDurationMs: Int = 200,
    val overlayFadeOutDurationMs: Int = 150,
    val overlaySlideOffsetDp: Int = 20,
    // ═══════════════════════════════════════════════════════════════════
    // Focus Dwell (time before overlay appears)
    // ═══════════════════════════════════════════════════════════════════
    val focusDwellDelayMs: Long = 500L,
    // ═══════════════════════════════════════════════════════════════════
    // Row Scroll
    // ═══════════════════════════════════════════════════════════════════
    val rowScrollDurationMs: Int = 300,
    // ═══════════════════════════════════════════════════════════════════
    // Screen Transitions
    // ═══════════════════════════════════════════════════════════════════
    val screenEnterDurationMs: Int = 300,
    val screenExitDurationMs: Int = 200,
    // ═══════════════════════════════════════════════════════════════════
    // Experience Skin Extras
    // ═══════════════════════════════════════════════════════════════════
    val parallaxEnabled: Boolean = false,
    val parallaxDurationMs: Int = 400,
    val glowPulseDurationMs: Int = 1000,
)

/**
 * Motion presets for different skins
 */
object FishMotionPresets {
    /**
     * Classic Skin: Minimal, calm motion
     */
    val Classic =
        FishMotion(
            focusScaleDurationMs = 100,
            overlayFadeInDurationMs = 150,
            overlayFadeOutDurationMs = 100,
            parallaxEnabled = false,
        )

    /**
     * Experience Skin: Expressive, cinematic motion
     */
    val Experience =
        FishMotion(
            focusScaleDurationMs = 200,
            focusGlowDurationMs = 300,
            overlayFadeInDurationMs = 250,
            overlaySlideOffsetDp = 30,
            parallaxEnabled = true,
            parallaxDurationMs = 500,
            glowPulseDurationMs = 1500,
        )
}
