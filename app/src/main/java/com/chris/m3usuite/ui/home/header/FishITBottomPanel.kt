package com.chris.m3usuite.ui.home.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.chris.m3usuite.ui.compat.focusGroup
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import com.chris.m3usuite.ui.common.IconVariant
import com.chris.m3usuite.ui.focus.FocusKit

object FishITBottomHeights {
    val bar = 56.dp
}

private val BottomBaseColor = Color(0xFF05080F)

// CompositionLocal: allows scaffold to provide a focus target and a collapse hook
val LocalBottomFirstFocus: androidx.compose.runtime.ProvidableCompositionLocal<FocusRequester?> = compositionLocalOf { null }

@Composable
fun FishITBottomPanel(
    selected: String, // "live" | "vod" | "series"
    onSelect: (String) -> Unit,
) {
    val focusOverlay = Color.White.copy(alpha = 0.4f)
    val firstFocus = LocalBottomFirstFocus.current
    val onChromeAction = LocalChromeOnAction.current
    Row(
        Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to BottomBaseColor.copy(alpha = 0.84f),
                        0.6f to BottomBaseColor.copy(alpha = 0.92f),
                        1f to BottomBaseColor
                    )
                )
            )
            .padding(horizontal = 12.dp)
            .height(FishITBottomHeights.bar)
            .then(if (FocusKit.isTvDevice(LocalContext.current)) Modifier.focusGroup() else Modifier),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AppIconButton(
            icon = AppIcon.LiveTv,
            variant = if (selected == "live") IconVariant.Primary else IconVariant.Duotone,
            contentDescription = "TV",
            onClick = { onChromeAction?.invoke(); onSelect("live") },
            size = 28.dp,
            tvFocusOverlay = focusOverlay,
            modifier = if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
        )
        AppIconButton(
            icon = AppIcon.MovieVod,
            variant = if (selected == "vod") IconVariant.Primary else IconVariant.Duotone,
            contentDescription = "Filme",
            onClick = { onChromeAction?.invoke(); onSelect("vod") },
            size = 28.dp,
            tvFocusOverlay = focusOverlay
        )
        AppIconButton(
            icon = AppIcon.Series,
            variant = if (selected == "series") IconVariant.Primary else IconVariant.Duotone,
            contentDescription = "Serien",
            onClick = { onChromeAction?.invoke(); onSelect("series") },
            size = 28.dp,
            tvFocusOverlay = focusOverlay
        )
    }
}
