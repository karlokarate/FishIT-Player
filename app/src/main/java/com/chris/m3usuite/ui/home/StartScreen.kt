package com.chris.m3usuite.ui.home

import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import com.chris.m3usuite.core.http.RequestHeadersProvider
import com.chris.m3usuite.core.playback.PlayUrlHelper
import com.chris.m3usuite.data.obx.toMediaItem
import com.chris.m3usuite.data.repo.MediaQueryRepository
import com.chris.m3usuite.telegram.live.TelegramLiveRepository
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.navigation.navigateTopLevel
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import com.chris.m3usuite.ui.components.rows.LiveAddTile
import com.chris.m3usuite.ui.components.rows.ReorderableLiveRow
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.OnPrefetchPaged
import com.chris.m3usuite.ui.focus.focusScaleOnTv
import com.chris.m3usuite.ui.fx.tvFocusGlow
import com.chris.m3usuite.ui.layout.FishHeaderData
import com.chris.m3usuite.ui.layout.FishHeaderHost
import com.chris.m3usuite.ui.layout.FishRow
import com.chris.m3usuite.ui.layout.FishRowPaged
import com.chris.m3usuite.ui.layout.LiveFishTile
import com.chris.m3usuite.ui.layout.SeriesFishTile
import com.chris.m3usuite.ui.layout.TelegramFishTile
import com.chris.m3usuite.ui.layout.VodFishTile
import com.chris.m3usuite.ui.layout.primaryTelegramPoster
import com.chris.m3usuite.ui.telemetry.AttachPagingTelemetry
import com.chris.m3usuite.ui.theme.DesignTokens
import com.chris.m3usuite.ui.util.AppImageLoader
import com.chris.m3usuite.ui.util.rememberImageHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun StartScreen(
    navController: NavHostController,
    openLive: (Long) -> Unit,
    openVod: (Long) -> Unit,
    openSeries: (Long) -> Unit,
    initialSearch: String? = null,
    openSearchOnStart: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // Settings bleiben lokal für UI-nahe Dinge (Navigation/Picker)
    val store = remember { SettingsStore(ctx) }

    // VM: Alle Daten-/Nebenwirkungen kommen ab hier aus dem ViewModel
    val vm: StartViewModel = viewModel(factory = StartViewModel.Factory(ctx))

    // -------------- Event Listener --------------
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is StartEvent.Toast ->
                    Toast.makeText(ctx, e.message, Toast.LENGTH_SHORT).show()
                is StartEvent.Failure ->
                    Toast.makeText(ctx, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // UI-gebundene States aus VM
    val ui by vm.ui.collectAsStateWithLifecycle()
    val debouncedQuery by vm.debouncedQuery.collectAsStateWithLifecycle("")
    val libraryTabIndex by vm.headerLibraryTabIndex.collectAsStateWithLifecycle(0)
    val isKid by vm.isKid.collectAsStateWithLifecycle(false)
    val canEditFavorites by vm.canEditFavorites.collectAsStateWithLifecycle(true)
    val canEditWhitelist by vm.canEditWhitelist.collectAsStateWithLifecycle(true)

    // Home-Listen (gemäß bisheriger Logik, aber aus VM)
    val tv by vm.live.collectAsStateWithLifecycle(emptyList())
    val seriesMixed by vm.seriesMixed.collectAsStateWithLifecycle(emptyList())
    val vodMixed by vm.vodMixed.collectAsStateWithLifecycle(emptyList())
    val seriesNewIds by vm.seriesNewIds.collectAsStateWithLifecycle(emptySet())
    val vodNewIds by vm.vodNewIds.collectAsStateWithLifecycle(emptySet())
    val favLive by vm.favLive.collectAsStateWithLifecycle(emptyList())

    // Header: Tab aus Store-Index
    val headerLibraryTab = remember(libraryTabIndex) {
        when (libraryTabIndex) {
            0 -> LibraryTab.Live
            1 -> LibraryTab.Vod
            else -> LibraryTab.Series
        }
    }

    // Playback-Launcher bleibt reines UI-Wiring
    val playbackLauncher = com.chris.m3usuite.playback.rememberPlaybackLauncher(
        onOpenInternal = { pr ->
            val encoded = PlayUrlHelper.encodeUrl(pr.url)
            val mimeArg = pr.mimeType?.let { Uri.encode(it) } ?: ""
            when (pr.type) {
                "vod" -> navController.navigate("player?url=$encoded&type=vod&mediaId=${pr.mediaId ?: -1}&startMs=${pr.startPositionMs ?: -1}&mime=$mimeArg")
                "live" -> navController.navigate("player?url=$encoded&type=live&mediaId=${pr.mediaId ?: -1}&startMs=${pr.startPositionMs ?: -1}&mime=$mimeArg")
                "series" -> navController.navigate("player?url=$encoded&type=series&seriesId=${pr.seriesId ?: -1}&season=${pr.season ?: -1}&episodeNum=${pr.episodeNum ?: -1}&episodeId=${pr.episodeId ?: -1}&startMs=${pr.startPositionMs ?: -1}&mime=$mimeArg")
            }
        }
    )

    // Telegram: UI-Binding (Service/Headers) bleibt in der View
    val telegramLiveRepo = remember { com.chris.m3usuite.telegram.live.TelegramLiveRepository(ctx) }
    val telegramHeaders = remember { RequestHeadersProvider.defaultHeadersBlocking(store) }
    val tgEnabled by store.tgEnabled.collectAsStateWithLifecycle(initialValue = false)
    val tgSelectedCsv by store.tgSelectedChatsCsv.collectAsStateWithLifecycle(initialValue = "")
    val telegramServiceClient = remember { TelegramServiceClient(ctx.applicationContext) }
    val playTelegram: (MediaItem) -> Unit = remember(playbackLauncher, telegramHeaders, telegramServiceClient) {
        { item ->
            scope.launch {
                val chatId = item.tgChatId ?: return@launch
                val messageId = item.tgMessageId ?: return@launch
                val tgUrl = runCatching {
                    PlayUrlHelper.tgPlayUri(chatId = chatId, messageId = messageId, svc = telegramServiceClient)
                        .toString()
                }.getOrElse { err ->
                    android.util.Log.w("StartScreen", "tgPlayUri failed chatId=$chatId messageId=$messageId: ${err.message}")
                    return@launch
                }
                playbackLauncher.launch(
                    com.chris.m3usuite.playback.PlayRequest(
                        type = item.type.ifBlank { "vod" },
                        mediaId = item.id,
                        url = tgUrl,
                        headers = telegramHeaders,
                        title = item.name
                    )
                )
            }
        }
    }
    val openTelegramDetail: (MediaItem) -> Unit = remember(navController, openVod, openSeries) {
        { item ->
            val normalizedType = item.type.lowercase(Locale.getDefault())
            when (normalizedType) {
                "series" -> openSeries(item.id)
                "vod" -> openVod(item.id)
                else -> {
                    val chat = item.tgChatId
                    val message = item.tgMessageId
                    if (chat != null && message != null) {
                        navController.navigate("telegram/$chat/$message")
                    }
                }
            }
        }
    }
    val telegramSelectedChats = remember(tgSelectedCsv) {
        tgSelectedCsv.split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()
    }

    // TV-only: Global Loading Overlay bleibt
    val loading by com.chris.m3usuite.ui.fx.FishSpin.isLoading.collectAsState(initial = false)

    // --- gebündelter Auswahlszustand ---
    var assignState by remember { mutableStateOf(AssignState()) }

    // Suchdialog state
    var showSearch by rememberSaveable("start:globalSearch:open") { mutableStateOf(openSearchOnStart) }
    var searchInput by rememberSaveable("start:globalSearch:query") { mutableStateOf(initialSearch ?: "") }

    // Live-Picker Sichtbarkeit (muss VOR der Live-Row deklariert sein)
    var showLivePicker by rememberSaveable("start:livePicker:visible") { mutableStateOf(false) }

    // Liste & Chrome
    val listState = com.chris.m3usuite.ui.state.rememberRouteListState("start:main")
    val preferSettingsFocus = remember(ui.loading) { false } // Loading-Flag aus VM; Empty behandeln wir unten

    // Initiale Query aus DeepLink übernehmen
    LaunchedEffect(initialSearch) {
        if (!initialSearch.isNullOrBlank()) vm.setQuery(initialSearch)
    }

    HomeChromeScaffold(
        title = "FishIT Player",
        onSearch = { showSearch = true },
        onProfiles = {
            scope.launch {
                store.setCurrentProfileId(-1)
                navController.navigate("gate") {
                    popUpTo("library") { inclusive = true }
                    launchSingleTop = true
                }
            }
        },
        onSettings = if (!canEditWhitelist && isKid) null else {
            {
                val current = navController.currentBackStackEntry?.destination?.route
                if (current != "settings") {
                    navController.navigate("settings") { launchSingleTop = true }
                }
            }
        },
        listState = listState,
        onLogo = {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != "library?q={q}&qs={qs}") {
                navController.navigateTopLevel("library?q=&qs=")
            }
        },
        libraryNav = LibraryNavConfig(
            selected = headerLibraryTab,
            onSelect = { tab ->
                val idx = when (tab) {
                    LibraryTab.Live -> 0
                    LibraryTab.Vod -> 1
                    LibraryTab.Series -> 2
                }
                scope.launch { store.setLibraryTabIndex(idx) }
                val current = navController.currentBackStackEntry?.destination?.route
                if (current != "browse") {
                    navController.navigateTopLevel("browse")
                }
            }
        ),
        preferSettingsFirstFocus = preferSettingsFocus
    ) { pads: PaddingValues ->

        // Hintergrund/Glow wie gehabt
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.background,
                            1f to MaterialTheme.colorScheme.surface
                        )
                    )
            )
            val accent = if (isKid) DesignTokens.KidAccent else DesignTokens.Accent
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = if (isKid) 0.22f else 0.14f), Color.Transparent),
                            radius = with(LocalDensity.current) { 680.dp.toPx() }
                        )
                    )
            )
            com.chris.m3usuite.ui.fx.FishBackground(
                modifier = Modifier.align(Alignment.Center).size(560.dp),
                alpha = 0.05f,
                neutralizeUnderlay = true
            )
        }

        // Globaler Loading-Blur (unverändert)
        if (loading) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    try {
                        if (Build.VERSION.SDK_INT >= 31) {
                            renderEffect = RenderEffect
                                .createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
                    } catch (_: Throwable) {}
                })
                val size = 220.dp
                val infinity = rememberInfiniteTransition(label = "fish-rotate")
                val angle by infinity.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
                    label = "angle"
                )
                Image(
                    painter = painterResource(id = com.chris.m3usuite.R.drawable.fisch_bg),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(size).graphicsLayer { rotationZ = angle }
                )
            }
        }

        // Suche-Dialog (unverändert)
        if (showSearch) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSearch = false },
                title = { Text("Globale Suche") },
                text = {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        singleLine = true,
                        label = { Text("Suchbegriff") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                        vm.setQuery(searchInput)
                        showSearch = false
                    }) { Text("Suchen") }
                },
                dismissButton = {
                    TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { showSearch = false }) { Text("Abbrechen") }
                }
            )
        }

        // Assign-FAB (sichtbar sobald Auswahl > 0)
        if (canEditWhitelist) {
            val totalSel = assignState.selVod.size + assignState.selSeries.size + assignState.selLive.size
            if (totalSel > 0) {
                Box(Modifier.fillMaxSize()) {
                    FloatingActionButton(
                        onClick = { /* unten via KidSelectSheet */ },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 20.dp)
                    ) {
                        AppIconButton(
                            icon = AppIcon.AddKid,
                            contentDescription = "In Profile zuordnen",
                            onClick = { /* unten via KidSelectSheet */ },
                            size = 28.dp
                        )
                    }
                }
            }
        }

        // Suchmodus: Paging-Ströme wie zuvor (UI darf Paging erzeugen)
        val isSearchMode = debouncedQuery.isNotBlank()
        if (isSearchMode) {
            val mediaRepo = remember { MediaQueryRepository(ctx, store) }
            val seriesFlowG = remember(debouncedQuery) { mediaRepo.pagingSearchFilteredFlow("series", debouncedQuery) }
            val vodFlowG = remember(debouncedQuery) { mediaRepo.pagingSearchFilteredFlow("vod", debouncedQuery) }
            val liveFlowG = remember(debouncedQuery) { mediaRepo.pagingSearchFilteredFlow("live", debouncedQuery) }
            val seriesItemsG = seriesFlowG.collectAsLazyPagingItems().also { AttachPagingTelemetry(tag = "home.series", items = it) }
            val vodItemsG = vodFlowG.collectAsLazyPagingItems().also { AttachPagingTelemetry(tag = "home.vod", items = it) }
            val liveItemsG = liveFlowG.collectAsLazyPagingItems().also { AttachPagingTelemetry(tag = "home.live", items = it) }
            val countFlow = remember(seriesItemsG, vodItemsG, liveItemsG) {
                com.chris.m3usuite.ui.state.combinedPagingCountFlow(seriesItemsG, vodItemsG, liveItemsG)
            }
            val searchUi by com.chris.m3usuite.ui.state.collectAsUiState(countFlow) { total -> total == 0 }

            when (val s = searchUi) {
                is com.chris.m3usuite.ui.state.UiState.Loading -> { com.chris.m3usuite.ui.state.LoadingState(); return@HomeChromeScaffold }
                is com.chris.m3usuite.ui.state.UiState.Empty -> { com.chris.m3usuite.ui.state.EmptyState(); return@HomeChromeScaffold }
                is com.chris.m3usuite.ui.state.UiState.Error -> {
                    com.chris.m3usuite.ui.state.ErrorState(text = s.message, onRetry = {
                        seriesItemsG.retry(); vodItemsG.retry(); liveItemsG.retry()
                    })
                    return@HomeChromeScaffold
                }
                is com.chris.m3usuite.ui.state.UiState.Success -> { /* render rows below */ }
            }

            // Suche: Reihen-Ausgabe unverändert (nur Handler teils via VM)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pads),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item("start_series_search_row") {
                    FishRowPaged(
                        items = seriesItemsG,
                        stateKey = "start_series_search",
                        edgeLeftExpandChrome = true,
                        header = FishHeaderData.Text(
                            anchorKey = "start_series_search",
                            text = "Serien – Suchtreffer",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    ) { _, media ->
                        val onSeriesPlayDirect: (MediaItem) -> Unit = { item ->
                            scope.launch { openSeries(item.id) }
                        }
                        val onSeriesAssign: (MediaItem) -> Unit = { item ->
                            if (canEditWhitelist) {
                                if (assignState.active) {
                                    assignState = assignState.copy(
                                        selSeries = if (item.id in assignState.selSeries)
                                            assignState.selSeries - item.id
                                        else
                                            assignState.selSeries + item.id
                                    )
                                } else {
                                    vm.allowForAllKids("series", item.id)
                                }
                            }
                        }
                        SeriesFishTile(
                            media = media,
                            isNew = false,
                            allowAssign = canEditWhitelist,
                            onOpenDetails = { item -> openSeries(item.id) },
                            onPlayDirect = onSeriesPlayDirect,
                            onAssignToKid = onSeriesAssign
                        )
                    }
                }

                item("start_vod_search_row") {
                    FishRowPaged(
                        items = vodItemsG,
                        stateKey = "start_vod_search",
                        edgeLeftExpandChrome = true,
                        header = FishHeaderData.Text(
                            anchorKey = "start_vod_search",
                            text = "Filme – Suchtreffer",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    ) { _, media ->
                        val onVodPlayDirect: (MediaItem) -> Unit = { item ->
                            scope.launch {
                                val req = PlayUrlHelper.forVod(ctx, store, item) ?: return@launch
                                playbackLauncher.launch(
                                    com.chris.m3usuite.playback.PlayRequest(
                                        type = "vod",
                                        mediaId = item.id,
                                        url = req.url,
                                        headers = req.headers,
                                        mimeType = req.mimeType,
                                        title = item.name
                                    )
                                )
                            }
                        }
                        val onVodAssign: (MediaItem) -> Unit = { item ->
                            if (canEditWhitelist) {
                                if (assignState.active) {
                                    assignState = assignState.copy(
                                        selVod = if (item.id in assignState.selVod)
                                            assignState.selVod - item.id
                                        else
                                            assignState.selVod + item.id
                                    )
                                } else {
                                    vm.allowForAllKids("vod", item.id)
                                }
                            }
                        }
                        VodFishTile(
                            media = media,
                            isNew = false,
                            allowAssign = canEditWhitelist,
                            onOpenDetails = { item -> openVod(item.id) },
                            onPlayDirect = onVodPlayDirect,
                            onAssignToKid = onVodAssign
                        )
                    }
                }

                if (tgEnabled) {
                    item("start_vod_search_telegram") {
                        var tgResults by remember(debouncedQuery, tgEnabled) { mutableStateOf<List<MediaItem>>(emptyList()) }
                        LaunchedEffect(debouncedQuery, tgEnabled) {
                            tgResults = if (tgEnabled && debouncedQuery.isNotBlank()) {
                                runCatching { telegramLiveRepo.searchAllVideos(debouncedQuery, limit = 60) }
                                    .getOrDefault(emptyList())
                            } else {
                                emptyList()
                            }
                        }
                        if (tgResults.isNotEmpty()) {
                            FishRow(
                                items = tgResults,
                                stateKey = "start_tg_search",
                                edgeLeftExpandChrome = true,
                                initialFocusEligible = false,
                                header = FishHeaderData.Text(
                                    anchorKey = "start_tg_search",
                                    text = "Telegram – Suchtreffer",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            ) { media ->
                                TelegramFishTile(media = media, onOpenDetails = openTelegramDetail, onPlay = playTelegram)
                            }
                        }
                    }
                }

                item("start_live_search_row") {
                    FishRowPaged(
                        items = liveItemsG,
                        stateKey = "start_live_search",
                        edgeLeftExpandChrome = true,
                        header = FishHeaderData.Text(
                            anchorKey = "start_live_search",
                            text = "LiveTV – Suchtreffer",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    ) { _, media ->
                        val onLivePlayDirect: (MediaItem) -> Unit = { item ->
                            scope.launch {
                                val req = PlayUrlHelper.forLive(ctx, store, item) ?: return@launch
                                playbackLauncher.launch(
                                    com.chris.m3usuite.playback.PlayRequest(
                                        type = "live",
                                        mediaId = item.id,
                                        url = req.url,
                                        headers = req.headers,
                                        mimeType = req.mimeType,
                                        title = item.name
                                    )
                                )
                            }
                        }
                        LiveFishTile(
                            media = media,
                            onOpenDetails = { item -> openLive(item.id) },
                            onPlayDirect = onLivePlayDirect
                        )
                    }
                }
            }

            return@HomeChromeScaffold
        }

        // Nicht-Suchmodus: identisches Layout, Daten aus VM
        val obxRepo = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pads),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (seriesMixed.isNotEmpty()) {
                item("start_series_row") {
                    val onSeriesPlayDirect: (MediaItem) -> Unit = { media -> scope.launch { openSeries(media.id) } }
                    val onSeriesAssign: (MediaItem) -> Unit = { media ->
                        if (canEditWhitelist) {
                            if (assignState.active) {
                                assignState = assignState.copy(
                                    selSeries = if (media.id in assignState.selSeries)
                                        assignState.selSeries - media.id
                                    else
                                        assignState.selSeries + media.id
                                )
                            } else {
                                vm.allowForAllKids("series", media.id)
                            }
                        }
                    }
                    FishRow(
                        items = seriesMixed,
                        stateKey = "start_series",
                        edgeLeftExpandChrome = true,
                        header = FishHeaderData.Text(
                            anchorKey = "start_series",
                            text = "Serien",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    ) { media ->
                        SeriesFishTile(
                            media = media,
                            isNew = seriesNewIds.contains(media.id),
                            allowAssign = canEditWhitelist,
                            onOpenDetails = { item -> openSeries(item.id) },
                            onPlayDirect = onSeriesPlayDirect,
                            onAssignToKid = onSeriesAssign
                        )
                    }
                }
            }

            if (vodMixed.isNotEmpty()) {
                item("start_vod_row") {
                    val onVodPlayDirect: (MediaItem) -> Unit = { media ->
                        scope.launch {
                            val req = PlayUrlHelper.forVod(ctx, store, media) ?: return@launch
                            playbackLauncher.launch(
                                com.chris.m3usuite.playback.PlayRequest(
                                    type = "vod",
                                    mediaId = media.id,
                                    url = req.url,
                                    headers = req.headers,
                                    mimeType = req.mimeType,
                                    title = media.name
                                )
                            )
                        }
                    }
                    val onVodAssign: (MediaItem) -> Unit = { media ->
                        if (canEditWhitelist) {
                            if (assignState.active) {
                                assignState = assignState.copy(
                                    selVod = if (media.id in assignState.selVod)
                                        assignState.selVod - media.id
                                    else
                                        assignState.selVod + media.id
                                )
                            } else {
                                vm.allowForAllKids("vod", media.id)
                            }
                        }
                    }
                    FishRow(
                        items = vodMixed,
                        stateKey = "start_vod",
                        edgeLeftExpandChrome = true,
                        initialFocusEligible = false,
                        header = FishHeaderData.Text(
                            anchorKey = "start_vod",
                            text = "Filme",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    ) { media ->
                        VodFishTile(
                            media = media,
                            isNew = vodNewIds.contains(media.id),
                            allowAssign = canEditWhitelist,
                            onOpenDetails = { item -> openVod(item.id) },
                            onPlayDirect = onVodPlayDirect,
                            onAssignToKid = onVodAssign
                        )
                    }
                }
            }

            if (tgEnabled) {
                item("start_series_telegram_aggregated") {
                    var seriesItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
                    LaunchedEffect(Unit) {
                        val repo = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
                        seriesItems = withContext(Dispatchers.IO) {
                            repo.seriesByProviderKeyNewest("telegram", 0, 60).map { it.toMediaItem(ctx) }
                        }
                    }
                    if (seriesItems.isNotEmpty()) {
                        FishRow(
                            items = seriesItems,
                            stateKey = "start_tg_series_aggregated",
                            edgeLeftExpandChrome = true,
                            header = FishHeaderData.Text(
                                anchorKey = "start_tg_series_header",
                                text = "Telegram Serien",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        ) { media ->
                            val onSeriesPlayDirect: (MediaItem) -> Unit = { item -> scope.launch { openSeries(item.id) } }
                            val onSeriesAssign: (MediaItem) -> Unit = { item ->
                                if (canEditWhitelist) {
                                    if (assignState.active) {
                                        assignState = assignState.copy(
                                            selSeries = if (item.id in assignState.selSeries)
                                                assignState.selSeries - item.id
                                            else
                                                assignState.selSeries + item.id
                                        )
                                    } else {
                                        vm.allowForAllKids("series", item.id)
                                    }
                                }
                            }
                            SeriesFishTile(
                                media = media,
                                isNew = seriesNewIds.contains(media.id),
                                allowAssign = canEditWhitelist,
                                onOpenDetails = { item -> openSeries(item.id) },
                                onPlayDirect = onSeriesPlayDirect,
                                onAssignToKid = onSeriesAssign
                            )
                        }
                    }
                }
                items(telegramSelectedChats, key = { "start_vod_tg_$it" }) { chatId ->
                    StartTelegramRow(
                        chatId = chatId,
                        stateKey = "start:tg:vod:$chatId",
                        enabled = tgEnabled,
                        repository = telegramLiveRepo,
                        onOpenDetails = openTelegramDetail,
                        onPlay = playTelegram
                    )
                }
            }

            val favoritesAvailable = favLive.isNotEmpty()
            val defaultLiveItems = if (favoritesAvailable) favLive else tv
            if (favoritesAvailable || defaultLiveItems.isNotEmpty() || canEditFavorites) {
                if (favoritesAvailable) {
                    item("start_live_epg") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                scope.launch {
                                    val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                                    com.chris.m3usuite.work.SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive)
                                }
                            }) { Text("Jetzt EPG aktualisieren") }
                        }
                    }
                }
                item("start_live_content") {
                    when {
                        canEditFavorites && favoritesAvailable -> {
                            ReorderableLiveRow(
                                items = favLive,
                                onOpen = { openLive(it) },
                                onPlay = { id ->
                                    scope.launch {
                                        val mi = favLive.firstOrNull { it.id == id } ?: return@launch
                                        val req = PlayUrlHelper.forLive(ctx, store, mi) ?: return@launch
                                        playbackLauncher.launch(
                                            com.chris.m3usuite.playback.PlayRequest(
                                                type = "live",
                                                mediaId = mi.id,
                                                url = req.url,
                                                headers = req.headers,
                                                mimeType = req.mimeType,
                                                title = mi.name
                                            )
                                        )
                                    }
                                },
                                onAdd = { showLivePicker = true },
                                onReorder = { newOrder -> vm.onReorderFavorites(newOrder) },
                                onRemove = { removeIds -> vm.onFavoritesRemove(removeIds) },
                                stateKey = "start_live_reorder",
                                initialFocusEligible = false,
                                edgeLeftExpandChrome = true,
                                header = FishHeaderData.Text(
                                    anchorKey = "start_live",
                                    text = "LiveTV",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                        canEditFavorites -> {
                            FishRow(
                                items = defaultLiveItems,
                                stateKey = "start_live_default_edit",
                                edgeLeftExpandChrome = true,
                                initialFocusEligible = false,
                                leading = {
                                    LiveAddTile(
                                        requestInitialFocus = defaultLiveItems.isEmpty(),
                                        onClick = { showLivePicker = true }
                                    )
                                },
                                onPrefetchKeys = { keys ->
                                    val base = 1_000_000_000_000L
                                    val sids = keys
                                        .filter { it >= base && it < 2_000_000_000_000L }
                                        .map { (it - base).toInt() }
                                    if (sids.isNotEmpty()) {
                                        obxRepo.prefetchEpgForVisible(sids, perStreamLimit = 2, parallelism = 4)
                                    }
                                },
                                header = FishHeaderData.Text(
                                    anchorKey = "start_live",
                                    text = "LiveTV",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            ) { media ->
                                val onLivePlayDirect: (MediaItem) -> Unit = { item ->
                                    scope.launch {
                                        val req = PlayUrlHelper.forLive(ctx, store, item) ?: return@launch
                                        playbackLauncher.launch(
                                            com.chris.m3usuite.playback.PlayRequest(
                                                type = "live",
                                                mediaId = item.id,
                                                url = req.url,
                                                headers = req.headers,
                                                mimeType = req.mimeType,
                                                title = item.name
                                            )
                                        )
                                    }
                                }
                                LiveFishTile(
                                    media = media,
                                    onOpenDetails = { item -> openLive(item.id) },
                                    onPlayDirect = onLivePlayDirect
                                )
                            }
                        }
                        favoritesAvailable -> {
                            FishRow(
                                items = favLive,
                                stateKey = "start_live_favorites",
                                edgeLeftExpandChrome = true,
                                initialFocusEligible = false,
                                onPrefetchKeys = { keys ->
                                    val base = 1_000_000_000_000L
                                    val sids = keys
                                        .filter { it >= base && it < 2_000_000_000_000L }
                                        .map { (it - base).toInt() }
                                    if (sids.isNotEmpty()) {
                                        obxRepo.prefetchEpgForVisible(sids, perStreamLimit = 2, parallelism = 4)
                                    }
                                },
                                header = FishHeaderData.Text(
                                    anchorKey = "start_live",
                                    text = "LiveTV",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            ) { media ->
                                val onLivePlayDirect: (MediaItem) -> Unit = { item ->
                                    scope.launch {
                                        val req = PlayUrlHelper.forLive(ctx, store, item) ?: return@launch
                                        playbackLauncher.launch(
                                            com.chris.m3usuite.playback.PlayRequest(
                                                type = "live",
                                                mediaId = item.id,
                                                url = req.url,
                                                headers = req.headers,
                                                mimeType = req.mimeType,
                                                title = item.name
                                            )
                                        )
                                    }
                                }
                                LiveFishTile(
                                    media = media,
                                    onOpenDetails = { item -> openLive(item.id) },
                                    onPlayDirect = onLivePlayDirect
                                )
                            }
                        }
                        defaultLiveItems.isNotEmpty() -> {
                            FishRow(
                                items = defaultLiveItems,
                                stateKey = "start_live_default",
                                edgeLeftExpandChrome = true,
                                initialFocusEligible = false,
                                onPrefetchKeys = { keys ->
                                    val base = 1_000_000_000_000L
                                    val sids = keys
                                        .filter { it >= base && it < 2_000_000_000_000L }
                                        .map { (it - base).toInt() }
                                    if (sids.isNotEmpty()) {
                                        obxRepo.prefetchEpgForVisible(sids, perStreamLimit = 2, parallelism = 4)
                                    }
                                },
                                header = FishHeaderData.Text(
                                    anchorKey = "start_live",
                                    text = "LiveTV",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            ) { media ->
                                val onLivePlayDirect: (MediaItem) -> Unit = { item ->
                                    scope.launch {
                                        val req = PlayUrlHelper.forLive(ctx, store, item) ?: return@launch
                                        playbackLauncher.launch(
                                            com.chris.m3usuite.playback.PlayRequest(
                                                type = "live",
                                                mediaId = item.id,
                                                url = req.url,
                                                headers = req.headers,
                                                mimeType = req.mimeType,
                                                title = item.name
                                            )
                                        )
                                    }
                                }
                                LiveFishTile(
                                    media = media,
                                    onOpenDetails = { item -> openLive(item.id) },
                                    onPlayDirect = onLivePlayDirect
                                )
                            }
                        }
                    }
                }
            }
        }

        // KidSelectSheet (bulk)
        var showAssignSheet by rememberSaveable("start:assign:sheet") { mutableStateOf(false) }
        if (canEditWhitelist) {
            val totalSel = assignState.selVod.size + assignState.selSeries.size + assignState.selLive.size
            if (totalSel > 0) showAssignSheet = true
        }
        if (showAssignSheet) {
            com.chris.m3usuite.ui.components.sheets.KidSelectSheet(
                onConfirm = { kidIds ->
                    scope.launch(Dispatchers.IO) {
                        val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                        kidIds.forEach { kid ->
                            if (assignState.selVod.isNotEmpty()) repo.allowBulk(kid, "vod", assignState.selVod)
                            if (assignState.selSeries.isNotEmpty()) repo.allowBulk(kid, "series", assignState.selSeries)
                            if (assignState.selLive.isNotEmpty()) repo.allowBulk(kid, "live", assignState.selLive)
                        }
                    }
                    scope.launch {
                        Toast.makeText(ctx, "Freigegeben (${assignState.selVod.size + assignState.selSeries.size + assignState.selLive.size})", Toast.LENGTH_SHORT).show()
                        showAssignSheet = false
                        // Selektion leeren
                        assignState = assignState.copy(
                            active = false,
                            selVod = emptySet(),
                            selSeries = emptySet(),
                            selLive = emptySet()
                        )
                    }
                },
                onDismiss = { showAssignSheet = false }
            )
        }

        // Live-Picker (Favoriten hinzufügen) – identisches UI; Persist über VM
        if (canEditFavorites && showLivePicker && !isKid) {
            val scopePick = rememberCoroutineScope()
            var query by rememberSaveable("start:livePicker:query") { mutableStateOf("") }
            val mediaRepo = remember { MediaQueryRepository(ctx, store) }
            var selected by remember { mutableStateOf(favLive.map { it.id }.toSet()) }
            var provider by rememberSaveable("start:livePicker:provider") { mutableStateOf<String?>(null) }
            val pagingFlow = remember(query, provider) {
                when {
                    query.isNotBlank() -> mediaRepo.pagingSearchFilteredFlow("live", query)
                        .map { data ->
                            data.filter { mi ->
                                mi.type == "live" && (provider?.let { p ->
                                    (mi.categoryName ?: "").contains(p, ignoreCase = true)
                                } ?: true)
                            }
                        }
                    else -> mediaRepo.pagingByTypeFilteredFlow("live", provider)
                }
            }
            val liveItems = pagingFlow.collectAsLazyPagingItems()
            ModalBottomSheet(onDismissRequest = { showLivePicker = false }) {
                val addReq = remember { FocusRequester() }
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Sender auswählen", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Suche (TV)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val providers = remember(liveItems.itemCount) {
                            val set = linkedSetOf<String>()
                            for (i in 0 until minOf(liveItems.itemCount, 100)) {
                                val it = liveItems[i]
                                val c = it?.categoryName?.trim()
                                if (!c.isNullOrEmpty()) set += c
                            }
                            set.toList().sorted()
                        }
                        FocusKit.TvRowLight(
                            stateKey = "start_live_picker_providers",
                            itemCount = providers.size + 1,
                            itemKey = { idx -> if (idx == 0) "__all__" else providers[idx - 1] },
                            itemSpacing = 8.dp,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { idx ->
                            if (idx == 0) {
                                FilterChip(
                                    modifier = Modifier
                                        .graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                                        .then(FocusKit.run { Modifier.tvClickable { provider = null } }),
                                    selected = provider == null,
                                    onClick = { provider = null },
                                    label = { Text("Alle") }
                                )
                            } else {
                                val p = providers[idx - 1]
                                FilterChip(
                                    modifier = Modifier
                                        .graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                                        .then(FocusKit.run { Modifier.tvClickable { provider = if (provider == p) null else p } }),
                                    selected = provider == p,
                                    onClick = { provider = if (provider == p) null else p },
                                    label = { Text(p) }
                                )
                            }
                        }
                        val gridState = com.chris.m3usuite.ui.state.rememberRouteGridState("start:livePicker")
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 180.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val refreshing = liveItems.loadState.refresh is androidx.paging.LoadState.Loading && liveItems.itemCount == 0
                            if (refreshing) {
                                items(12) { _ -> com.chris.m3usuite.ui.fx.ShimmerBox(modifier = Modifier.size(180.dp)) }
                            }
                            items(liveItems.itemCount, key = { idx -> liveItems[idx]?.id ?: idx.toLong() }) { idx ->
                                val mi = liveItems[idx] ?: return@items
                                val isSel = mi.id in selected
                                ChannelPickTile(
                                    item = mi,
                                    selected = isSel,
                                    onToggle = {
                                        selected = if (isSel) selected - mi.id else selected + mi.id
                                    },
                                    focusRight = addReq
                                )
                            }
                            val appending = liveItems.loadState.append is androidx.paging.LoadState.Loading
                            if (appending) {
                                items(6) { _ -> com.chris.m3usuite.ui.fx.ShimmerBox(modifier = Modifier.size(180.dp)) }
                            }
                        }
                    }
                    FloatingActionButton(
                        onClick = {
                            scopePick.launch {
                                vm.setFavoritesCsv(selected.joinToString(","))
                                showLivePicker = false
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .focusRequester(addReq)
                            .focusable(true)
                    ) {
                        AppIconButton(
                            icon = AppIcon.BookmarkAdd,
                            contentDescription = "Hinzufügen",
                            onClick = {
                                scopePick.launch {
                                    vm.setFavoritesCsv(selected.joinToString(","))
                                    showLivePicker = false
                                }
                            },
                            size = 28.dp
                        )
                    }
                }
            }
        }
    }
}

// --- Hilfs-UI ---------------------------------------------------------------

@Composable
fun ChannelPickTile(
    item: MediaItem,
    selected: Boolean,
    onToggle: () -> Unit,
    focusRight: FocusRequester
) {
    val shape = RoundedCornerShape(14.dp)
    val borderBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onToggle,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .focusable(true)
            .onFocusEvent { focused = it.isFocused || it.hasFocus }
            .focusScaleOnTv(focusedScale = 1.12f, pressedScale = 1.12f)
            .focusProperties { right = focusRight }
            .border(1.dp, borderBrush, shape)
            .drawWithContent {
                drawContent()
                val grad = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.10f),
                    1f to Color.Transparent
                )
                drawRect(brush = grad)
            }
            .tvFocusGlow(focused = focused, shape = shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sz = 77.dp
            val url = item.logo ?: item.poster
            if (url != null) {
                com.chris.m3usuite.ui.util.AppPosterImage(
                    url = url,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(sz)
                        .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
                    contentScale = ContentScale.Fit,
                    crossfade = false
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = item.name.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StartTelegramRow(
    chatId: Long,
    stateKey: String,
    enabled: Boolean,
    repository: TelegramLiveRepository,
    onOpenDetails: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit
) {
    if (!enabled) return
    val pager = remember(chatId) { repository.pagerForChat(chatId) }
    val pagingItems = pager.flow.collectAsLazyPagingItems()
    val refreshState = pagingItems.loadState.refresh
    val isRefreshing = refreshState is androidx.paging.LoadState.Loading
    if (!isRefreshing && pagingItems.itemCount == 0) return

    var chatTitle by remember(chatId) { mutableStateOf("Telegram $chatId") }
    LaunchedEffect(chatId, enabled) {
        if (enabled) {
            repository.chatTitle(chatId)?.let { chatTitle = it }
        }
    }

    val ctx = LocalContext.current
    val imageHeaders = rememberImageHeaders()
    val telegramPrefetcher: OnPrefetchPaged = remember(ctx, imageHeaders) {
        prefetch@{ indices, items ->
            try {
                val cc = currentCoroutineContext()
                val count = items.itemCount
                if (count <= 0) return@prefetch
                val posters = buildList {
                    for (i in indices) {
                        if (!cc.isActive) break
                        if (i in 0 until count) {
                            runCatching { items[i]?.primaryTelegramPoster() }
                                .getOrNull()
                                ?.let { add(it) }
                        }
                    }
                }
                if (posters.isNotEmpty()) {
                    AppImageLoader.preload(ctx, posters, imageHeaders)
                }
            } catch (_: CancellationException) {
                // Composable/Coroutine disposed while navigating away – benign
            }
        }
    }

    FishRowPaged(
        items = pagingItems,
        stateKey = stateKey,
        edgeLeftExpandChrome = true,
        onPrefetchPaged = telegramPrefetcher,
        header = FishHeaderData.Text(
            anchorKey = stateKey,
            text = "Telegram – $chatTitle",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    ) { _, media ->
        TelegramFishTile(media = media, onOpenDetails = onOpenDetails, onPlay = onPlay)
    }
}

// Preview beibehalten
@Composable
private fun previewMediaList(type: String, count: Int = 10): List<MediaItem> = List(count) { idx ->
    val baseId = when (type) {
        "live" -> 1_000_000_000_000L
        "vod" -> 2_000_000_000_000L
        "series" -> 3_000_000_000_000L
        else -> 9_000_000_000_000L
    }
    MediaItem(
        id = baseId + idx + 1,
        type = type,
        name = when (type) {
            "live" -> "Sender ${idx + 1}"
            "vod" -> "Film ${idx + 1}"
            "series" -> "Serie ${idx + 1}"
            else -> "Item ${idx + 1}"
        },
        year = 2024
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF101010, name = "Start Full (Loaded)")
@Composable
fun StartScreenPreview_FullLoaded() {
    com.chris.m3usuite.ui.theme.AppTheme {
        val listState = rememberLazyListState()
        HomeChromeScaffold(
            title = "Start",
            onSearch = {},
            onProfiles = {},
            onSettings = {},
            listState = listState,
            onLogo = {},
            preferSettingsFirstFocus = false
        ) { pads: PaddingValues ->
            val series = previewMediaList("series", 12)
            val vod = previewMediaList("vod", 12)
            val live = previewMediaList("live", 12)

            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to MaterialTheme.colorScheme.background,
                                1f to MaterialTheme.colorScheme.surface
                            )
                        )
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), Color.Transparent),
                                radius = with(LocalDensity.current) { 680.dp.toPx() }
                            )
                        )
                )
                com.chris.m3usuite.ui.fx.FishBackground(
                    modifier = Modifier.align(Alignment.Center).size(560.dp),
                    alpha = 0.05f,
                    neutralizeUnderlay = true
                )
            }

            FishHeaderHost(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(pads),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item("preview_series_row") {
                        FishRow(
                            items = series,
                            stateKey = "preview_series",
                            edgeLeftExpandChrome = true,
                            header = FishHeaderData.Text(
                                anchorKey = "preview_series",
                                text = "Serien",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        ) { mi ->
                            com.chris.m3usuite.ui.layout.FishTile(
                                title = mi.name,
                                poster = com.chris.m3usuite.R.drawable.fisch_bg,
                                contentScale = ContentScale.Fit,
                                onClick = {}
                            )
                        }
                    }
                    item("preview_vod_row") {
                        FishRow(
                            items = vod,
                            stateKey = "preview_vod",
                            edgeLeftExpandChrome = true,
                            header = FishHeaderData.Text(
                                anchorKey = "preview_vod",
                                text = "Filme",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        ) { mi ->
                            com.chris.m3usuite.ui.layout.FishTile(
                                title = mi.name,
                                poster = com.chris.m3usuite.R.drawable.fisch_bg,
                                contentScale = ContentScale.Fit,
                                onClick = {}
                            )
                        }
                    }
                    item("preview_live_row") {
                        FishRow(
                            items = live,
                            stateKey = "preview_live",
                            edgeLeftExpandChrome = true,
                            header = FishHeaderData.Text(
                                anchorKey = "preview_live",
                                text = "LiveTV",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        ) { mi ->
                            com.chris.m3usuite.ui.layout.FishTile(
                                title = mi.name,
                                poster = com.chris.m3usuite.R.drawable.fisch_bg,
                                contentScale = ContentScale.Fit,
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
