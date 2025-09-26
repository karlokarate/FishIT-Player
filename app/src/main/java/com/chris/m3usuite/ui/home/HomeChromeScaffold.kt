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

// TV chrome mode (file-private)
private enum class ChromeMode { Visible, Collapsed, Expanded }

// Global toggle provider to allow deep children (e.g., tiles) to trigger burger behavior
val LocalChromeToggle: androidx.compose.runtime.ProvidableCompositionLocal<(() -> Unit)?> = compositionLocalOf { null }

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
    content: @Composable (PaddingValues) -> Unit
) {
    // TV device detection
    val ctx = LocalContext.current
    val isTv = remember(ctx) {
        val pm = ctx.packageManager
        pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

    // Chrome state: Visible (default), Collapsed (hidden), Expanded (focus trap + blur)
    val tvChromeMode = rememberSaveable { mutableStateOf(if (isTv) ChromeMode.Visible else ChromeMode.Visible) }
    // System-Insets (compute via density to avoid extension import issues)
    val density = LocalDensity.current
    val statusPad = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navPad = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    val seedingInFlight by XtreamImportCoordinator.seederInFlight.collectAsStateWithLifecycle(initialValue = false)

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
    // Track LEFT key hold duration for global long-press detection
    val leftPressStartMs = remember { mutableStateOf<Long?>(null) }

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
                        // Log BACK on both down/up; collapse when expanded
                        com.chris.m3usuite.core.debug.GlobalDebug.logDpad("BACK")
                        if (isDown) {
                            if (tvChromeMode.value == ChromeMode.Expanded) { tvChromeMode.value = ChromeMode.Collapsed; true } else false
                        } else false
                    }
                    Key.DirectionDown -> {
                        com.chris.m3usuite.core.debug.GlobalDebug.logDpad("DOWN")
                        if (tvChromeMode.value == ChromeMode.Expanded) { pendingBottomFocus = true; true } else false
                    }
                    Key.DirectionUp -> {
                        com.chris.m3usuite.core.debug.GlobalDebug.logDpad("UP")
                        if (tvChromeMode.value == ChromeMode.Expanded) { pendingHeaderFocus = true; true } else false
                    }
                    Key.DirectionLeft -> {
                        if (isDown) { leftPressStartMs.value = SystemClock.uptimeMillis(); false }
                        else if (isUp) {
                            val start = leftPressStartMs.value; leftPressStartMs.value = null
                            val held = if (start != null) SystemClock.uptimeMillis() - start else 0L
                            if (held >= 300L) {
                                com.chris.m3usuite.core.debug.GlobalDebug.logDpad("LEFT_LONG")
                                tvChromeMode.value = if (tvChromeMode.value == ChromeMode.Expanded) ChromeMode.Collapsed else ChromeMode.Expanded
                                true
                            } else {
                                com.chris.m3usuite.core.debug.GlobalDebug.logDpad("LEFT"); false
                            }
                        } else false
                    }
                    Key.DirectionRight -> { com.chris.m3usuite.core.debug.GlobalDebug.logDpad("RIGHT"); false }
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
            LocalChromeToggle provides ({ tvChromeMode.value = if (tvChromeMode.value == ChromeMode.Expanded) ChromeMode.Collapsed else ChromeMode.Expanded })
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
                LocalChromeOnAction provides (if (isTv) ({ tvChromeMode.value = ChromeMode.Collapsed }) else null)
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
                        .padding(WindowInsets.navigationBars.asPaddingValues()),
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
