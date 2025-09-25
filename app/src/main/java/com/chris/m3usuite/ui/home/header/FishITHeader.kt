package com.chris.m3usuite.ui.home.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
// removed windowInsetsPadding; using statusBarsPadding()
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import com.chris.m3usuite.ui.common.IconVariant

object FishITHeaderHeights {
    val topBar = 56.dp
    val spacer = 8.dp
    val total = topBar + spacer
}

private val HeaderBaseColor = Color(0xFF05080F)

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
    val scrim = scrimAlpha.coerceIn(0f, 1f)
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
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val logoModifier = Modifier
                .padding(vertical = 8.dp)
                .let { m ->
                    if (onLogo != null) {
                        m.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onLogo() }
                    } else m
                }
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.chris.m3usuite.R.drawable.fisch_header),
                contentDescription = title,
                modifier = logoModifier
            )
            val focusOverlay = Color.White.copy(alpha = 0.4f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onSearch != null) AppIconButton(icon = AppIcon.Search, contentDescription = "Suche öffnen", onClick = onSearch, size = 28.dp, tvFocusOverlay = focusOverlay)
                if (onProfiles != null) AppIconButton(icon = AppIcon.Profile, contentDescription = "Profile", onClick = onProfiles, size = 28.dp, tvFocusOverlay = focusOverlay)
                if (onSettings != null) {
                    AppIconButton(
                        icon = AppIcon.Settings,
                        variant = IconVariant.Primary,
                        contentDescription = "Einstellungen",
                        onClick = onSettings,
                        size = 28.dp,
                        tvFocusOverlay = focusOverlay
                    )
                }
            }
        }
        Spacer(Modifier.height(FishITHeaderHeights.spacer))
    }
}
