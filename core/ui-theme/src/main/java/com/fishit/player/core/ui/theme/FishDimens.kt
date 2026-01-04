package com.fishit.player.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * FishIT Player v2 Layout Dimensions
 *
 * Centralized dimensions for tiles, rows, spacing, and focus effects.
 * Used by FishTile, FishRow, and other layout components.
 */
@Immutable
data class FishDimens(
    // ═══════════════════════════════════════════════════════════════════
    // Tile Dimensions (2:3 aspect ratio for movies)
    // ═══════════════════════════════════════════════════════════════════
    val tileWidth: Dp = 180.dp,
    val tileHeight: Dp = 270.dp,
    val tileCorner: Dp = 14.dp,
    val tileSpacing: Dp = 12.dp,
    // ═══════════════════════════════════════════════════════════════════
    // Series Tile (16:9 landscape)
    // ═══════════════════════════════════════════════════════════════════
    val seriesTileWidth: Dp = 280.dp,
    val seriesTileHeight: Dp = 157.dp,
    // ═══════════════════════════════════════════════════════════════════
    // Live TV Tile (1:1 square)
    // ═══════════════════════════════════════════════════════════════════
    val liveTileSize: Dp = 120.dp,
    // ═══════════════════════════════════════════════════════════════════
    // Focus Effects
    // ═══════════════════════════════════════════════════════════════════
    val focusScale: Float = 1.10f,
    val focusScaleExperience: Float = 1.15f,
    val focusBorderWidth: Dp = 2.5.dp,
    val focusGlowRadius: Dp = 8.dp,
    // ═══════════════════════════════════════════════════════════════════
    // Row Layout
    // ═══════════════════════════════════════════════════════════════════
    val rowHeight: Dp = 320.dp,
    val rowSpacing: Dp = 24.dp,
    val contentPaddingHorizontal: Dp = 48.dp,
    val contentPaddingVertical: Dp = 16.dp,
    // ═══════════════════════════════════════════════════════════════════
    // Header
    // ═══════════════════════════════════════════════════════════════════
    val headerHeight: Dp = 56.dp,
    val headerPaddingHorizontal: Dp = 48.dp,
    // ═══════════════════════════════════════════════════════════════════
    // Overlay
    // ═══════════════════════════════════════════════════════════════════
    val overlayWidth: Dp = 400.dp,
    val overlayPadding: Dp = 20.dp,
    // ═══════════════════════════════════════════════════════════════════
    // Progress Bar
    // ═══════════════════════════════════════════════════════════════════
    val progressBarHeight: Dp = 3.dp,
    // ═══════════════════════════════════════════════════════════════════
    // Experience Skin Options
    // ═══════════════════════════════════════════════════════════════════
    val enableGlow: Boolean = true,
    val enableParallax: Boolean = false,
    val parallaxDepth: Float = 0.05f,
    val reflectionAlpha: Float = 0.18f,
    val showTitleWhenUnfocused: Boolean = false,
)

/**
 * CompositionLocal for FishDimens, allowing override in sub-trees
 */
val LocalFishDimens = staticCompositionLocalOf { FishDimens() }

/**
 * Preset configurations for different skins
 */
object FishDimensPresets {
    /**
     * Classic Skin: No glow, no parallax, stable and calm
     */
    val Classic =
        FishDimens(
            enableGlow = false,
            enableParallax = false,
            focusScale = 1.08f,
            showTitleWhenUnfocused = true,
        )

    /**
     * Experience Skin: Glow, parallax, cinematic feel
     */
    val Experience =
        FishDimens(
            enableGlow = true,
            enableParallax = true,
            focusScale = 1.12f,
            parallaxDepth = 0.05f,
            showTitleWhenUnfocused = false,
        )
}
