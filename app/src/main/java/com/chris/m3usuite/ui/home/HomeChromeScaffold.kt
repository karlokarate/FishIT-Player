package com.chris.m3usuite.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusProperties
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
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import com.chris.m3usuite.telegram.TgRawLogger
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.home.MiniPlayerHost
import com.chris.m3usuite.ui.home.MiniPlayerState
import com.chris.m3usuite.work.SchedulingGateway
import com.chris.m3usuite.ui.focus.run as focusKitRun

// Rows notify current focused row to drive chrome behavior
val LocalChromeRowFocusSetter: androidx.compose.runtime.ProvidableCompositionLocal<(String?) -> Unit> = compositionLocalOf { {}
}

data class ChromeHeaderFocusRefs(
    val logo: FocusRequester,
    val search: FocusRequester,
    val profile: FocusRequester,
    val settings: FocusRequester
)

data class ChromeBottomFocusRefs(
    val live: FocusRequester,
    val vod: FocusRequester,
    val series: FocusRequester
)

val LocalChromeHeaderFocusRefs: androidx.compose.runtime.ProvidableCompositionLocal<ChromeHeaderFocusRefs?> = compositionLocalOf { null }
val LocalChromeBottomFocusRefs: androidx.compose.runtime.ProvidableCompositionLocal<ChromeBottomFocusRefs?> = compositionLocalOf { null }

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
    selectedBottom: String = "all",
    onSelectBottom: (String) -> Unit = {},
    // TV-only: when true, the header attaches initial focus to the Settings button if available.
    // Useful on first start when no content exists to allow users to reach settings immediately.
    preferSettingsFirstFocus: Boolean = false,
    // TV-only: allow DPAD LEFT to expand chrome (default true). Detail screens can disable this.
    enableDpadLeftChrome: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    // TV device detection
    val ctx = LocalContext.current
    val isTv = remember(ctx) { FocusKit.isTvDevice(ctx) }
    val isPreview = LocalInspectionMode.current
    val settingsStore = remember(ctx) { com.chris.m3usuite.prefs.SettingsStore(ctx) }
    val logOverlayHost = remember { SnackbarHostState() }
    val logOverlayState = if (isPreview) remember { mutableStateOf(false) } else settingsStore.tgLogOverlayEnabled.collectAsStateWithLifecycle(initialValue = false)
    val logVerbosityState = if (isPreview) remember { mutableStateOf(0) } else settingsStore.tgLogVerbosity.collectAsStateWithLifecycle(initialValue = 1)
    val showLogOverlay by logOverlayState
    val tgLogVerbosity by logVerbosityState

    // Chrome state: Visible (default), Collapsed (hidden), Expanded (focus trap + blur)
    // On TV, start collapsed to keep focus in content rows; header/bottom remain visible on phones
    val tvChromeMode = rememberSaveable { mutableStateOf(if (isTv && !isPreview) ChromeMode.Collapsed else ChromeMode.Visible) }
    var focusedRowKey by remember { mutableStateOf<String?>(null) }
    // System-Insets (compute via density to avoid extension import issues)
    val density = LocalDensity.current
    val statusPad = with(density) { (if (isPreview) 24.dp else WindowInsets.statusBars.getTop(this).toDp()) }
    val navPad = with(density) { (if (isPreview) 16.dp else WindowInsets.navigationBars.getBottom(this).toDp()) }

    val seedingInFlight by XtreamImportCoordinator.seederInFlight.collectAsStateWithLifecycle(initialValue = false)
    val telegramSyncState by SchedulingGateway.telegramSyncState.collectAsStateWithLifecycle(initialValue = SchedulingGateway.TelegramSyncState.Idle)

    // Focus manager to push focus back into content after collapsing chrome via BACK
    val focusManager: FocusManager = LocalFocusManager.current

    // App-Chrome-Höhen
    val topBarHeight: Dp = FishITHeaderHeights.total
    val bottomBarHeight: Dp = FishITBottomHeights.bar

    // Nur vom "Vorhandensein" der BottomBar abhängig, nicht von ihrer Funktions-Identität
    val hasBottomBar = showBottomBar

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
    // Light-touch DPAD hooks: only expand chrome on specific conditions, but DO NOT move focus here.
    // Return false so the event continues to propagate and natural focus navigation still applies.
    val chromeDpadModifier = if (isTv) Modifier.onPreviewKeyEvent { ev ->
        val isDown = ev.type == KeyEventType.KeyDown
        when (ev.key) {
            Key.DirectionUp -> {
                if (isDown && tvChromeMode.value != ChromeMode.Expanded) {
                    val atTop = listState.firstVisibleItemIndex == 0
                    val rowIdx = focusedRowKey?.let { com.chris.m3usuite.ui.state.readRowFocus(it).index }
                    if (atTop && rowIdx == 0) {
                        tvChromeMode.value = ChromeMode.Expanded
                        focusedRowKey = null
                    }
                }
                false // allow normal up navigation
            }
            Key.DirectionDown -> false
            Key.DirectionLeft -> {
                if (isDown && enableDpadLeftChrome) {
                    val rowIdx = focusedRowKey?.let { com.chris.m3usuite.ui.state.readRowFocus(it).index }
                    val noContent = focusedRowKey == null
                    val shouldExpand = noContent || (rowIdx != null && rowIdx <= 0)
                    if (shouldExpand && tvChromeMode.value != ChromeMode.Expanded) {
                        tvChromeMode.value = ChromeMode.Expanded
                        focusedRowKey = null
                    }
                }
                false // allow normal left navigation
            }
            else -> false
        }
    } else Modifier

    val telegramBannerVisible = telegramSyncState !is SchedulingGateway.TelegramSyncState.Idle
    val telegramBannerText = remember(telegramSyncState) {
        when (val state = telegramSyncState) {
            is SchedulingGateway.TelegramSyncState.Running -> {
                val processed = state.processedChats.coerceIn(0, state.totalChats)
                if (state.totalChats > 0) "Telegram Sync läuft… ${processed}/${state.totalChats} Chats" else "Telegram Sync läuft…"
            }
            is SchedulingGateway.TelegramSyncState.Success -> {
                val result = state.result
                val parts = buildList {
                    if (result.seriesAdded > 0) add("${result.seriesAdded} Serien")
                    if (result.episodesAdded > 0) add("${result.episodesAdded} Episoden")
                    if (result.moviesAdded > 0) add("${result.moviesAdded} Filme")
                }
                val details = if (parts.isEmpty()) "keine neuen Inhalte" else parts.joinToString(", ")
                "Telegram Sync abgeschlossen – ${details}"
            }
            is SchedulingGateway.TelegramSyncState.Failure -> "Telegram Sync fehlgeschlagen: ${state.error}"
            SchedulingGateway.TelegramSyncState.Idle -> ""
        }
    }
    val telegramSurfaceColor = when (telegramSyncState) {
        is SchedulingGateway.TelegramSyncState.Failure -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
    }
    val telegramTextColor = when (telegramSyncState) {
        is SchedulingGateway.TelegramSyncState.Failure -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    LaunchedEffect(showLogOverlay, tgLogVerbosity, isPreview) {
        if (isPreview) return@LaunchedEffect
        logOverlayHost.currentSnackbarData?.dismiss()
        if (!showLogOverlay || tgLogVerbosity <= 0) return@LaunchedEffect
        TgRawLogger.overlayEvents.collectLatest { message ->
            logOverlayHost.showSnackbar(message)
        }
    }

    LaunchedEffect(telegramSyncState) {
        if (telegramSyncState is SchedulingGateway.TelegramSyncState.Success || telegramSyncState is SchedulingGateway.TelegramSyncState.Failure) {
            delay(6_000)
            SchedulingGateway.acknowledgeTelegramSync()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(chromeDpadModifier)
            .onPreviewKeyEvent { ev ->
                if (!isTv) return@onPreviewKeyEvent false
                val isDown = ev.type == KeyEventType.KeyDown
                val isUp = ev.type == KeyEventType.KeyUp
                when (ev.key) {
                    Key.Menu -> {
                        // If mini-player is visible, focus it instead of toggling chrome
                        if (MiniPlayerState.visible.value) {
                            MiniPlayerState.requestFocus(); true
                        } else {
                            com.chris.m3usuite.core.debug.GlobalDebug.logDpad("MENU")
                            if (tvChromeMode.value == ChromeMode.Expanded) {
                                tvChromeMode.value = ChromeMode.Collapsed
                            } else {
                                focusedRowKey = null
                                tvChromeMode.value = ChromeMode.Expanded
                            }
                            true
                        }
                    }
                    Key.Escape, Key.Back -> {
                        // Treat ESC/BACK as chrome-collapse first on TV to avoid closing content/player
                        if (isUp) com.chris.m3usuite.core.debug.GlobalDebug.logDpad("BACK")
                        val expanded = tvChromeMode.value != ChromeMode.Collapsed
                        if (expanded) {
                            if (isDown) {
                                tvChromeMode.value = ChromeMode.Collapsed
                                runCatching {
                                    val moved = focusManager.moveFocus(FocusDirection.Down)
                                    if (!moved) focusManager.moveFocus(FocusDirection.Up)
                                    com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:Chrome:content")
                                }
                            }
                            true
                        } else false
                    }
                    else -> false
                }
        }
    ) {
        // Focus handled inside overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusPad + 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = seedingInFlight) {
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
            AnimatedVisibility(visible = telegramBannerVisible) {
                Surface(
                    tonalElevation = 6.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = telegramSurfaceColor
                ) {
                    Text(
                        text = telegramBannerText,
                        color = telegramTextColor,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .alpha(0.96f)
                    )
                }
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
            LocalChromeToggle provides ({
                if (tvChromeMode.value == ChromeMode.Expanded) {
                    tvChromeMode.value = ChromeMode.Collapsed
                } else {
                    focusedRowKey = null
                    tvChromeMode.value = ChromeMode.Expanded
                }
            }),
            LocalChromeExpand provides (if (isTv) ({
                focusedRowKey = null
                tvChromeMode.value = ChromeMode.Expanded
            }) else null),
            LocalChromeRowFocusSetter provides { key: String? ->
                focusedRowKey = key
                if (isTv && key != null && tvChromeMode.value != ChromeMode.Expanded) tvChromeMode.value = ChromeMode.Collapsed
            }
        ) {
            val contentFocusBlocker = if (isTv && tvChromeMode.value == ChromeMode.Expanded) Modifier.focusProperties { canFocus = false } else Modifier
            Box(Modifier.fillMaxSize().then(blurModifier).then(contentFocusBlocker)) {
                content(pads)
            }
        }

        // Unified overlay for header + bottom
        com.chris.m3usuite.ui.home.chrome.HomeChromeOverlay(
            expanded = isTv && tvChromeMode.value == ChromeMode.Expanded,
            showHeader = headerShouldShow,
            showBottom = bottomShouldShow,
            title = title,
            onLogo = onLogo,
            onSearch = onSearch,
            onProfiles = onProfiles,
            onSettings = onSettings,
            bottomSelected = selectedBottom,
            onBottomSelect = onSelectBottom,
            statusPad = statusPad,
            navPad = navPad,
            scrimAlpha = scrimAlpha,
            preferSettingsFirstFocus = preferSettingsFirstFocus,
            onActionCollapse = { if (isTv) tvChromeMode.value = ChromeMode.Collapsed }
        )

        // Global TV mini player overlay (bottom-right). When chrome is Expanded, leave it visible but non-focusable.
        MiniPlayerHost(focusEnabled = !(isTv && tvChromeMode.value == ChromeMode.Expanded))

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
        if (!isPreview && showLogOverlay && tgLogVerbosity > 0) {
            val navInsets = WindowInsets.navigationBars.asPaddingValues()
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(navInsets),
                contentAlignment = Alignment.BottomCenter
            ) {
                val extraBottom = if (snackbarHost != null) 72.dp else 12.dp
                SnackbarHost(hostState = logOverlayHost, modifier = Modifier.padding(bottom = extraBottom))
            }
        }
    }
}
