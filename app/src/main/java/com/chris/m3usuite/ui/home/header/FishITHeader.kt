package com.chris.m3usuite.ui.home.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
// removed windowInsetsPadding; using statusBarsPadding()
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.chris.m3usuite.ui.compat.focusGroup
import com.chris.m3usuite.ui.skin.tvClickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import com.chris.m3usuite.ui.common.TvIconButton
import com.chris.m3usuite.ui.skin.TvFocusColors
import com.chris.m3usuite.ui.common.IconVariant
import com.chris.m3usuite.ui.debug.safePainter
import com.chris.m3usuite.ui.skin.isTvDevice

object FishITHeaderHeights {
    val topBar = 56.dp
    val spacer = 8.dp
    val total = topBar + spacer
}

private val HeaderBaseColor = Color(0xFF05080F)

// CompositionLocals provided by the Scaffold
val LocalHeaderFirstFocus: androidx.compose.runtime.ProvidableCompositionLocal<FocusRequester?> = compositionLocalOf { null }
val LocalChromeOnAction: androidx.compose.runtime.ProvidableCompositionLocal<(() -> Unit)?> = compositionLocalOf { null }

/** Translucent overlay header with app icon + settings gear; alpha controls scrim intensity. */
@Composable
fun FishITHeader(
    title: String,
    onSettings: (() -> Unit)?,
    scrimAlpha: Float, // 0f..1f depending on scroll
    onSearch: (() -> Unit)? = null,
    onProfiles: (() -> Unit)? = null,
    onLogo: (() -> Unit)? = null,
) {
    val firstFocus = LocalHeaderFirstFocus.current
    val onChromeAction = LocalChromeOnAction.current
    val scrim = scrimAlpha.coerceIn(0f, 1f)
    val attachToSearch = firstFocus != null && onSearch != null
    val attachToProfiles = firstFocus != null && !attachToSearch && onProfiles != null
    val attachToSettings = firstFocus != null && !attachToSearch && !attachToProfiles && onSettings != null
    val attachToLogo = firstFocus != null && !attachToSearch && !attachToProfiles && !attachToSettings && onLogo != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to HeaderBaseColor,
                        0.45f to HeaderBaseColor.copy(alpha = (0.78f + scrim * 0.18f).coerceIn(0f, 1f)),
                        1f to HeaderBaseColor.copy(alpha = 0f)
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        // Top bar
        Row(
            Modifier
                .height(FishITHeaderHeights.topBar)
                .fillMaxWidth()
                .then(if (isTvDevice(LocalContext.current)) Modifier.focusGroup() else Modifier),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val logoModifier = Modifier
                .padding(vertical = 8.dp)
                .let { m -> if (attachToLogo) m.focusRequester(firstFocus!!) else m }
            if (onLogo != null) {
                TvIconButton(
                    onClick = { onChromeAction?.invoke(); onLogo() },
                    modifier = logoModifier,
                    focusColors = TvFocusColors.Icon
                ) {
                    androidx.compose.foundation.Image(
                        painter = safePainter(com.chris.m3usuite.R.drawable.fisch_header, label = "FishITHeader"),
                        contentDescription = title,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                androidx.compose.foundation.Image(
                    painter = safePainter(com.chris.m3usuite.R.drawable.fisch_header, label = "FishITHeader"),
                    contentDescription = title,
                    modifier = logoModifier
                )
            }
            val focusOverlay = Color.White.copy(alpha = 0.4f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onSearch != null) AppIconButton(
                    icon = AppIcon.Search,
                    contentDescription = "Suche öffnen",
                    onClick = { onChromeAction?.invoke(); onSearch() },
                    size = 28.dp,
                    tvFocusOverlay = focusOverlay,
                    modifier = if (attachToSearch) Modifier.focusRequester(firstFocus!!) else Modifier
                )
                if (onProfiles != null) AppIconButton(
                    icon = AppIcon.Profile,
                    contentDescription = "Profile",
                    onClick = { onChromeAction?.invoke(); onProfiles() },
                    size = 28.dp,
                    tvFocusOverlay = focusOverlay,
                    modifier = if (attachToProfiles) Modifier.focusRequester(firstFocus!!) else Modifier
                )
                if (onSettings != null) {
                    AppIconButton(
                        icon = AppIcon.Settings,
                        variant = IconVariant.Primary,
                        contentDescription = "Einstellungen",
                        onClick = { onChromeAction?.invoke(); onSettings() },
                        size = 28.dp,
                        tvFocusOverlay = focusOverlay,
                        modifier = if (attachToSettings) Modifier.focusRequester(firstFocus!!) else Modifier
                    )
                }
            }
        }
        Spacer(Modifier.height(FishITHeaderHeights.spacer))
    }
}
