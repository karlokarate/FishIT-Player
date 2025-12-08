package com.chris.m3usuite.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkFancy =
    darkColorScheme(
        // Accents
        primary = Color(0xFF2EC27E), // sattes Grün (nah an App-Accent)
        onPrimary = Color(0xFF00140A),
        secondary = Color(0xFF1E8F61),
        onSecondary = Color(0xFFE7FFF5),
        tertiary = Color(0xFF1A9E90),
        onTertiary = Color(0xFFE6FFFB),
        // Moss background & surfaces (leichte Helligkeitsvariation für Gradienten in Screens)
        background = Color(0xFF0F2418), // dunkelgrüner, moosiger Grundton
        onBackground = Color(0xFFEAF5EF), // hohe Lesbarkeit (nahe Weiß)
        surface = Color(0xFF15301F), // leicht heller als Hintergrund
        onSurface = Color(0xFFEAF5EF),
        surfaceVariant = Color(0xFF1A3A25), // für Chips/Schimmer/Container
        onSurfaceVariant = Color(0xFFD7E9E0),
        // Outline (weißer Rahmen mit leichtem Grünstich)
        outline = Color(0xFFD5F0E2),
        outlineVariant = Color(0xFFB6DCCD),
        // Error
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF1C0000),
    )

// Light palette removed to enforce consistent dark appearance across devices

private val GlossShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // Force dark appearance globally for consistent look across devices (phone/TV)
    MaterialTheme(
        colorScheme = DarkFancy,
        typography = Typography(),
        shapes = GlossShapes,
        content = content,
    )
}
