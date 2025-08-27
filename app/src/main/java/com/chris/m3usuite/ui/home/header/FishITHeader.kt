package com.chris.m3usuite.ui.home.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

data class HeaderTab(val id: String, val label: String)

object FishITHeaderHeights {
    val topBar = 64.dp
    val tabs = 40.dp
    val spacer = 8.dp
    val total = topBar + tabs + spacer
}

/** Translucent overlay header with actions + tabs; alpha controls scrim intensity. */
@Composable
fun FishITHeader(
    title: String,
    tabs: List<HeaderTab>,
    selectedTabId: String,
    onTabSelected: (HeaderTab) -> Unit,
    onRefresh: () -> Unit,
    onSwitchProfile: () -> Unit,
    onSettings: () -> Unit,
    scrimAlpha: Float, // 0f..1f depending on scroll
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
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp)
    ) {
        // Top bar
        Row(
            Modifier
                .height(FishITHeaderHeights.topBar)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Autorenew, contentDescription = "Aktualisieren")
                }
                IconButton(onClick = onSwitchProfile) {
                    Icon(Icons.Outlined.ManageAccounts, contentDescription = "Profil wechseln")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen")
                }
            }
        }
        // Tabs (Live / VOD / Series / Alle)
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0),
            edgePadding = 0.dp,
            divider = {},
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            tabs.forEach { t ->
                Tab(
                    selected = t.id == selectedTabId,
                    onClick = { onTabSelected(t) },
                    text = { Text(t.label) }
                )
            }
        }
        Spacer(Modifier.height(FishITHeaderHeights.spacer))
    }
}
