package com.chris.m3usuite.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.platform.LocalDensity
import com.chris.m3usuite.ui.home.header.FishITHeader
import com.chris.m3usuite.ui.home.header.FishITHeaderHeights
import com.chris.m3usuite.ui.home.header.rememberHeaderAlpha
import androidx.compose.foundation.lazy.LazyListState
import com.chris.m3usuite.ui.home.header.FishITBottomPanel
import com.chris.m3usuite.ui.home.header.FishITBottomHeights

@Composable
fun HomeChromeScaffold(
    title: String,
    onSearch: (() -> Unit)? = null,
    onProfiles: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    listState: LazyListState,
    bottomBar: @Composable (() -> Unit) = {
        FishITBottomPanel(selected = "all", onSelect = {})
    },
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val statusPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topBarHeight: Dp = FishITHeaderHeights.total
    val bottomBarHeight: Dp = FishITBottomHeights.bar

    val pads = PaddingValues(
        top = statusPad + topBarHeight,
        bottom = navPad + bottomBarHeight
    )

    val scrimAlpha = rememberHeaderAlpha(listState)

    Box(Modifier.fillMaxSize()) {
        // Content below with provided padding
        content(pads)

        // Header overlay
        FishITHeader(
            title = title,
            onSettings = { onSettings?.invoke() },
            scrimAlpha = scrimAlpha,
            onSearch = onSearch,
            onProfiles = onProfiles,
            onRefresh = onRefresh
        )

        // Bottom bar overlay
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            bottomBar()
        }
    }
}
