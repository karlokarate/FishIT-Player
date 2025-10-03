package com.chris.m3usuite.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.m3usuite.ui.home.header.FishITBottomHeights
import com.chris.m3usuite.ui.home.header.FishITBottomPanel
import com.chris.m3usuite.ui.home.header.FishITHeader
import com.chris.m3usuite.ui.home.header.FishITHeaderHeights
import com.chris.m3usuite.ui.home.header.rememberHeaderAlpha
import com.chris.m3usuite.ui.home.header.LocalBottomFirstFocus
import com.chris.m3usuite.ui.home.header.LocalHeaderFirstFocus
import com.chris.m3usuite.ui.home.header.LocalChromeOnAction
import com.chris.m3usuite.core.xtream.XtreamImportCoordinator
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import android.os.SystemClock
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay

// Rows notify current focused row to drive chrome behavior
val LocalChromeRowFocusSetter: androidx.compose.runtime.ProvidableCompositionLocal<(String?) -> Unit> = compositionLocalOf { {}
}

// TV chrome mode (file-private)
private enum class ChromeMode { Visible, Collapsed, Expanded }

// Global toggle provider to allow deep children (e.g., tiles) to trigger burger behavior
val LocalChromeToggle: androidx.compose.runtime.ProvidableCompositionLocal<(() -> Unit)?> = compositionLocalOf { null }
// Explicit expand action for chrome (TV only usage)
val LocalChromeExpand: androidx.compose.runtime.ProvidableCompositionLocal<(() -> Unit)?> = compositionLocalOf { null }

