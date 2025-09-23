package com.chris.m3usuite.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import com.chris.m3usuite.ui.home.header.FishITBottomHeights
import com.chris.m3usuite.ui.home.header.FishITBottomPanel
import com.chris.m3usuite.ui.home.header.FishITHeader
import com.chris.m3usuite.ui.home.header.FishITHeaderHeights
import com.chris.m3usuite.ui.home.header.rememberHeaderAlpha

@Composable
fun HomeChromeScaffold(
    title: String,
    onSearch: (() -> Unit)? = null,
    onProfiles: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    listState: LazyListState,
    onLogo: (() -> Unit)? = null,
    snackbarHost: SnackbarHostState? = null,
    // Optional: BottomBar, Sichtbarkeit steuert das untere Padding
    showBottomBar: Boolean = true,
    bottomBar: (@Composable () -> Unit)? = {
        FishITBottomPanel(selected = "all", onSelect = {})
    },
    content: @Composable (PaddingValues) -> Unit
) {
    // System-Insets (compute via density to avoid extension import issues)
    val density = LocalDensity.current
    val statusPad = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navPad = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    // App-Chrome-Höhen
    val topBarHeight: Dp = FishITHeaderHeights.total
    val bottomBarHeight: Dp = FishITBottomHeights.bar

    // Nur vom "Vorhandensein" der BottomBar abhängig, nicht von ihrer Funktions-Identität
    val hasBottomBar = showBottomBar && bottomBar != null

    // Effektives Content-Padding (oben: Status + Header, unten: Nav + optional BottomBar)
    val pads = remember(statusPad, navPad, hasBottomBar) {
        PaddingValues(
            top = statusPad + topBarHeight,
            bottom = navPad + if (hasBottomBar) bottomBarHeight else 0.dp
        )
    }

    val scrimAlpha = rememberHeaderAlpha(listState)

    Box(Modifier.fillMaxSize()) {
        // Inhalt mit korrektem Chrome/Inset-Padding
        content(pads)

        // Top-Header-Overlay
        FishITHeader(
            title = title,
            onSettings = { onSettings?.invoke() },
            scrimAlpha = scrimAlpha,
            onSearch = onSearch,
            onProfiles = onProfiles,
            onLogo = onLogo
        )

        // BottomBar-Overlay (optional) – mit Navigation-Bar-Padding
        if (hasBottomBar) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.navigationBars.asPaddingValues()),
                contentAlignment = Alignment.BottomCenter
            ) {
                bottomBar?.invoke()
            }
        }

        // SnackbarHost (optional) – ebenfalls oberhalb der Navigation-Bar platzieren
        if (snackbarHost != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.navigationBars.asPaddingValues()),
                contentAlignment = Alignment.BottomCenter
            ) {
                SnackbarHost(hostState = snackbarHost)
            }
        }
    }
}
