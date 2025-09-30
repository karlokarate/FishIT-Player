package com.chris.m3usuite.ui.home

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.Toast
import android.net.Uri
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.chris.m3usuite.navigation.navigateTopLevel
import com.chris.m3usuite.data.repo.MediaQueryRepository
import com.chris.m3usuite.domain.selectors.sortByYearDesc
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.model.isAdultCategory
import com.chris.m3usuite.ui.components.rows.LocalRowItemHeightOverride
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import com.chris.m3usuite.ui.fx.FadeThrough
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
import com.chris.m3usuite.core.playback.PlayUrlHelper
import com.chris.m3usuite.ui.telemetry.AttachPagingTelemetry
import com.chris.m3usuite.data.obx.toMediaItem
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.fx.tvFocusGlow

@SuppressLint("UnusedBoxWithConstraintsScope")
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
    var vodNewIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val vm: StartViewModel = viewModel()
    val homeQuery by vm.query.collectAsStateWithLifecycle(initialValue = "")
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
                    if (mi != null) mi else null
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
    }

    val listState = com.chris.m3usuite.ui.state.rememberRouteListState("start:main")
    // capture scope in a state-safe way for callbacks without calling composables inside them
    val scopeCurrent by rememberUpdatedState(scope)
    // Live picker dialog flag must be defined before first usage below
    var showLivePicker by remember { mutableStateOf(false) }
    var showSearch by androidx.compose.runtime.saveable.rememberSaveable("start:globalSearch:open") { mutableStateOf(openSearchOnStart) }
    var searchInput by androidx.compose.runtime.saveable.rememberSaveable("start:globalSearch:query") { mutableStateOf(initialSearch ?: "") }

    LaunchedEffect(initialSearch) {
        if (!initialSearch.isNullOrBlank()) vm.query.value = initialSearch
    }

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
        showBottomBar = !isKid,
        bottomBar = if (!isKid) {
            {
                com.chris.m3usuite.ui.home.header.FishITBottomPanel(
                    selected = "all",
                    onSelect = { id ->
                        val tab = when (id) { "live" -> 0; "vod" -> 1; "series" -> 2; else -> 3 }
                        scope.launch { store.setLibraryTabIndex(tab) }
                        val current = navController.currentBackStackEntry?.destination?.route
                        if (current != "browse") {
                            navController.navigate("browse") { launchSingleTop = true }
                        }
                    }
                )
            }
        } else null,
        listState = listState,
        onLogo = {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != "library?q={q}&qs={qs}") {
                navController.navigateTopLevel("library?q=&qs=")
            }
        }
    ) { pads: PaddingValues ->
        val loading by com.chris.m3usuite.ui.fx.FishSpin.isLoading.collectAsState(initial = false)
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
                    title = { androidx.compose.material3.Text("Globale Suche") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = searchInput,
                            onValueChange = { searchInput = it },
                            singleLine = true,
                            label = { androidx.compose.material3.Text("Suchbegriff") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                            vm.query.value = searchInput
                            showSearch = false
                        }) { androidx.compose.material3.Text("Suchen") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(modifier = Modifier.focusScaleOnTv(), onClick = { showSearch = false }) { androidx.compose.material3.Text("Abbrechen") }
                    }
                )
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val sectionSpacing = 2.dp
                val titleStyle = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.2f,
                    color = Color.White
                )
                // Compute an optional tile-height override for landscape so the three sections fill the space.
                val cfg = LocalConfiguration.current
                val isLandscape = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                // Section weights: landscape emphasizes Series/VOD equally and keeps Live visible.
                val sectionWeights = if (isLandscape) Triple(0.4f, 0.4f, 0.2f) else Triple(1f, 1f, 1f)
                // Space available for three sections (already excludes header/bottom pads).
                val perSection = (maxHeight - sectionSpacing * 2) / 3f
                // Reserve a small area for the in-card header and the card's inner padding.
                val titleReserve = 30.dp
                val cardInnerVertical = 20.dp
                val rowArea = (perSection - titleReserve - cardInnerVertical).coerceAtLeast(120.dp)
                // Default row height heuristic (keep in sync with HomeRows.rowItemHeight base values)
                val isTablet = cfg.smallestScreenWidthDp >= 600
                val base = when {
                    isTablet && isLandscape -> 230
                    isTablet -> 210
                    isLandscape -> 200
                    else -> 180
                }
                val defaultRowDp = (base * 0.88f).dp
                // In landscape let rows fully grow to fill the card height (minus header/padding)
                val desiredRowDp = if (isLandscape) rowArea else null
                val desiredRowInt: Int? = desiredRowDp?.value?.toInt()
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                ) {
                    // Serien (Paged)
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(sectionWeights.first)) {
                        com.chris.m3usuite.ui.common.AccentCard(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .weight(1f),
                            accent = Accent,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            androidx.compose.material3.Surface(
                                color = Color.Black.copy(alpha = 0.28f),
                                contentColor = Color.White,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                            ) {
                                androidx.compose.material3.Text(
                                    text = "Serien",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(Modifier.size(4.dp))
                            CompositionLocalProvider(
                                LocalRowItemHeightOverride provides desiredRowInt
                            ) {
                            if (debouncedQuery.isBlank()) {
                                com.chris.m3usuite.ui.components.rows.SeriesRow(
                                    items = seriesMixed,
                                    stateKey = "start_series",
                                    newIds = seriesNewIds,
                                    onOpenDetails = { mi -> openSeries(mi.id) },
                                    onPlayDirect = { mi ->
                                        scope.launch {
                                            val sid = mi.streamId ?: return@launch
                                            val obxEp = withContext(Dispatchers.IO) {
                                                val list = obxRepo.episodesForSeries(sid)
                                                if (list.isNotEmpty()) list.firstOrNull() else run {
                                                    obxRepo.importSeriesDetailOnce(sid)
                                                    obxRepo.episodesForSeries(sid).firstOrNull()
                                                }
                                            }
                                            if (obxEp != null) {
                                                openSeries(mi.id)
                                            }
                                        }
                                    },
                                    onAssignToKid = { mi ->
                                        scope.launch {
                                            val kids = withContext(Dispatchers.IO) { com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" } }
                                            withContext(Dispatchers.IO) {
                                                val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                                kids.forEach { repo.allow(it.id, "series", mi.id) }
                                            }
                                            Toast.makeText(ctx, "Für Kinder freigegeben", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    showAssign = canEditWhitelist,
                                    edgeLeftExpandChrome = true
                                )
                            } else {
                                val seriesFlow = remember(debouncedQuery) {
                                    mediaRepo.pagingSearchFilteredFlow("series", debouncedQuery)
                                }
                                val seriesItems = seriesFlow.collectAsLazyPagingItems()
                                AttachPagingTelemetry(tag = "home.series", items = seriesItems)
                                com.chris.m3usuite.ui.components.rows.SeriesRowPaged(
                                    items = seriesItems,
                                    stateKey = "start_series_search",
                                    onOpenDetails = { mi -> openSeries(mi.id) },
                                    onPlayDirect = { mi ->
                                        scope.launch {
                                            val sid = mi.streamId ?: return@launch
                                            val obxEp = withContext(Dispatchers.IO) {
                                                val list = obxRepo.episodesForSeries(sid)
                                                if (list.isNotEmpty()) list.firstOrNull() else run {
                                                    obxRepo.importSeriesDetailOnce(sid)
                                                    obxRepo.episodesForSeries(sid).firstOrNull()
                                                }
                                            }
                                            if (obxEp != null) {
                                                openSeries(mi.id)
                                            }
                                        }
                                    },
                                    onAssignToKid = { mi ->
                                        scope.launch {
                                            val kids = withContext(Dispatchers.IO) { com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" } }
                                            withContext(Dispatchers.IO) {
                                                val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                                kids.forEach { repo.allow(it.id, "series", mi.id) }
                                            }
                                            Toast.makeText(ctx, "Für Kinder freigegeben", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    showAssign = canEditWhitelist,
                                    edgeLeftExpandChrome = true
                                )
                            }
                            }
                        }
                    }

                    // Filme (Paged)
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(sectionWeights.second)) {
                        com.chris.m3usuite.ui.common.AccentCard(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .weight(1f),
                            accent = Accent,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            androidx.compose.material3.Surface(
                                color = Color.Black.copy(alpha = 0.28f),
                                contentColor = Color.White,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                            ) {
                                androidx.compose.material3.Text(
                                    text = "Filme",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(Modifier.size(4.dp))
                            CompositionLocalProvider(
                                LocalRowItemHeightOverride provides desiredRowInt
                            ) {
                            if (debouncedQuery.isBlank()) {
                                com.chris.m3usuite.ui.components.rows.VodRow(
                                    items = vodMixed,
                                    stateKey = "start_vod",
                                    newIds = vodNewIds,
                                    onOpenDetails = { mi -> openVod(mi.id) },
                                    onPlayDirect = { mi ->
                                        scope.launch {
                                            val req = PlayUrlHelper.forVod(ctx, store, mi) ?: return@launch
                                            com.chris.m3usuite.player.PlayerChooser.start(
                                                context = ctx,
                                                store = store,
                                                url = req.url,
                                                headers = req.headers,
                                                startPositionMs = withContext(Dispatchers.IO) {
                                                    com.chris.m3usuite.data.repo.ResumeRepository(ctx)
                                                        .recentVod(1)
                                                        .firstOrNull { it.mediaId == mi.id }
                                                        ?.positionSecs?.toLong()?.times(1000)
                                                },
                                                mimeType = req.mimeType
                                            ) { s, resolvedMime ->
                                                val encoded = PlayUrlHelper.encodeUrl(req.url)
                                                val mimeArg = resolvedMime?.let { Uri.encode(it) } ?: ""
                                                navController.navigate("player?url=$encoded&type=vod&mediaId=${mi.id}&startMs=${s ?: -1}&mime=$mimeArg")
                                            }
                                        }
                                    },
                                    onAssignToKid = { mi ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                                                val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                                kids.forEach { repo.allow(it.id, "vod", mi.id) }
                                            }
                                            Toast.makeText(ctx, "Für Kinder freigegeben", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    showAssign = canEditWhitelist,
                                    initialFocusEligible = false,
                                    edgeLeftExpandChrome = true
                                )
                            } else {
                                val vodFlow = remember(debouncedQuery) {
                                    mediaRepo.pagingSearchFilteredFlow("vod", debouncedQuery)
                                }
                                val vodItems = vodFlow.collectAsLazyPagingItems()
                                AttachPagingTelemetry(tag = "home.vod", items = vodItems)
                                com.chris.m3usuite.ui.components.rows.VodRowPaged(
                                    items = vodItems,
                                    stateKey = "start_vod_search",
                                    onOpenDetails = { mi -> openVod(mi.id) },
                                    onPlayDirect = { mi ->
                                        scope.launch {
                                            val req = PlayUrlHelper.forVod(ctx, store, mi) ?: return@launch
                                            com.chris.m3usuite.player.PlayerChooser.start(
                                                context = ctx,
                                                store = store,
                                                url = req.url,
                                                headers = req.headers,
                                                startPositionMs = withContext(Dispatchers.IO) {
                                                    com.chris.m3usuite.data.repo.ResumeRepository(ctx)
                                                        .recentVod(1)
                                                        .firstOrNull { it.mediaId == mi.id }
                                                        ?.positionSecs?.toLong()?.times(1000)
                                                },
                                                mimeType = req.mimeType
                                            ) { s, resolvedMime ->
                                                val encoded = PlayUrlHelper.encodeUrl(req.url)
                                                val mimeArg = resolvedMime?.let { Uri.encode(it) } ?: ""
                                                navController.navigate("player?url=$encoded&type=vod&mediaId=${mi.id}&startMs=${s ?: -1}&mime=$mimeArg")
                                            }
                                        }
                                    },
                                    onAssignToKid = { mi ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                                                val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                                kids.forEach { repo.allow(it.id, "vod", mi.id) }
                                            }
                                            Toast.makeText(ctx, "Für Kinder freigegeben", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    showAssign = canEditWhitelist,
                                    edgeLeftExpandChrome = true
                                )
                            }
                            }
                        }
                    }

                    // Live (Favoriten oder globale Suche)
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(sectionWeights.third)) {
                        val q = debouncedQuery.trim()
                        val showFavorites = q.isBlank()
                        if (showFavorites) {
                            com.chris.m3usuite.ui.common.AccentCard(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .weight(1f),
                                accent = Accent,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                // Header row inside card: LiveTV + EPG refresh top-right
                                androidx.compose.foundation.layout.Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Surface(
                                        color = Color.Black.copy(alpha = 0.28f),
                                        contentColor = Color.White,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                                    ) {
                                        androidx.compose.material3.Text(
                                            text = "LiveTV",
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                    if (favLive.isNotEmpty()) {
                                        androidx.compose.material3.TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                                            scope.launch {
                                                val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                                                com.chris.m3usuite.work.SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive)
                                            }
                                        }) { androidx.compose.material3.Text("Jetzt EPG aktualisieren") }
                                    }
                                }
                                Spacer(Modifier.size(4.dp))
                            CompositionLocalProvider(
                                LocalRowItemHeightOverride provides desiredRowInt
                            ) {
                                FadeThrough(key = favLive.size) {
                                    androidx.compose.foundation.layout.Column {
                                        val liveFiltered = favLive
                                        if (!canEditFavorites) {
                                            com.chris.m3usuite.ui.components.rows.LiveRow(
                                                items = liveFiltered,
                                                stateKey = "start_live_favorites",
                                                onOpenDetails = { mi -> openLive(mi.id) },
                                                onPlayDirect = { mi ->
                                                    scope.launch {
                                                        val req = PlayUrlHelper.forLive(ctx, store, mi) ?: return@launch
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
                                                },
                                                initialFocusEligible = false,
                                                edgeLeftExpandChrome = true
                                            )
                                        } else {
                                            com.chris.m3usuite.ui.components.rows.ReorderableLiveRow(
                                                items = liveFiltered,
                                                onOpen = { openLive(it) },
                                                onPlay = { id ->
                                                    scope.launch {
                                                        val mi = favLive.firstOrNull { it.id == id } ?: return@launch
                                                        val req = PlayUrlHelper.forLive(ctx, store, mi) ?: return@launch
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
                                                edgeLeftExpandChrome = true
                                            )
                                        }
                                    }
                                }
                                }
                            }
                        } else {
                            com.chris.m3usuite.ui.common.AccentCard(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .weight(1f),
                                accent = Accent,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                // Header label only
                                androidx.compose.material3.Surface(
                                    color = Color.Black.copy(alpha = 0.28f),
                                    contentColor = Color.White,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                                ) {
                                    androidx.compose.material3.Text(
                                        text = "LiveTV",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Spacer(Modifier.size(4.dp))
                            CompositionLocalProvider(
                                LocalRowItemHeightOverride provides desiredRowInt
                            ) {
                                val liveFlow = remember(q) { mediaRepo.pagingSearchFilteredFlow("live", q) }
                                val livePaged = liveFlow.collectAsLazyPagingItems()
                                AttachPagingTelemetry(tag = "home.live.search", items = livePaged)
                                com.chris.m3usuite.ui.components.rows.LiveRowPaged(
                                    items = livePaged,
                                    stateKey = "start_live_search",
                                    onOpenDetails = { mi -> openLive(mi.id) },
                                    onPlayDirect = { mi ->
                                        scope.launch {
                                            val req = PlayUrlHelper.forLive(ctx, store, mi) ?: return@launch
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
                                    },
                                    edgeLeftExpandChrome = true
                                )
                                }
                            }
                        }
                    }
                }
            }
        }
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
                androidx.compose.foundation.layout.Column(
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
                    run {
                        com.chris.m3usuite.ui.tv.TvFocusRow(
                            stateKey = "start_live_picker_providers",
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            itemSpacing = 8.dp,
                            itemCount = providers.size + 1,
                            itemKey = { idx -> if (idx == 0) "__all__" else providers[idx - 1] }
                        ) { idx ->
                            if (idx == 0) {
                                FilterChip(
                                    modifier = Modifier
                                        .graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                                        .then(com.chris.m3usuite.ui.skin.run { Modifier.tvClickable { provider = null } }),
                                    selected = provider == null,
                                    onClick = { provider = null },
                                    label = { Text("Alle") }
                                )
                            } else {
                                val p = providers[idx - 1]
                                FilterChip(
                                    modifier = Modifier
                                        .graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                                        .then(com.chris.m3usuite.ui.skin.run { Modifier.tvClickable { provider = if (provider == p) null else p } }),
                                    selected = provider == p,
                                    onClick = { provider = if (provider == p) null else p },
                                    label = { Text(p) }
                                )
                            }
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
    androidx.compose.material3.Card(
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
        androidx.compose.foundation.layout.Row(
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