@Composable
fun HomeChromeScaffold(
    title: String,
    onSearch: (() -> Unit)? = null,
    onProfiles: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    listState: LazyListState,
    onLogo: (() -> Unit)? = null,
    snackbarHost: SnackbarHostState? = null,
    // Optional: TopBar sichtbar? (analog zur BottomBar). Wenn false, wird kein Header gezeichnet
    // und das Top-Padding enthält nur noch Statusbar-Insets.
    showHeader: Boolean = true,
    // Optional: BottomBar, Sichtbarkeit steuert das untere Padding
    showBottomBar: Boolean = true,
    bottomBar: (@Composable () -> Unit)? = {
        FishITBottomPanel(selected = "all", onSelect = {})
    },
    // TV-only: when true, the header attaches initial focus to the Settings button if available.
    // Useful on first start when no content exists to allow users to reach settings immediately.
    preferSettingsFirstFocus: Boolean = false,
    // TV-only: allow DPAD LEFT to expand chrome (default true). Detail screens can disable this.
    enableDpadLeftChrome: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    // TV device detection
    val ctx = LocalContext.current
    val isTv = remember(ctx) {
        val pm = ctx.packageManager
        pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

    // Chrome state: Visible (default), Collapsed (hidden), Expanded (focus trap + blur)
    // On TV, start collapsed to keep focus in content rows; header/bottom remain visible on phones
    val tvChromeMode = rememberSaveable { mutableStateOf(if (isTv) ChromeMode.Collapsed else ChromeMode.Visible) }
    var focusedRowKey by remember { mutableStateOf<String?>(null) }
    // System-Insets (compute via density to avoid extension import issues)
    val density = LocalDensity.current
    val statusPad = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navPad = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    val seedingInFlight by XtreamImportCoordinator.seederInFlight.collectAsStateWithLifecycle(initialValue = false)

    // Focus manager to push focus back into content after collapsing chrome via BACK
    val focusManager: FocusManager = LocalFocusManager.current

    fun focusContentFromChrome(direction: FocusDirection) {
        // Try a few steps to escape chrome and land in content (middle row will accept focus via RowCore firstFocus)
        repeat(4) { focusManager.moveFocus(direction) }
        com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:Chrome:content")
    }

    // App-Chrome-Höhen
    val topBarHeight: Dp = FishITHeaderHeights.total
    val bottomBarHeight: Dp = FishITBottomHeights.bar

    // Nur vom "Vorhandensein" der BottomBar abhängig, nicht von ihrer Funktions-Identität
    val hasBottomBar = showBottomBar && bottomBar != null

    // Visibility derived from TV mode or explicit flags
    val headerShouldShow = showHeader && (!isTv || tvChromeMode.value != ChromeMode.Collapsed)
    val bottomShouldShow = hasBottomBar && (!isTv || tvChromeMode.value != ChromeMode.Collapsed)

    // Animated content padding (reclaims space smoothly)
    val targetTopPad = statusPad + if (headerShouldShow) topBarHeight else 0.dp
    val targetBottomPad = navPad + if (bottomShouldShow) bottomBarHeight else 0.dp
    val animatedTopPad by animateDpAsState(targetValue = targetTopPad, animationSpec = tween(180), label = "topPad")
    val animatedBottomPad by animateDpAsState(targetValue = targetBottomPad, animationSpec = tween(180), label = "bottomPad")
    val pads = PaddingValues(top = animatedTopPad, bottom = animatedBottomPad)

    val scrimAlpha = if (headerShouldShow) rememberHeaderAlpha(listState) else 0f

    // Auto-collapse on scroll (TV only)
    LaunchedEffect(isTv, listState) {
        if (!isTv) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .collectLatest { tvChromeMode.value = ChromeMode.Collapsed }
    }

    // Focus targets for Expanded trap
    val headerFocus = remember { FocusRequester() }
    val bottomFocus = remember { FocusRequester() }
    var pendingHeaderFocus by remember { mutableStateOf(false) }
    var pendingBottomFocus by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { ev ->
                if (!isTv) return@onPreviewKeyEvent false
                val isDown = ev.type == KeyEventType.KeyDown
                val isUp = ev.type == KeyEventType.KeyUp
                when (ev.key) {
                    Key.Menu -> {
                        com.chris.m3usuite.core.debug.GlobalDebug.logDpad("MENU")
                        tvChromeMode.value = if (tvChromeMode.value == ChromeMode.Expanded) ChromeMode.Collapsed else ChromeMode.Expanded
                        true
                    }
                    Key.Escape, Key.Back -> {
                        // Treat ESC/BACK as chrome-collapse first on TV to avoid closing content/player
                        if (isUp) com.chris.m3usuite.core.debug.GlobalDebug.logDpad("BACK")
                        val expanded = tvChromeMode.value != ChromeMode.Collapsed
                        if (expanded) {
                            if (isDown) {
                                tvChromeMode.value = ChromeMode.Collapsed
                                // After collapsing, nudge focus back into the main content area
                                runCatching {
                                    val moved = focusManager.moveFocus(FocusDirection.Down)
                                    if (!moved) focusManager.moveFocus(FocusDirection.Up)
                                    com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:Chrome:content")
                                }
                            }
                            // Consume both DOWN and UP when collapsing chrome to prevent app/back navigation
                            true
                        } else false
                    }
                    Key.DirectionDown -> {
                        if (isUp) com.chris.m3usuite.core.debug.GlobalDebug.logDpad("DOWN")
                        if (tvChromeMode.value == ChromeMode.Expanded) {
                            // Collapse chrome and push focus into content
                            tvChromeMode.value = ChromeMode.Collapsed
                            focusContentFromChrome(FocusDirection.Down)
                            true
                        } else false
                    }
                    Key.DirectionUp -> {
                        if (isUp) com.chris.m3usuite.core.debug.GlobalDebug.logDpad("UP")
                        if (tvChromeMode.value == ChromeMode.Expanded) {
                            tvChromeMode.value = ChromeMode.Collapsed
                            focusContentFromChrome(FocusDirection.Up)
                            true
                        } else if (tvChromeMode.value == ChromeMode.Collapsed) {
                            // Expand if we are at the very top and the first item of the focused top row is selected
                            val atTop = listState.firstVisibleItemIndex == 0
                            val rowKey = focusedRowKey
                            val rowIdx = rowKey?.let { com.chris.m3usuite.ui.state.readRowFocus(it).index }
                            if (atTop && rowIdx == 0) {
                                tvChromeMode.value = ChromeMode.Expanded
                                pendingHeaderFocus = true
                                true
                            } else false
                        } else false
                    }
                    Key.DirectionLeft -> {
                        if (isUp) com.chris.m3usuite.core.debug.GlobalDebug.logDpad("LEFT")
                        if (isTv && enableDpadLeftChrome) {
                            // Expand chrome when: current row focus is at the very left (index 0),
                            // or when there is no focused row/content yet (empty screen/loading)
                            val rowKey = focusedRowKey
                            val rowIdx = rowKey?.let { com.chris.m3usuite.ui.state.readRowFocus(it).index }
                            val noContent = rowKey == null
                            if (noContent || (rowIdx != null && rowIdx <= 0)) {
                                tvChromeMode.value = ChromeMode.Expanded
                                pendingHeaderFocus = true
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }
                    Key.DirectionRight -> { if (isUp) com.chris.m3usuite.core.debug.GlobalDebug.logDpad("RIGHT"); false }
                    else -> false
                }
        }
    ) {
        // When expanding chrome on TV, wait a frame and then move focus to header
        LaunchedEffect(isTv, tvChromeMode.value) {
            if (isTv && tvChromeMode.value == ChromeMode.Expanded) {
                // Ensure header is composed and focusRequester attached
                delay(16)
                runCatching {
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:Chrome:expandHeader")
                    headerFocus.requestFocus()
                }
            }
        }
        // Deferred focus requests via DPAD UP/DOWN
        LaunchedEffect(pendingHeaderFocus, tvChromeMode.value, isTv) {
            if (isTv && tvChromeMode.value == ChromeMode.Expanded && pendingHeaderFocus) {
                pendingHeaderFocus = false
                delay(16)
                runCatching {
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:Chrome:header")
                    headerFocus.requestFocus()
                }
            }
        }
        LaunchedEffect(pendingBottomFocus, tvChromeMode.value, isTv) {
            if (isTv && tvChromeMode.value == ChromeMode.Expanded && pendingBottomFocus) {
                pendingBottomFocus = false
                delay(16)
                runCatching {
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:Chrome:bottom")
                    bottomFocus.requestFocus()
                }
            }
        }
        AnimatedVisibility(
            visible = seedingInFlight,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusPad + 12.dp)
        ) {
            Surface(
                tonalElevation = 6.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
            ) {
                Text(
                    text = "Import läuft…",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .alpha(0.9f)
                )
            }
        }
        // Inhalt mit korrektem Chrome/Inset-Padding (blur in Expanded on TV)
        val blurModifier = if (isTv && tvChromeMode.value == ChromeMode.Expanded) {
            if (Build.VERSION.SDK_INT >= 31) {
                Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP).asComposeRenderEffect() }
            } else {
                Modifier.drawWithContent {
                    drawContent()
                    drawRect(Color.Black.copy(alpha = 0.22f))
                }
            }
        } else Modifier
        androidx.compose.runtime.CompositionLocalProvider(
            LocalChromeToggle provides ({ tvChromeMode.value = if (tvChromeMode.value == ChromeMode.Expanded) ChromeMode.Collapsed else ChromeMode.Expanded }),
            LocalChromeExpand provides (if (isTv) ({ tvChromeMode.value = ChromeMode.Expanded }) else null),
            LocalChromeRowFocusSetter provides { key: String? ->
                focusedRowKey = key
                if (isTv && key != null) tvChromeMode.value = ChromeMode.Collapsed
            }
        ) {
            Box(Modifier.fillMaxSize().then(blurModifier)) {
                content(pads)
            }
        }

        // Top-Header-Overlay with slide animation
        AnimatedVisibility(
            visible = headerShouldShow,
            enter = slideInVertically(animationSpec = tween(180)) { full -> -full },
            exit = slideOutVertically(animationSpec = tween(180)) { full -> -full }
        ) {
            CompositionLocalProvider(
                LocalHeaderFirstFocus provides (if (isTv && tvChromeMode.value == ChromeMode.Expanded) headerFocus else null),
                LocalChromeOnAction provides (if (isTv) ({ tvChromeMode.value = ChromeMode.Collapsed }) else null),
                com.chris.m3usuite.ui.home.header.LocalPreferSettingsFirstFocus provides (if (isTv) preferSettingsFirstFocus else false)
            ) {
                Box(Modifier.onFocusEvent { st ->
                    if (isTv && (st.isFocused || st.hasFocus)) {
                        // Do not auto-expand on incidental focus; only expand via Menu/DPAD Up
                        com.chris.m3usuite.core.debug.GlobalDebug.logTree("focus:header")
                    }
                }) {
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
        }

        // BottomBar-Overlay (optional) – mit Navigation-Bar-Padding + slide animation
        AnimatedVisibility(
            visible = bottomShouldShow,
            enter = slideInVertically(animationSpec = tween(180)) { full -> full },
            exit = slideOutVertically(animationSpec = tween(180)) { full -> full }
        ) {
            CompositionLocalProvider(
                LocalBottomFirstFocus provides (if (isTv && tvChromeMode.value == ChromeMode.Expanded) bottomFocus else null),
                LocalChromeOnAction provides (if (isTv) ({ tvChromeMode.value = ChromeMode.Collapsed }) else null)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(WindowInsets.navigationBars.asPaddingValues())
                        .onFocusEvent { st -> if (isTv && (st.isFocused || st.hasFocus)) com.chris.m3usuite.core.debug.GlobalDebug.logTree("focus:bottom") },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    bottomBar?.invoke()
                }
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
