package com.fishit.player.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * FishIT Player v2 Color Palette
 *
 * Dark-only theme with moss-green accents, ported from v1 DarkFancy theme.
 * Designed for TV and mobile, supporting both Classic and Experience skins.
 */
object FishColors {

    // ═══════════════════════════════════════════════════════════════════
    // Primary Accents (Green-based)
    // ═══════════════════════════════════════════════════════════════════

    val Primary = Color(0xFF2EC27E)          // Sattes Grün - Hauptakzent
    val OnPrimary = Color(0xFF00140A)        // Text auf Primary
    val PrimaryContainer = Color(0xFF1E8F61)
    val OnPrimaryContainer = Color(0xFFEAF5EF)

    val Secondary = Color(0xFF1E8F61)
    val OnSecondary = Color(0xFF001F12)
    val SecondaryContainer = Color(0xFF0D4D32)
    val OnSecondaryContainer = Color(0xFFD7F3E6)

    val Tertiary = Color(0xFF1A9E90)
    val OnTertiary = Color(0xFF00201C)
    val TertiaryContainer = Color(0xFF0F5F56)
    val OnTertiaryContainer = Color(0xFFD0F5F0)

    // ═══════════════════════════════════════════════════════════════════
    // Moss Background (Dark Green)
    // ═══════════════════════════════════════════════════════════════════

    val Background = Color(0xFF0F2418)        // Dunkelgrüner Hintergrund
    val OnBackground = Color(0xFFEAF5EF)

    val Surface = Color(0xFF15301F)
    val OnSurface = Color(0xFFEAF5EF)
    val SurfaceVariant = Color(0xFF1A3A25)
    val OnSurfaceVariant = Color(0xFFD7E9E0)

    val SurfaceContainer = Color(0xFF1A3A25)
    val SurfaceContainerHigh = Color(0xFF204530)
    val SurfaceContainerHighest = Color(0xFF26503A)
    val SurfaceDim = Color(0xFF0C1E14)

    // ═══════════════════════════════════════════════════════════════════
    // Outline & Borders
    // ═══════════════════════════════════════════════════════════════════

    val Outline = Color(0xFFD5F0E2)
    val OutlineVariant = Color(0xFFB6DCCD)

    // ═══════════════════════════════════════════════════════════════════
    // Error & Status
    // ═══════════════════════════════════════════════════════════════════

    val Error = Color(0xFFFF6B6B)
    val OnError = Color(0xFF1A0000)
    val ErrorContainer = Color(0xFF4D1F1F)
    val OnErrorContainer = Color(0xFFFFDADA)
    
    /** Success state indicator color */
    val Success = Color(0xFF4CAF50)
    /** Warning state indicator color */
    val Warning = Color(0xFFFFC107)

    // ═══════════════════════════════════════════════════════════════════
    // Source Colors (for Multi-Source Frame)
    // ═══════════════════════════════════════════════════════════════════

    val SourceTelegram = Color(0xFF26A5E4)   // Telegram Blue
    val SourceXtream = Color(0xFFE53935)     // Xtream Red
    val SourceLocal = Color(0xFF4CAF50)      // Local Green
    val SourcePlex = Color(0xFFE5A00D)       // Plex Gold
    val SourceJellyfin = Color(0xFF9C27B0)   // Jellyfin Purple

    // ═══════════════════════════════════════════════════════════════════
    // Design Tokens
    // ═══════════════════════════════════════════════════════════════════

    val Accent = Color(0xFF3DDC84)            // App-Icon Grün
    val KidAccent = Color(0xFFFF5E5B)         // Kid-Profile Rot

    // Tile glow colors
    val FocusGlow = Color(0xFF2EC27E)
    val FocusGlowExperience = Color(0xFF3DDC84)

    // Rating star color
    val Rating = Color(0xFFFFD700)            // Gold for star ratings

    // ═══════════════════════════════════════════════════════════════════
    // Scrim & Overlay
    // ═══════════════════════════════════════════════════════════════════

    val Scrim = Color(0xCC000000)             // 80% Black
    val ScrimLight = Color(0x99000000)        // 60% Black
    val Overlay = Color(0xE6121212)           // 90% Dark
}

/**
 * Alpha values for various UI states
 */
object FishAlpha {
    const val Badge = 0.92f
    const val Disabled = 0.38f
    const val Medium = 0.60f
    const val High = 0.87f
    const val Focused = 1.0f
    const val Unfocused = 0.7f
    const val Reflection = 0.18f
}
