package com.chris.m3usuite.ui.home.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.IconVariant
import com.chris.m3usuite.ui.common.resId
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.focusGroup
import com.chris.m3usuite.ui.home.ChromeBottomFocusRefs
import com.chris.m3usuite.ui.home.ChromeHeaderFocusRefs
import com.chris.m3usuite.ui.home.LocalChromeBottomFocusRefs
import com.chris.m3usuite.ui.home.LocalChromeHeaderFocusRefs

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
    val headerRefs = LocalChromeHeaderFocusRefs.current
    val bottomRefs = LocalChromeBottomFocusRefs.current
        ?: remember { ChromeBottomFocusRefs(FocusRequester(), FocusRequester(), FocusRequester()) }
    // Preferred header target to jump to on DPAD UP (falls back to logo when null)
    val headerFirst = LocalHeaderFirstFocus.current

    val liveRequester = firstFocus ?: bottomRefs.live
    val vodRequester = bottomRefs.vod
    val seriesRequester = bottomRefs.series

    val headerLogo = headerRefs?.logo
    val headerSearch = headerRefs?.search
    val headerProfile = headerRefs?.profile
    val headerSettings = headerRefs?.settings

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
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
    ) {
        val liveIcon = AppIcon.LiveTv.resId(if (selected == "live") IconVariant.Primary else IconVariant.Duotone)
        val vodIcon = AppIcon.MovieVod.resId(if (selected == "vod") IconVariant.Primary else IconVariant.Duotone)
        val seriesIcon = AppIcon.Series.resId(if (selected == "series") IconVariant.Primary else IconVariant.Duotone)

        // Live
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .then(
                    FocusKit.run {
                        Modifier
                            .tvClickable(
                                onClick = { onChromeAction?.invoke(); onSelect("live") },
                                focusRequester = liveRequester,
                                focusColors = FocusKit.FocusDefaults.IconColors,
                                focusBorderWidth = 2.2.dp,
                                debugTag = "Bottom/Live"
                            )
                            .focusNeighbors(
                                right = vodRequester,
                                up = headerFirst ?: headerLogo
                            )
                    }
                )
        ) {
            androidx.compose.foundation.Image(
                painter = com.chris.m3usuite.ui.debug.safePainter(liveIcon, label = "Bottom/Live"),
                contentDescription = "TV",
                modifier = Modifier.size(28.dp)
            )
        }

        // VOD
        androidx.compose.foundation.layout.Box(
            modifier = FocusKit.run {
                Modifier
                    .tvClickable(
                        onClick = { onChromeAction?.invoke(); onSelect("vod") },
                        focusRequester = vodRequester,
                        focusColors = FocusKit.FocusDefaults.IconColors,
                        focusBorderWidth = 2.2.dp,
                        debugTag = "Bottom/VOD"
                    )
                    .focusNeighbors(
                        left = liveRequester,
                        right = seriesRequester,
                        up = headerFirst ?: headerLogo
                    )
            }
        ) {
            androidx.compose.foundation.Image(
                painter = com.chris.m3usuite.ui.debug.safePainter(vodIcon, label = "Bottom/VOD"),
                contentDescription = "Filme",
                modifier = Modifier.size(28.dp)
            )
        }

        // Series
        androidx.compose.foundation.layout.Box(
            modifier = FocusKit.run {
                Modifier
                    .tvClickable(
                        onClick = { onChromeAction?.invoke(); onSelect("series") },
                        focusRequester = seriesRequester,
                        focusColors = FocusKit.FocusDefaults.IconColors,
                        focusBorderWidth = 2.2.dp,
                        debugTag = "Bottom/Series"
                    )
                    .focusNeighbors(
                        left = vodRequester,
                        up = headerFirst ?: headerLogo
                    )
            }
        ) {
            androidx.compose.foundation.Image(
                painter = com.chris.m3usuite.ui.debug.safePainter(seriesIcon, label = "Bottom/Series"),
                contentDescription = "Serien",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
