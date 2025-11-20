package com.chris.m3usuite.ui.layout

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class FishDimens(
    val tileWidthDp: Dp = 180.dp,
    val tileHeightDp: Dp = 270.dp,
    val tileCornerDp: Dp = 14.dp,
    val tileSpacingDp: Dp = 12.dp,
    val focusScale: Float = 1.10f,
    val focusBorderWidthDp: Dp = 2.5.dp,
    val reflectionAlpha: Float = 0.18f,
    val contentPaddingHorizontalDp: Dp = 16.dp,
    val enableGlow: Boolean = true,
    val showTitleWhenUnfocused: Boolean = false,
)

val LocalFishDimens = compositionLocalOf { FishDimens() }

@Composable
fun FishTheme(
    dimens: FishDimens = FishDimens(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFishDimens provides dimens) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
