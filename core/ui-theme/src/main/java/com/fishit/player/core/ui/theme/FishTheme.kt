package com.fishit.player.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocals for FishTheme
 */
val LocalFishSkin = staticCompositionLocalOf { FishSkin.Classic }
val LocalFishMotion = staticCompositionLocalOf { FishMotion() }

/**
 * FishIT Player v2 Theme
 *
 * Wraps Material3 theme with FishIT-specific tokens and skin support.
 *
 * Usage:
 * ```
 * FishTheme(skin = FishSkin.Classic) {
 *     // Content
 * }
 * ```
 */
@Composable
fun FishTheme(
    skin: FishSkin = FishSkin.Classic,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        darkColorScheme(
            // Primary
            primary = FishColors.Primary,
            onPrimary = FishColors.OnPrimary,
            primaryContainer = FishColors.PrimaryContainer,
            onPrimaryContainer = FishColors.OnPrimaryContainer,
            // Secondary
            secondary = FishColors.Secondary,
            onSecondary = FishColors.OnSecondary,
            secondaryContainer = FishColors.SecondaryContainer,
            onSecondaryContainer = FishColors.OnSecondaryContainer,
            // Tertiary
            tertiary = FishColors.Tertiary,
            onTertiary = FishColors.OnTertiary,
            tertiaryContainer = FishColors.TertiaryContainer,
            onTertiaryContainer = FishColors.OnTertiaryContainer,
            // Background & Surface
            background = FishColors.Background,
            onBackground = FishColors.OnBackground,
            surface = FishColors.Surface,
            onSurface = FishColors.OnSurface,
            surfaceVariant = FishColors.SurfaceVariant,
            onSurfaceVariant = FishColors.OnSurfaceVariant,
            surfaceContainer = FishColors.SurfaceContainer,
            surfaceContainerHigh = FishColors.SurfaceContainerHigh,
            surfaceContainerHighest = FishColors.SurfaceContainerHighest,
            surfaceDim = FishColors.SurfaceDim,
            // Outline
            outline = FishColors.Outline,
            outlineVariant = FishColors.OutlineVariant,
            // Error
            error = FishColors.Error,
            onError = FishColors.OnError,
            errorContainer = FishColors.ErrorContainer,
            onErrorContainer = FishColors.OnErrorContainer,
            // Scrim
            scrim = FishColors.Scrim,
        )

    CompositionLocalProvider(
        LocalFishSkin provides skin,
        LocalFishDimens provides skin.dimens,
        LocalFishMotion provides skin.motion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = FishShapes.M3Shapes,
            content = content,
        )
    }
}

/**
 * Accessor object for theme values within Composables
 */
object FishTheme {
    /**
     * Current skin configuration
     */
    val skin: FishSkin
        @Composable
        @ReadOnlyComposable
        get() = LocalFishSkin.current

    /**
     * Current dimension tokens
     */
    val dimens: FishDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalFishDimens.current

    /**
     * Current motion tokens
     */
    val motion: FishMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalFishMotion.current

    /**
     * Whether Experience skin features are enabled
     */
    val isExperience: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalFishSkin.current.isExperience
}
