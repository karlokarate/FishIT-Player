package com.chris.m3usuite.ui.home.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.home.ChromeBottomFocusRefs
import com.chris.m3usuite.ui.home.ChromeHeaderFocusRefs
import com.chris.m3usuite.ui.home.LocalChromeBottomFocusRefs
import com.chris.m3usuite.ui.home.LocalChromeHeaderFocusRefs
import com.chris.m3usuite.ui.home.header.FishITBottomPanel
import com.chris.m3usuite.ui.home.header.FishITHeader
import com.chris.m3usuite.ui.home.header.LocalBottomFirstFocus
import com.chris.m3usuite.ui.home.header.LocalHeaderFirstFocus
import com.chris.m3usuite.ui.home.header.LocalPreferSettingsFirstFocus
import com.chris.m3usuite.ui.home.header.LocalChromeOnAction

/** Hosts header + bottom chrome and wires focus locals for TV. */
@Composable
fun HomeChromeOverlay(
    expanded: Boolean,
    showHeader: Boolean,
    showBottom: Boolean,
    title: String,
    onLogo: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    onProfiles: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    bottomSelected: String,
    onBottomSelect: (String) -> Unit,
    statusPad: Dp,
    navPad: Dp,
    scrimAlpha: Float,
    preferSettingsFirstFocus: Boolean,
    onActionCollapse: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) { FocusKit.isTvDevice(context) }
    val headerFocusRefs = remember { ChromeHeaderFocusRefs(FocusRequester(), FocusRequester(), FocusRequester(), FocusRequester()) }
    val bottomFocusRefs = remember { ChromeBottomFocusRefs(FocusRequester(), FocusRequester(), FocusRequester()) }
    val headerFirstFocus = remember { FocusRequester() }
    val bottomFirstFocus = remember { FocusRequester() }

    val headerInitial = if (expanded && isTv && showHeader) headerFirstFocus else null
    val bottomInitial = if (expanded && isTv && showBottom) bottomFirstFocus else null

    LaunchedEffect(expanded, isTv, showHeader, showBottom) {
        if (expanded && isTv) {
            when {
                showHeader -> headerInitial?.requestFocus()
                showBottom -> bottomInitial?.requestFocus()
            }
        }
    }

    CompositionLocalProvider(
        LocalChromeHeaderFocusRefs provides headerFocusRefs,
        LocalChromeBottomFocusRefs provides bottomFocusRefs,
        LocalHeaderFirstFocus provides headerInitial,
        LocalBottomFirstFocus provides bottomInitial,
        LocalChromeOnAction provides onActionCollapse,
        LocalPreferSettingsFirstFocus provides preferSettingsFirstFocus
    ) {
        Box(Modifier.fillMaxSize()) {
            if (expanded && isTv) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.02f))
                )
            }
            if (showHeader) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = statusPad)
                ) {
                    FishITHeader(
                        title = title,
                        onSettings = onSettings,
                        scrimAlpha = scrimAlpha,
                        onSearch = onSearch,
                        onProfiles = onProfiles,
                        onLogo = onLogo
                    )
                }
            }
            if (showBottom) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navPad)
                ) {
                    FishITBottomPanel(
                        selected = bottomSelected,
                        onSelect = onBottomSelect
                    )
                }
            }
        }
    }
}
