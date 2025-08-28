package com.chris.m3usuite.ui.home.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
// removed windowInsetsPadding; using statusBarsPadding()
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import com.chris.m3usuite.ui.common.IconVariant
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

object FishITHeaderHeights {
    val topBar = 56.dp
    val spacer = 8.dp
    val total = topBar + spacer
}

/** Translucent overlay header with app icon + settings gear; alpha controls scrim intensity. */
@Composable
fun FishITHeader(
    title: String,
    onSettings: () -> Unit,
    scrimAlpha: Float, // 0f..1f depending on scroll
    onSearch: (() -> Unit)? = null,
    onProfiles: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onLogo: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = (0.85f * scrimAlpha).coerceIn(0f, 0.85f)),
                    1f to Color.Transparent
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
                .let { m -> if (onLogo != null) m.clickable { onLogo() } else m }
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.chris.m3usuite.R.drawable.fisch),
                contentDescription = title,
                modifier = logoModifier
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onSearch != null) AppIconButton(icon = AppIcon.Search, contentDescription = "Suche öffnen", onClick = onSearch, size = 28.dp)
                if (onProfiles != null) AppIconButton(icon = AppIcon.Profile, contentDescription = "Profile", onClick = onProfiles, size = 28.dp)
                if (onRefresh != null) AppIconButton(icon = AppIcon.Refresh, contentDescription = "Aktualisieren", onClick = onRefresh, size = 28.dp)
                AppIconButton(icon = AppIcon.Settings, variant = IconVariant.Primary, contentDescription = "Einstellungen", onClick = onSettings, size = 28.dp)
            }
        }
        Spacer(Modifier.height(FishITHeaderHeights.spacer))
    }
}
