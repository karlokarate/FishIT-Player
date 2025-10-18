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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
// PreviewParameter imports removed (single preview variant only)
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.chris.m3usuite.core.http.RequestHeadersProvider
import com.chris.m3usuite.core.playback.PlayUrlHelper
import com.chris.m3usuite.data.obx.toMediaItem
import com.chris.m3usuite.data.repo.MediaQueryRepository
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.domain.selectors.sortByYearDesc
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.model.isAdultCategory
import com.chris.m3usuite.navigation.navigateTopLevel
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import com.chris.m3usuite.ui.components.rows.ReorderableLiveRow
import com.chris.m3usuite.ui.focus.FocusKit
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
import com.chris.m3usuite.ui.telemetry.AttachPagingTelemetry
import com.chris.m3usuite.ui.theme.DesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val store = remember { SettingsStore(ctx) }
    val mediaRepo = remember { MediaQueryRepository(ctx, store) }
    val obxRepo = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }
    val permRepo = remember { com.chris.m3usuite.data.repo.PermissionRepository(ctx, store) }
    val resumeRepo = remember { com.chris.m3usuite.data.repo.ResumeRepository(ctx) }
    val showAdults by store.showAdults.collectAsStateWithLifecycle(initialValue = false)

    val currentProfileId by store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    var isKid by remember { mutableStateOf(false) }
    LaunchedEffect(currentProfileId) {
        isKid = withContext(Dispatchers.IO) {
            if (currentProfileId > 0) {
                val p = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
                    .boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java)
                    .get(currentProfileId)
                p?.type == "kid"
            } else false
        }
    }

    // Centralized playback launcher: always enabled; settings decide internal/external/ask
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

    val telegramRepo = remember { TelegramContentRepository(ctx, store) }
    val telegramService = remember { TelegramServiceClient(ctx) }
    DisposableEffect(Unit) {
        telegramService.bind()
        telegramService.getAuth()
        onDispose { telegramService.unbind() }
    }
    val telegramHeaders = remember { RequestHeadersProvider.defaultHeadersBlocking(store) }
    val tgEnabled by store.tgEnabled.collectAsStateWithLifecycle(initialValue = false)
    val tgSelectedCsv by store.tgSelectedChatsCsv.collectAsStateWithLifecycle(initialValue = "")
    val playTelegram: (MediaItem) -> Unit = remember(playbackLauncher, telegramHeaders) {
        { item ->
            scope.launch {
                val chatId = item.tgChatId ?: return@launch
                val messageId = item.tgMessageId ?: return@launch
                val tgUrl = "tg://message?chatId=$chatId&messageId=$messageId"
                if (playbackLauncher != null) {
                    playbackLauncher.launch(
                        com.chris.m3usuite.playback.PlayRequest(
                            type = item.type.ifBlank { "vod" },
                            mediaId = item.id,
                            url = tgUrl,
                            headers = telegramHeaders,
                            title = item.name
                        )
                    )
                } else {
                    com.chris.m3usuite.player.PlayerChooser.start(
                        context = ctx,
                        store = store,
                        url = tgUrl,
                        headers = telegramHeaders,
                        startPositionMs = null,
                        mimeType = null
                    ) { _, _ -> }
                }
            }
        }
    }
    val telegramSelectedChats = remember(tgSelectedCsv) {
        tgSelectedCsv.split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()
    }

    var canEditFavorites by remember { mutableStateOf(true) }
    var canEditWhitelist by remember { mutableStateOf(true) }
    LaunchedEffect(currentProfileId) {
        val p = permRepo.current()
        canEditFavorites = p.canEditFavorites
        canEditWhitelist = p.canEditWhitelist
    }

    var series by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var movies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var tv by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    // Mixed rows for Home: recents first, then newest (For Adults filtered by toggle)
    var seriesMixed by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var seriesNewIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var vodMixed by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var homeUiState by remember { mutableStateOf<com.chris.m3usuite.ui.state.UiState<Unit>>(com.chris.m3usuite.ui.state.UiState.Loading) }
    var vodNewIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // Assign Mode (multi-select across rows/search)
    var assignMode by rememberSaveable { mutableStateOf(false) }
    var selVod by remember { mutableStateOf(setOf<Long>()) }
    var selSeries by remember { mutableStateOf(setOf<Long>()) }
    var selLive by remember { mutableStateOf(setOf<Long>()) }

    val vm: StartViewModel = viewModel()
    val debouncedQuery by vm.debouncedQuery.collectAsStateWithLifecycle(initialValue = "")

    suspend fun recomputeMixedRows() {
        withContext(Dispatchers.IO) {
            // Helpers
            suspend fun isAllowed(type: String, id: Long): Boolean = mediaRepo.isAllowed(type, id)

            // Category label maps (for Adults filter)
            val catSer: Map<String, String?> = runCatching {
                obxRepo.categories("series").associateBy({ it.categoryId }, { it.categoryName })
            }.getOrElse { emptyMap() }
            val catVod: Map<String, String?> = runCatching {
                obxRepo.categories("vod").associateBy({ it.categoryId }, { it.categoryName })
            }.getOrElse { emptyMap() }

            // --- Series ---
            run {
                val recMarks = resumeRepo.recentEpisodes(50)
                val serBox = com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxSeries::class.java)
                val recentItems = recMarks.mapNotNull { mk ->
                    val row = serBox.query(com.chris.m3usuite.data.obx.ObxSeries_.seriesId.equal(mk.seriesId.toLong())).build().findFirst()
                    val mi = row?.toMediaItem(ctx)?.copy(categoryName = catSer[row?.categoryId])
                    mi
                }.distinctBy { it.id }
                    .filter { item -> showAdults || !item.isAdultCategory() }
                    .filter { isAllowed("series", it.id) }

                val newestItems = obxRepo.seriesPagedNewest(0, 200).map { it.toMediaItem(ctx).copy(categoryName = it.categoryId?.let { k -> catSer[k] }) }
                    .filter { item -> showAdults || !item.isAdultCategory() }
                    .filter { isAllowed("series", it.id) }

                val recentIds = recentItems.map { it.id }.toSet()
                val newestExcl = newestItems.filter { it.id !in recentIds }
                seriesMixed = recentItems + newestExcl
                seriesNewIds = newestExcl.map { it.id }.toSet()
            }

            // --- VOD ---
            run {
                val recMarks = resumeRepo.recentVod(50)
                val vodBox = com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)
                val recentItems = recMarks.mapNotNull { mk ->
                    val vodId = (mk.mediaId - 2_000_000_000_000L).toInt()
                    val row = vodBox.query(com.chris.m3usuite.data.obx.ObxVod_.vodId.equal(vodId.toLong())).build().findFirst()
                    val mi = row?.toMediaItem(ctx)?.copy(categoryName = catVod[row?.categoryId])
                    if (mi != null) mi else null
                }.distinctBy { it.id }
                    .filter { item -> showAdults || !item.isAdultCategory() }
                    .filter { isAllowed("vod", it.id) }

                val newestItems = obxRepo.vodPagedNewest(0, 200).map { it.toMediaItem(ctx).copy(categoryName = it.categoryId?.let { k -> catVod[k] }) }
                    .filter { item -> showAdults || !item.isAdultCategory() }
                    .filter { isAllowed("vod", it.id) }

                val recentIds = recentItems.map { it.id }.toSet()
                val newestExcl = newestItems.filter { it.id !in recentIds }
                vodMixed = recentItems + newestExcl
                vodNewIds = newestExcl.map { it.id }.toSet()
            }
            if (debouncedQuery.isBlank()) {
                homeUiState = if (seriesMixed.isNotEmpty() || vodMixed.isNotEmpty() || tv.isNotEmpty())
                    com.chris.m3usuite.ui.state.UiState.Success(Unit)
                else com.chris.m3usuite.ui.state.UiState.Empty
            }
        }
    }

    suspend fun reloadFromObx() {
        val filteredSeries = mediaRepo.listByTypeFiltered("series", limit = 600, offset = 0)
        val filteredVod = mediaRepo.listByTypeFiltered("vod", limit = 600, offset = 0)
        val filteredLive = mediaRepo.listByTypeFiltered("live", limit = 600, offset = 0)
        series = sortByYearDesc(filteredSeries, { it.year }, { it.name }).distinctBy { it.id }
        movies = sortByYearDesc(filteredVod, { it.year }, { it.name }).distinctBy { it.id }
        tv = filteredLive.distinctBy { it.id }
    }

    LaunchedEffect(isKid, showAdults) {
        homeUiState = com.chris.m3usuite.ui.state.UiState.Loading
        reloadFromObx()
        recomputeMixedRows()
        val isEmpty = series.isEmpty() && movies.isEmpty() && tv.isEmpty()
        val hasXt = withContext(Dispatchers.IO) { store.hasXtream() }
        if (isEmpty && hasXt) {
            val seeded = withContext(Dispatchers.IO) {
                com.chris.m3usuite.core.xtream.XtreamSeeder.ensureSeeded(
                    context = ctx,
                    store = store,
                    reason = "start",
                    force = false,
                    forceDiscovery = false
                )
            }
            if (seeded?.isSuccess == true) {
                reloadFromObx()
                recomputeMixedRows()
            }
        }
        com.chris.m3usuite.ui.fx.FishSpin.setLoading(false)
    }

    LaunchedEffect(Unit) {
        val changes = merge(
            obxRepo.liveChanges().map { "live" },
            obxRepo.vodChanges().map { "vod" },
            obxRepo.seriesChanges().map { "series" }
        ).debounce(350)

        changes.collectLatest { kind ->
            when (kind) {
                "live" -> {
                    val list = mediaRepo.listByTypeFiltered("live", limit = 600, offset = 0)
                    tv = list.distinctBy { it.id }
                }
                "vod" -> {
                    val list = mediaRepo.listByTypeFiltered("vod", limit = 600, offset = 0)
                    movies = sortByYearDesc(list, { it.year }, { it.name }).distinctBy { it.id }
                    recomputeMixedRows()
                }
                "series" -> {
                    val list = mediaRepo.listByTypeFiltered("series", limit = 600, offset = 0)
                    series = sortByYearDesc(list, { it.year }, { it.name }).distinctBy { it.id }
                    recomputeMixedRows()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        com.chris.m3usuite.metrics.RouteTag.set("home")
        com.chris.m3usuite.core.debug.GlobalDebug.logTree("home:root")
        WorkManager.getInstance(ctx)
            .getWorkInfosForUniqueWorkLiveData("xtream_delta_import_once")
            .asFlow()
            .map { infos -> infos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }?.id }
            .distinctUntilChanged()
            .collectLatest { if (it != null) reloadFromObx() }
    }

    val favCsv by store.favoriteLiveIdsCsv.collectAsStateWithLifecycle(initialValue = "")
    var favLive by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    LaunchedEffect(favCsv, isKid) {
        val rawIds = favCsv.split(',').mapNotNull { it.toLongOrNull() }.distinct()
        if (rawIds.isEmpty()) { favLive = emptyList(); return@LaunchedEffect }
        val allAllowed = mediaRepo.listByTypeFiltered("live", limit = 6000, offset = 0)
        if (allAllowed.isEmpty()) { favLive = emptyList(); return@LaunchedEffect }
        val translated = rawIds.filter { it >= 1_000_000_000_000L }
        val map = allAllowed.associateBy { it.id }
        favLive = translated.mapNotNull { map[it] }.distinctBy { it.id }
        if (debouncedQuery.isBlank()) {
            homeUiState = if (seriesMixed.isNotEmpty() || vodMixed.isNotEmpty() || tv.isNotEmpty() || favLive.isNotEmpty())
                com.chris.m3usuite.ui.state.UiState.Success(Unit)
            else com.chris.m3usuite.ui.state.UiState.Empty
        }
    }

    val listState = com.chris.m3usuite.ui.state.rememberRouteListState("start:main")
    // capture scope in a state-safe way for callbacks without calling composables inside them
    val scopeCurrent by rememberUpdatedState(scope)
    // Live picker dialog flag must be defined before first usage below
    var showLivePicker by remember { mutableStateOf(false) }
    var showSearch by androidx.compose.runtime.saveable.rememberSaveable("start:globalSearch:open") { mutableStateOf(openSearchOnStart) }
    var searchInput by androidx.compose.runtime.saveable.rememberSaveable("start:globalSearch:query") { mutableStateOf(initialSearch ?: "") }
    var showAssignSheet by androidx.compose.runtime.saveable.rememberSaveable("start:assign:sheet") { mutableStateOf(false) }

    LaunchedEffect(initialSearch) {
        if (!initialSearch.isNullOrBlank()) vm.query.value = initialSearch
    }

    val preferSettingsFocus = remember(homeUiState) { homeUiState is com.chris.m3usuite.ui.state.UiState.Empty }
    HomeChromeScaffold(
        title = "FishIT Player",
        onSearch = { showSearch = true },
        onProfiles = {
            val scopeLocal = scopeCurrent
            scopeLocal.launch {
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
        preferSettingsFirstFocus = preferSettingsFocus
    ) { pads: PaddingValues ->
        // TV-only: if Start is empty, auto-expand chrome once and focus Settings for immediate access.
        val isTv = FocusKit.isTvDevice(LocalContext.current)
        val expandChrome = LocalChromeExpand.current
        var didAutoOpenChrome by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(homeUiState, isTv) {
            if (isTv && homeUiState is com.chris.m3usuite.ui.state.UiState.Empty && !didAutoOpenChrome) {
                // Defer a frame to ensure scaffold locals are available
                kotlinx.coroutines.delay(16)
                runCatching { expandChrome?.invoke() }
                didAutoOpenChrome = true
            }
        }
        val loading by com.chris.m3usuite.ui.fx.FishSpin.isLoading.collectAsState(initial = false)
        if (debouncedQuery.isBlank()) {
            when (val s = homeUiState) {
                is com.chris.m3usuite.ui.state.UiState.Loading -> { com.chris.m3usuite.ui.state.LoadingState(); return@HomeChromeScaffold }
                is com.chris.m3usuite.ui.state.UiState.Empty -> { com.chris.m3usuite.ui.state.EmptyState(); return@HomeChromeScaffold }
                is com.chris.m3usuite.ui.state.UiState.Error -> { com.chris.m3usuite.ui.state.ErrorState(s.message, s.retry); return@HomeChromeScaffold }
                is com.chris.m3usuite.ui.state.UiState.Success -> { /* render content */ }
            }
        }

        // Search mode gating using combined paging flows (series/vod/live)
        if (debouncedQuery.isNotBlank()) {
            val seriesFlowG = remember(debouncedQuery) { mediaRepo.pagingSearchFilteredFlow("series", debouncedQuery) }
            val vodFlowG = remember(debouncedQuery) { mediaRepo.pagingSearchFilteredFlow("vod", debouncedQuery) }
            val liveFlowG = remember(debouncedQuery) { mediaRepo.pagingSearchFilteredFlow("live", debouncedQuery) }
            val seriesItemsG = seriesFlowG.collectAsLazyPagingItems()
            val vodItemsG = vodFlowG.collectAsLazyPagingItems()
            val liveItemsG = liveFlowG.collectAsLazyPagingItems()
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
                is com.chris.m3usuite.ui.state.UiState.Success -> { /* proceed */ }
            }
        }
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

        val Accent = if (isKid) DesignTokens.KidAccent else DesignTokens.Accent

        Box(Modifier.fillMaxSize().padding(pads)) {
            LaunchedEffect(Unit) { com.chris.m3usuite.metrics.RouteTag.set("home") }

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
                            colors = listOf(Accent.copy(alpha = if (isKid) 0.22f else 0.14f), Color.Transparent),
                            radius = with(LocalDensity.current) { 680.dp.toPx() }
                        )
                    )
            )
            com.chris.m3usuite.ui.fx.FishBackground(
                modifier = Modifier.align(Alignment.Center).size(560.dp),
                alpha = 0.05f,
                neutralizeUnderlay = true
            )

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
                            vm.query.value = searchInput
                            showSearch = false
                        }) { Text("Suchen") }
                    },
                    dismissButton = {
                        TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { showSearch = false }) { Text("Abbrechen") }
                    }
                )
            }
            // Floating assign action: appears when there is at least one marked tile
            if (canEditWhitelist) {
                val totalSel = selVod.size + selSeries.size + selLive.size
                if (totalSel > 0) {
                    FloatingActionButton(
                        onClick = { showAssignSheet = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp)
                    ) {
                        AppIconButton(
                            icon = AppIcon.AddKid,
                            contentDescription = "In Profile zuordnen",
                            onClick = { showAssignSheet = true },
                            size = 28.dp
                        )
                    }
                }
            }
        
            val assignSelectionContext = com.chris.m3usuite.ui.state.AssignSelectionContext(
                enabled = assignMode,
                isSelected = { mi ->
                    when (mi.type) {
                        "vod" -> mi.id in selVod
                        "series" -> mi.id in selSeries
                        "live" -> mi.id in selLive
                        else -> false
                    }
                },
                toggle = { mi ->
                    when (mi.type) {
                        "vod" -> selVod = if (mi.id in selVod) selVod - mi.id else selVod + mi.id
                        "series" -> selSeries = if (mi.id in selSeries) selSeries - mi.id else selSeries + mi.id
                        "live" -> selLive = if (mi.id in selLive) selLive - mi.id else selLive + mi.id
                        else -> Unit
                    }
                },
                start = { mi ->
                    assignMode = true
                    when (mi.type) {
                        "vod" -> selVod = selVod + mi.id
                        "series" -> selSeries = selSeries + mi.id
                        "live" -> selLive = selLive + mi.id
                        else -> {}
                    }
                }
            )

            FishHeaderHost(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    com.chris.m3usuite.ui.state.LocalAssignBadgeVisible provides canEditWhitelist,
                    com.chris.m3usuite.ui.state.LocalAssignSelection provides assignSelectionContext
                ) {
                val isSearchMode = debouncedQuery.isNotBlank()

                val seriesPagingItems = if (isSearchMode) {
                    val flow = remember(debouncedQuery) { mediaRepo.pagingSearchFilteredFlow("series", debouncedQuery) }
                    flow.collectAsLazyPagingItems().also {
                        AttachPagingTelemetry(tag = "home.series", items = it)
                    }
                } else null

                val vodPagingItems = if (isSearchMode) {
                    val flow = remember(debouncedQuery) { mediaRepo.pagingSearchFilteredFlow("vod", debouncedQuery) }
                    flow.collectAsLazyPagingItems().also {
                        AttachPagingTelemetry(tag = "home.vod", items = it)
                    }
                } else null

                val livePagingItems = if (isSearchMode) {
                    val flow = remember(debouncedQuery) { mediaRepo.pagingSearchFilteredFlow("live", debouncedQuery) }
                    flow.collectAsLazyPagingItems().also {
                        AttachPagingTelemetry(tag = "home.live", items = it)
                    }
                } else null

                val searchUi = if (isSearchMode) {
                    val countFlow = remember(seriesPagingItems, vodPagingItems, livePagingItems) {
                        com.chris.m3usuite.ui.state.combinedPagingCountFlow(
                            seriesPagingItems!!,
                            vodPagingItems!!,
                            livePagingItems!!
                        )
                    }
                    com.chris.m3usuite.ui.state.collectAsUiState(countFlow) { total -> total == 0 }
                } else null

                val onSeriesPlayDirect: (MediaItem) -> Unit = { media ->
                    scope.launch {
                        val sid = media.streamId ?: return@launch
                        withContext(Dispatchers.IO) {
                            val episodes = obxRepo.episodesForSeries(sid)
                            if (episodes.isEmpty()) {
                                runCatching { obxRepo.importSeriesDetailOnce(sid) }
                            }
                        }
                        openSeries(media.id)
                    }
                }

                val onSeriesAssign: (MediaItem) -> Unit = { media ->
                    if (canEditWhitelist) {
                        if (assignMode) {
                            selSeries = if (media.id in selSeries) selSeries - media.id else selSeries + media.id
                        } else {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                                    val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                    kids.forEach { repo.allow(it.id, "series", media.id) }
                                }
                                Toast.makeText(ctx, "Für Kinder freigegeben", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val onVodPlayDirect: (MediaItem) -> Unit = { media ->
                    scope.launch {
                        val req = PlayUrlHelper.forVod(ctx, store, media) ?: return@launch
                        if (playbackLauncher != null) {
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
                        } else {
                            val resumeMs = withContext(Dispatchers.IO) {
                                com.chris.m3usuite.data.repo.ResumeRepository(ctx)
                                    .recentVod(1)
                                    .firstOrNull { it.mediaId == media.id }
                                    ?.positionSecs?.toLong()?.times(1000)
                            }
                            com.chris.m3usuite.player.PlayerChooser.start(
                                context = ctx,
                                store = store,
                                url = req.url,
                                headers = req.headers,
                                startPositionMs = resumeMs,
                                mimeType = req.mimeType
                            ) { s, resolvedMime ->
                                val encoded = PlayUrlHelper.encodeUrl(req.url)
                                val mimeArg = resolvedMime?.let { Uri.encode(it) } ?: ""
                                navController.navigate("player?url=$encoded&type=vod&mediaId=${media.id}&startMs=${s ?: -1}&mime=$mimeArg")
                            }
                        }
                    }
                }

                val onVodAssign: (MediaItem) -> Unit = { media ->
                    if (canEditWhitelist) {
                        if (assignMode) {
                            selVod = if (media.id in selVod) selVod - media.id else selVod + media.id
                        } else {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                                    val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                    kids.forEach { repo.allow(it.id, "vod", media.id) }
                                }
                                Toast.makeText(ctx, "Für Kinder freigegeben", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val onLivePlayDirect: (MediaItem) -> Unit = { media ->
                    scope.launch {
                        val req = PlayUrlHelper.forLive(ctx, store, media) ?: return@launch
                        if (playbackLauncher != null) {
                            playbackLauncher.launch(
                                com.chris.m3usuite.playback.PlayRequest(
                                    type = "live",
                                    mediaId = media.id,
                                    url = req.url,
                                    headers = req.headers,
                                    mimeType = req.mimeType,
                                    title = media.name
                                )
                            )
                        } else {
                            com.chris.m3usuite.player.PlayerChooser.start(
                                context = ctx,
                                store = store,
                                url = req.url,
                                headers = req.headers,
                                startPositionMs = null,
                                mimeType = req.mimeType
                            ) { startMs, resolvedMime ->
                                val encoded = PlayUrlHelper.encodeUrl(req.url)
                                val mimeArg = resolvedMime?.let { Uri.encode(it) } ?: ""
                                navController.navigate(
                                    "player?url=$encoded&type=live&mediaId=${media.id}&startMs=${startMs ?: -1}&mime=$mimeArg"
                                )
                            }
                        }
                    }
                }

                when (searchUi) {
                    is com.chris.m3usuite.ui.state.UiState.Loading -> {
                        com.chris.m3usuite.ui.state.LoadingState()
                        return@CompositionLocalProvider
                    }
                    is com.chris.m3usuite.ui.state.UiState.Empty -> {
                        com.chris.m3usuite.ui.state.EmptyState()
                        return@CompositionLocalProvider
                    }
                    is com.chris.m3usuite.ui.state.UiState.Error -> {
                        com.chris.m3usuite.ui.state.ErrorState(text = searchUi.message, onRetry = {
                            seriesPagingItems?.retry()
                            vodPagingItems?.retry()
                            livePagingItems?.retry()
                        })
                        return@CompositionLocalProvider
                    }
                    else -> {}
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(pads),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (!isSearchMode) {
                        if (seriesMixed.isNotEmpty()) {
                            item("start_series_row") {
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
                            // Serien werden darunter als aggregierte "Telegram Serien"-Row angezeigt.
                        }

                        if (vodMixed.isNotEmpty()) {
                            item("start_vod_row") {
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
                                    ) { _, media ->
                                        SeriesFishTile(media = media, onOpenDetails = { item -> openSeries(item.id) })
                                    }
                                }
                            }
                            items(telegramSelectedChats, key = { "start_vod_tg_$it" }) { chatId ->
                                StartTelegramSection(
                                    chatId = chatId,
                                    stateKey = "start:tg:vod:$chatId",
                                    service = telegramService,
                                    enabled = tgEnabled,
                                    selectionCsv = tgSelectedCsv,
                                    playTelegram = playTelegram,
                                    loader = { telegramRepo.recentVodByChat(chatId, 60, 0) }
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
                                        }) {
                                            Text("Jetzt EPG aktualisieren")
                                        }
                                    }
                                }
                            }
                            item("start_live_content") {
                                when {
                                    canEditFavorites -> {
                                        ReorderableLiveRow(
                                            items = favLive,
                                            onOpen = { openLive(it) },
                                            onPlay = { id ->
                                                scope.launch {
                                                    val mi = favLive.firstOrNull { it.id == id } ?: return@launch
                                                    val req = PlayUrlHelper.forLive(ctx, store, mi) ?: return@launch
                                                    if (playbackLauncher != null) {
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
                                                    } else {
                                                        com.chris.m3usuite.player.PlayerChooser.start(
                                                            context = ctx,
                                                            store = store,
                                                            url = req.url,
                                                            headers = req.headers,
                                                            startPositionMs = null,
                                                            mimeType = req.mimeType
                                                        ) { startMs, resolvedMime ->
                                                            val encoded = PlayUrlHelper.encodeUrl(req.url)
                                                            val mimeArg = resolvedMime?.let { Uri.encode(it) } ?: ""
                                                            navController.navigate("player?url=$encoded&type=live&mediaId=${mi.id}&startMs=${startMs ?: -1}&mime=$mimeArg")
                                                        }
                                                    }
                                                }
                                            },
                                            onAdd = { showLivePicker = true },
                                            onReorder = { newOrder ->
                                                scope.launch {
                                                    store.setFavoriteLiveIdsCsv(newOrder.joinToString(","))
                                                    val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                                                    runCatching { com.chris.m3usuite.work.SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive) }
                                                }
                                            },
                                            onRemove = { removeIds ->
                                                scope.launch {
                                                    val current = store.favoriteLiveIdsCsv.first()
                                                        .split(',').mapNotNull { it.toLongOrNull() }.toMutableList()
                                                    current.removeAll(removeIds.toSet())
                                                    store.setFavoriteLiveIdsCsv(current.joinToString(","))
                                                    val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                                                    runCatching { com.chris.m3usuite.work.SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive) }
                                                }
                                            },
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
                                    favoritesAvailable -> {
                                        FishRow(
                                            items = favLive,
                                            stateKey = "start_live_favorites",
                                            edgeLeftExpandChrome = true,
                                            initialFocusEligible = false,
                                            onPrefetchKeys = { indices, source ->
                                                val sids = indices.mapNotNull { source.getOrNull(it)?.streamId }
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
                                            onPrefetchKeys = { indices, source ->
                                                val sids = indices.mapNotNull { source.getOrNull(it)?.streamId }
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
                    } else {
                        item("start_series_search_row") {
                            seriesPagingItems?.let { pagingItems ->
                                FishRowPaged(
                                    items = pagingItems,
                                    stateKey = "start_series_search",
                                    edgeLeftExpandChrome = true,
                                    header = FishHeaderData.Text(
                                        anchorKey = "start_series_search",
                                        text = "Serien – Suchtreffer",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                ) { _, media ->
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
                        }

                        item("start_vod_search_row") {
                            vodPagingItems?.let { pagingItems ->
                                FishRowPaged(
                                    items = pagingItems,
                                    stateKey = "start_vod_search",
                                    edgeLeftExpandChrome = true,
                                    header = FishHeaderData.Text(
                                        anchorKey = "start_vod_search",
                                        text = "Filme – Suchtreffer",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                ) { _, media ->
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
                        }

                        if (tgEnabled) {
                            item("start_vod_search_telegram") {
                                var tgResults by remember(debouncedQuery) { mutableStateOf<List<MediaItem>>(emptyList()) }
                                LaunchedEffect(debouncedQuery) {
                                    tgResults = withContext(Dispatchers.IO) { telegramRepo.searchAllChats(debouncedQuery, limit = 60) }
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
                                        TelegramFishTile(media = media, onPlay = playTelegram)
                                    }
                                }
                            }
                        }

                        item("start_live_search_row") {
                            livePagingItems?.let { pagingItems ->
                                FishRowPaged(
                                    items = pagingItems,
                                    stateKey = "start_live_search",
                                    edgeLeftExpandChrome = true,
                                    onPrefetchPaged = { indices, lp ->
                                        val count = lp.itemCount
                                        if (count <= 0) return@FishRowPaged
                                        val sids = indices
                                            .filter { it in 0 until count }
                                            .mapNotNull { idx -> lp.peek(idx)?.takeIf { it.type == "live" }?.streamId }
                                        if (sids.isNotEmpty()) {
                                            obxRepo.prefetchEpgForVisible(sids, perStreamLimit = 2, parallelism = 4)
                                        }
                                    },
                                    header = FishHeaderData.Text(
                                        anchorKey = "start_live_search",
                                        text = "LiveTV – Suchtreffer",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                ) { _, media ->
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
        }
        // Bulk assign confirmation via KidSelectSheet
        if (showAssignSheet) {
            com.chris.m3usuite.ui.components.sheets.KidSelectSheet(
                onConfirm = { kidIds ->
                    scope.launch(Dispatchers.IO) {
                        val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                    kidIds.forEach { kid ->
                        if (selVod.isNotEmpty()) repo.allowBulk(kid, "vod", selVod)
                        if (selSeries.isNotEmpty()) repo.allowBulk(kid, "series", selSeries)
                        if (selLive.isNotEmpty()) repo.allowBulk(kid, "live", selLive)
                    }
                }
                scope.launch {
                    Toast.makeText(ctx, "Freigegeben (${selVod.size + selSeries.size + selLive.size})", Toast.LENGTH_SHORT).show()
                    // After changes, immediately reload filtered lists to reflect new allowances
                    reloadFromObx()
                    recomputeMixedRows()
                }
                showAssignSheet = false
                assignMode = false
                selVod = emptySet(); selSeries = emptySet(); selLive = emptySet()
            },
            onDismiss = { showAssignSheet = false }
        )
        }

        // moved above to ensure it is in scope before usage
        if (showLivePicker && !isKid) {
            val scopePick = rememberCoroutineScope()
            var query by androidx.compose.runtime.saveable.rememberSaveable("start:livePicker:query") { mutableStateOf("") }
            var selected by remember { mutableStateOf(favCsv.split(',').mapNotNull { it.toLongOrNull() }.toSet()) }
        var provider by androidx.compose.runtime.saveable.rememberSaveable("start:livePicker:provider") { mutableStateOf<String?>(null) }
        val pagingFlow = remember(query, provider) {
            when {
                query.isNotBlank() -> MediaQueryRepository(ctx, store).pagingSearchFilteredFlow("live", query)
                    .map { data ->
                        data.filter { mi ->
                            mi.type == "live" && (provider?.let { p ->
                                (mi.categoryName ?: "").contains(p, ignoreCase = true)
                            } ?: true)
                        }
                    }
                else -> MediaQueryRepository(ctx, store).pagingByTypeFilteredFlow("live", provider)
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
                                onToggle = { selected = if (isSel) selected - mi.id else selected + mi.id },
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
                            val csv = selected.joinToString(",")
                            store.setFavoriteLiveIdsCsv(csv)
                            val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                            runCatching { com.chris.m3usuite.work.SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive) }
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
                                val csv = selected.joinToString(",")
                                store.setFavoriteLiveIdsCsv(csv)
                                val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                                runCatching { com.chris.m3usuite.work.SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive) }
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

// Closing brace to balance file-level scope
}
}

// Use Compose's official weight extension (foundation.layout.weight)

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
private fun StartTelegramSection(
    chatId: Long,
    stateKey: String,
    service: TelegramServiceClient,
    enabled: Boolean,
    selectionCsv: String,
    playTelegram: (MediaItem) -> Unit,
    loader: suspend () -> List<MediaItem>
) {
    var items by remember(chatId, selectionCsv, enabled) { mutableStateOf(emptyList<MediaItem>()) }
    LaunchedEffect(chatId, enabled, selectionCsv) {
        items = if (enabled) {
            withContext(Dispatchers.IO) { loader() }
        } else {
            emptyList()
        }
    }
    if (items.isEmpty()) return

    var chatTitle by remember(chatId) { mutableStateOf("Telegram $chatId") }
    LaunchedEffect(chatId, enabled, selectionCsv) {
        if (enabled) {
            val resolved = runCatching { service.resolveChatTitles(longArrayOf(chatId)) }.getOrNull()
            resolved?.firstOrNull()?.second?.let { chatTitle = it }
        }
    }

    FishRow(
        items = items,
        stateKey = stateKey,
        edgeLeftExpandChrome = true,
        initialFocusEligible = false,
        header = FishHeaderData.Text(
            anchorKey = stateKey,
            text = "Telegram – $chatTitle",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    ) { media ->
        TelegramFishTile(media = media, onPlay = playTelegram)
    }
}

// -----------------------------------------------------------------------------
// StartScreen – Preview: Voll verdrahtet, befüllter Zustand (nur eine Variante)
// -----------------------------------------------------------------------------

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

            // Hintergrund wie im StartScreen: Verlauf + radialer Glow + FishBackground
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
                    // Serien
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
                    // Filme
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
                    // Live
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
