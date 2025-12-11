package com.fishit.player.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * FishIT Player v2 Shape System
 *
 * Gloss-style rounded corners for tiles, cards, and containers.
 */
object FishShapes {

    val ExtraSmall = RoundedCornerShape(10.dp)
    val Small = RoundedCornerShape(14.dp)
    val Medium = RoundedCornerShape(18.dp)
    val Large = RoundedCornerShape(22.dp)
    val ExtraLarge = RoundedCornerShape(28.dp)

    /**
     * Material3 Shapes for theme integration
     */
    val M3Shapes = Shapes(
        extraSmall = ExtraSmall,
        small = Small,
        medium = Medium,
        large = Large,
        extraLarge = ExtraLarge
    )

    // ═══════════════════════════════════════════════════════════════════
    // Tile-specific shapes
    // ═══════════════════════════════════════════════════════════════════

    val Tile = RoundedCornerShape(14.dp)
    val TileSmall = RoundedCornerShape(10.dp)
    val TileLarge = RoundedCornerShape(18.dp)

    // Channel logo (rounded square)
    val ChannelLogo = RoundedCornerShape(12.dp)

    // Music tile (circular)
    val MusicTile = RoundedCornerShape(50)

    // Audiobook tile (softly rounded square)
    val AudiobookTile = RoundedCornerShape(16.dp)

    // ═══════════════════════════════════════════════════════════════════
    // Button shapes
    // ═══════════════════════════════════════════════════════════════════

    val Button = RoundedCornerShape(12.dp)
    val ButtonSmall = RoundedCornerShape(8.dp)
    val Chip = RoundedCornerShape(8.dp)

    // ═══════════════════════════════════════════════════════════════════
    // Overlay & Dialog shapes
    // ═══════════════════════════════════════════════════════════════════

    val Overlay = RoundedCornerShape(20.dp)
    val Dialog = RoundedCornerShape(24.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
