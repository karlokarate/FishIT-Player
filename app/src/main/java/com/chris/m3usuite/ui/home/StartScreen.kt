package com.chris.m3usuite.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.chris.m3usuite.ui.fx.FadeThrough
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.components.rows.SeriesRow
import com.chris.m3usuite.ui.components.rows.VodRow
import com.chris.m3usuite.domain.selectors.sortByYearDesc
import com.chris.m3usuite.domain.selectors.filterGermanTv
import kotlinx.coroutines.launch
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.withContext
import com.chris.m3usuite.work.SchedulingGateway
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.flow.first
// removed unused zIndex/IntOffset/animateFloatAsState/mutableStateListOf
import com.chris.m3usuite.data.repo.MediaQueryRepository
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.animation.core.animateFloat
import com.chris.m3usuite.ui.theme.DesignTokens
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.map
import androidx.paging.filter
import com.chris.m3usuite.data.obx.toMediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    navController: NavHostController,
    openLive: (Long) -> Unit,
    openVod: (Long) -> Unit,
    openSeries: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val mediaRepo = remember { MediaQueryRepository(ctx, store) }

    // Kid/Adult flag
    val currentProfileId by store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    var isKid by remember { mutableStateOf(false) }
    LaunchedEffect(currentProfileId) {
        isKid = withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (currentProfileId > 0) {
                val p = com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java).get(currentProfileId)
                p?.type == "kid"
            } else false
        }
    }

    var series by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var movies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var tv by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var favLive by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var showLivePicker by remember { mutableStateOf(false) }
    val vm: StartViewModel = viewModel()
    val homeQuery by vm.query.collectAsStateWithLifecycle(initialValue = "")
    val debouncedQuery by vm.debouncedQuery.collectAsStateWithLifecycle(initialValue = "")
    val permRepo = remember { com.chris.m3usuite.data.repo.PermissionRepository(ctx, store) }
    var canEditFavorites by remember { mutableStateOf(true) }
    var canEditWhitelist by remember { mutableStateOf(true) }
    LaunchedEffect(currentProfileId) {
        val p = permRepo.current()
        canEditFavorites = p.canEditFavorites
        canEditWhitelist = p.canEditWhitelist
    }

    LaunchedEffect(isKid) {
        val obx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
        suspend fun reloadFromObx() {
            val obxSeries = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.seriesPaged(0, 2000).map { it.toMediaItem(ctx) } }
            val obxVod = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.vodPaged(0, 2000).map { it.toMediaItem(ctx) } }
            val obxLive = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.livePaged(0, 2000).map { it.toMediaItem(ctx) } }
            series = sortByYearDesc(obxSeries, { it.year }, { it.name }).distinctBy { it.id }
            movies = sortByYearDesc(obxVod, { it.year }, { it.name }).distinctBy { it.id }
            // No prefiltering: show Live as-is; user filters can be applied in UI later
            tv = obxLive.distinctBy { it.id }
        }
        reloadFromObx()
        // If empty but Xtream configured, quickly seed minimal lists for immediate visibility
        val isEmpty = series.isEmpty() && movies.isEmpty() && tv.isEmpty()
        val hasXt = withContext(kotlinx.coroutines.Dispatchers.IO) { store.hasXtream() }
        if (isEmpty && hasXt) {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                obx.seedListsQuick(limitPerKind = 200)
            }
            reloadFromObx()
            // No prefiltering on Live; keep whatever the seed loaded
        }
        com.chris.m3usuite.ui.fx.FishSpin.setLoading(false)
    }

    // React to ObjectBox changes (bridge OBX -> Compose)
    LaunchedEffect(Unit) {
        val obx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
        kotlinx.coroutines.coroutineScope {
            launch { obx.liveChanges().collect { 
                val obxLive = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.livePaged(0, 2000).map { it.toMediaItem(ctx) } }
                tv = obxLive.distinctBy { it.id }
            } }
            launch { obx.vodChanges().collect { 
                val obxVod = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.vodPaged(0, 2000).map { it.toMediaItem(ctx) } }
                movies = sortByYearDesc(obxVod, { it.year }, { it.name }).distinctBy { it.id }
            } }
            launch { obx.seriesChanges().collect { 
                val obxSeries = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.seriesPaged(0, 2000).map { it.toMediaItem(ctx) } }
                series = sortByYearDesc(obxSeries, { it.year }, { it.name }).distinctBy { it.id }
            } }
        }
    }

    // Auto-refresh Start lists after Xtream delta import completes
    LaunchedEffect(Unit) {
        val wm = androidx.work.WorkManager.getInstance(ctx)
        var lastId: java.util.UUID? = null
        while (true) {
            try {
                val infos = withContext(kotlinx.coroutines.Dispatchers.IO) { wm.getWorkInfosForUniqueWork("xtream_delta_import_once").get() }
                val done = infos.firstOrNull { it.state == androidx.work.WorkInfo.State.SUCCEEDED }
                if (done != null && done.id != lastId) {
                    lastId = done.id
                    val obx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
                    val obxSeries = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.seriesPaged(0, 2000).map { it.toMediaItem(ctx) } }
                    val obxVod = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.vodPaged(0, 2000).map { it.toMediaItem(ctx) } }
                    val obxLive = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.livePaged(0, 2000).map { it.toMediaItem(ctx) } }
                    series = sortByYearDesc(obxSeries, { it.year }, { it.name }).distinctBy { it.id }
                    movies = sortByYearDesc(obxVod, { it.year }, { it.name }).distinctBy { it.id }
                    tv = obxLive.distinctBy { it.id }
                }
            } catch (_: Throwable) { /* ignore */ }
            kotlinx.coroutines.delay(1200)
        }
    }

    // Favorites for live row on Home
    val favCsv by store.favoriteLiveIdsCsv.collectAsStateWithLifecycle(initialValue = "")
    LaunchedEffect(favCsv, isKid) {
        val rawIds = favCsv.split(',').mapNotNull { it.toLongOrNull() }.distinct()
        if (rawIds.isEmpty()) { favLive = emptyList(); return@LaunchedEffect }
        val obx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
        val allObx = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.livePaged(0, 6000).map { it.toMediaItem(ctx) } }
        if (allObx.isEmpty()) { favLive = emptyList(); return@LaunchedEffect }
        // Only consider OBX-encoded IDs
        val translated = rawIds.filter { it >= 1_000_000_000_000L }
        val map = allObx.associateBy { it.id }
        favLive = translated.mapNotNull { map[it] }.distinctBy { it.id }
    }

    val listState = rememberLazyListState()
    HomeChromeScaffold(
        title = "FishIT Player",
        onSearch = { /* future */ },
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
        onRefresh = {
            scope.launch {
                val rawSeries = withContext(kotlinx.coroutines.Dispatchers.IO) { mediaRepo.listByTypeFiltered("series", 2000, 0) }
                val rawMovies = withContext(kotlinx.coroutines.Dispatchers.IO) { mediaRepo.listByTypeFiltered("vod", 2000, 0) }
                val rawTv = withContext(kotlinx.coroutines.Dispatchers.IO) { mediaRepo.listByTypeFiltered("live", 2000, 0) }
                series = sortByYearDesc(rawSeries, { it.year }, { it.name }).distinctBy { it.id }
                movies = sortByYearDesc(rawMovies, { it.year }, { it.name }).distinctBy { it.id }
                // No prefilter; show Live as-is
                tv = rawTv.distinctBy { it.id }
            }
        },
        bottomBar = if (isKid) ({}) else {
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
        },
        listState = listState,
        onLogo = {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != "library") {
                navController.navigate("library") { launchSingleTop = true }
            }
        }
    ) { pads ->
        // Global loading overlay with rotating fish and background blur
        val loading by com.chris.m3usuite.ui.fx.FishSpin.isLoading.collectAsState(initial = false)
        if (loading) {
            Box(Modifier.fillMaxSize()) {
                // Blur underlay content via graphicsLayer renderEffect when available
                Box(Modifier.fillMaxSize().graphicsLayer {
                    try { if (Build.VERSION.SDK_INT >= 31) {
                        renderEffect = RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                    }} catch (_: Throwable) {}
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
                    painter = painterResource(id = com.chris.m3usuite.R.drawable.fisch),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(size).graphicsLayer { rotationZ = angle }
                )
            }
        }
        val Accent = if (isKid) DesignTokens.KidAccent else DesignTokens.Accent
        Box(Modifier.fillMaxSize().padding(pads)) {
            LaunchedEffect(Unit) { com.chris.m3usuite.metrics.RouteTag.set("home") }
            // Background
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
                alpha = 0.05f
            )
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                item("search") {
                    OutlinedTextField(
                        value = homeQuery,
                        onValueChange = { vm.query.value = it },
                        singleLine = true,
                        label = { Text("Suche (global)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item("hdr_series") {
                    Text("Serien", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp))
                }
                item("row_series") {
                    com.chris.m3usuite.ui.common.AccentCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        accent = Accent
                    ) {
                        val seriesFlow = remember(debouncedQuery) {
                            androidx.paging.Pager(
                                config = androidx.paging.PagingConfig(pageSize = 60, initialLoadSize = 60, prefetchDistance = 30),
                                pagingSourceFactory = {
                                    com.chris.m3usuite.data.repo.ObxSeriesPagingSource(
                                        context = ctx,
                                        store = store,
                                        categoryId = null,
                                        query = debouncedQuery.takeIf { it.isNotBlank() }
                                    )
                                }
                            ).flow
                        }
                        val seriesItems = seriesFlow.collectAsLazyPagingItems()
                        com.chris.m3usuite.ui.components.rows.SeriesRowPaged(
                            items = seriesItems,
                            onOpenDetails = { mi -> openSeries(mi.id) },
                            onPlayDirect = { mi ->
                                scope.launch {
                                    val sid = mi.streamId ?: return@launch
                                    val storeLocal = store
                                    val last = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.chris.m3usuite.data.repo.ResumeRepository(ctx).recentEpisodes(50).firstOrNull { it.seriesId == sid }
                                    }
                                    // Prefer OBX episodes (on-demand import if missing)
                                    val obxRepo = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, storeLocal)
                                    suspend fun pickEpisode(): com.chris.m3usuite.data.obx.ObxEpisode? {
                                        val list = withContext(kotlinx.coroutines.Dispatchers.IO) { obxRepo.episodesForSeries(sid) }
                                        if (list.isNotEmpty()) return list.firstOrNull()
                                        // Import series details once, then retry
                                        withContext(kotlinx.coroutines.Dispatchers.IO) { obxRepo.importSeriesDetailOnce(sid) }
                                        return withContext(kotlinx.coroutines.Dispatchers.IO) { obxRepo.episodesForSeries(sid).firstOrNull() }
                                    }
                                    val obxEp = pickEpisode()
                                    if (obxEp != null) {
                                        // Without stable episodeId in OBX, open series details to start playback there
                                        openSeries(mi.id)
                                    }
                                }
                            },
                            onAssignToKid = { mi ->
                                scope.launch {
                                    val kids = withContext(kotlinx.coroutines.Dispatchers.IO) { com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" } }
                                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                        kids.forEach { repo.allow(it.id, "series", mi.id) }
                                    }
                                    android.widget.Toast.makeText(ctx, "Für Kinder freigegeben", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            showAssign = canEditWhitelist
                        )
                    }
                }
                item("hdr_movies") {
                    Text("Filme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp))
                }
                item("row_movies") {
                    com.chris.m3usuite.ui.common.AccentCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        accent = Accent
                    ) {
                        val vodFlow = remember(debouncedQuery) {
                            androidx.paging.Pager(
                                config = androidx.paging.PagingConfig(pageSize = 60, initialLoadSize = 60, prefetchDistance = 30),
                                pagingSourceFactory = {
                                    com.chris.m3usuite.data.repo.ObxVodPagingSource(
                                        context = ctx,
                                        store = store,
                                        categoryId = null,
                                        query = debouncedQuery.takeIf { it.isNotBlank() }
                                    )
                                }
                            ).flow
                        }
                        val vodItems = vodFlow.collectAsLazyPagingItems()
                        com.chris.m3usuite.ui.components.rows.VodRowPaged(
                            items = vodItems,
                            onOpenDetails = { mi -> openVod(mi.id) },
                            onPlayDirect = { mi ->
                                scope.launch {
                                    val url = run {
                                        if (mi.source == "TG" && mi.tgChatId != null && mi.tgMessageId != null) "tg://message?chatId=${mi.tgChatId}&messageId=${mi.tgMessageId}"
                                        else mi.url ?: run {
                                            val port = store.xtPort.first()
                                            val scheme = if (port == 443) "https" else "http"
                                            val http = com.chris.m3usuite.core.http.HttpClientFactory.create(ctx, store)
                                            val client = com.chris.m3usuite.core.xtream.XtreamClient(http)
                                            val caps = com.chris.m3usuite.core.xtream.ProviderCapabilityStore(ctx)
                                            val ports = com.chris.m3usuite.core.xtream.EndpointPortStore(ctx)
                                            client.initialize(scheme, store.xtHost.first(), store.xtUser.first(), store.xtPass.first(), basePath = null, store = caps, portStore = ports, portOverride = port)
                                            mi.streamId?.let { client.buildVodPlayUrl(it, null) }
                                        }
                                    } ?: return@launch
                                    val headers = buildMap<String,String> {
                                        val ua = store.userAgent.first(); val ref = store.referer.first()
                                        if (ua.isNotBlank()) put("User-Agent", ua); if (ref.isNotBlank()) put("Referer", ref)
                                    }
                                    com.chris.m3usuite.player.PlayerChooser.start(
                                        context = ctx,
                                        store = store,
                                        url = url,
                                        headers = headers,
                                        startPositionMs = withContext(kotlinx.coroutines.Dispatchers.IO) { com.chris.m3usuite.data.repo.ResumeRepository(ctx).recentVod(1).firstOrNull { it.mediaId == mi.id }?.positionSecs?.toLong()?.times(1000) }
                                    ) { s ->
                                        val encoded = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8.name())
                                        navController.navigate("player?url=$encoded&type=vod&mediaId=${mi.id}&startMs=${s ?: -1}")
                                    }
                                }
                            },
                            onAssignToKid = { mi ->
                                scope.launch {
                                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                                        val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                        kids.forEach { repo.allow(it.id, "vod", mi.id) }
                                    }
                                    android.widget.Toast.makeText(ctx, "Für Kinder freigegeben", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            showAssign = canEditWhitelist
                        )
                    }
                }
                // TV (Favoriten oder globale Suche)
                item("row_tv") {
                    Box(Modifier.padding(top = 4.dp)) {
                        val q = debouncedQuery.trim().lowercase()
                        val liveSearch: List<MediaItem> = if (q.isBlank()) emptyList() else run {
                            val all = runCatching { kotlinx.coroutines.runBlocking { mediaRepo.listByTypeFiltered("live", 6000, 0) } }.getOrDefault(emptyList())
                            all.filter { it.name.lowercase().contains(q) || (it.categoryName ?: "").lowercase().contains(q) }
                        }
                        val liveItems = if (q.isBlank()) favLive else liveSearch
                        if (liveItems.isEmpty()) {
                            if (q.isBlank() && favLive.isEmpty()) {
                                // Show a paged global Live row (non-favorites) with skeletons
                                com.chris.m3usuite.ui.common.AccentCard(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    accent = Accent
                                ) {
                                    val liveFlow = remember {
                                        androidx.paging.Pager(
                                            config = androidx.paging.PagingConfig(pageSize = 60, initialLoadSize = 60, prefetchDistance = 30),
                                            pagingSourceFactory = {
                                                com.chris.m3usuite.data.repo.ObxLivePagingSource(
                                                    context = ctx,
                                                    store = store,
                                                    categoryId = null,
                                                    query = null
                                                )
                                            }
                                        ).flow
                                    }
                                    val livePaged = liveFlow.collectAsLazyPagingItems()
                                    com.chris.m3usuite.ui.components.rows.LiveRowPaged(
                                        items = livePaged,
                                        onOpenDetails = { mi -> openLive(mi.id) },
                                        onPlayDirect = { mi ->
                                            scope.launch {
                                                val url = run {
                                                    if (mi.source == "TG" && mi.tgChatId != null && mi.tgMessageId != null) "tg://message?chatId=${mi.tgChatId}&messageId=${mi.tgMessageId}"
                                                    else mi.url ?: run {
                                                        val port = store.xtPort.first()
                                                        val scheme = if (port == 443) "https" else "http"
                                                        val http = com.chris.m3usuite.core.http.HttpClientFactory.create(ctx, store)
                                                        val client = com.chris.m3usuite.core.xtream.XtreamClient(http)
                                                        val caps = com.chris.m3usuite.core.xtream.ProviderCapabilityStore(ctx)
                                                        val ports = com.chris.m3usuite.core.xtream.EndpointPortStore(ctx)
                                                        client.initialize(scheme, store.xtHost.first(), store.xtUser.first(), store.xtPass.first(), basePath = null, store = caps, portStore = ports, portOverride = port)
                                                        mi.streamId?.let { client.buildLivePlayUrl(it) }
                                                    }
                                                } ?: return@launch
                                                val headers = buildMap<String, String> {
                                                    val ua = store.userAgent.first(); val ref = store.referer.first()
                                                    if (ua.isNotBlank()) put("User-Agent", ua)
                                                    if (ref.isNotBlank()) put("Referer", ref)
                                                }
                                                com.chris.m3usuite.player.PlayerChooser.start(
                                                    context = ctx,
                                                    store = store,
                                                    url = url,
                                                    headers = headers,
                                                    startPositionMs = null
                                                ) { startMs ->
                                                    val encoded = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8.name())
                                                    navController.navigate("player?url=$encoded&type=live&mediaId=${mi.id}&startMs=${startMs ?: -1}")
                                                }
                                            }
                                        }
                                    )
                                }
                            } else {
                                if (canEditFavorites) {
                                    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                                        item {
                                            Card(
                                                modifier = Modifier.size(200.dp, 112.dp).padding(end = 12.dp),
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(8.dp)) {
                                                    AppIconButton(icon = AppIcon.BookmarkAdd, contentDescription = "Sender hinzufügen", onClick = { showLivePicker = true }, size = 36.dp)
                                                }
                                            }
                                        }
                                    }
                                }
                                if (homeQuery.isNotBlank()) {
                                    // Keine Treffer Hinweis für Live bei Suche
                                    Text(
                                        "Keine Treffer",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        } else {
                            com.chris.m3usuite.ui.common.AccentCard(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                accent = Accent
                            ) {
                                FadeThrough(key = favLive.size) {
                                    androidx.compose.foundation.layout.Column {
                                        if (liveItems.isNotEmpty() && q.isBlank()) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                                                        SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive)
                                                    }
                                                }) { Text("Jetzt EPG aktualisieren") }
                                            }
                                        }
                                        val liveFiltered = liveItems
                                        if (!canEditFavorites) {
                                            com.chris.m3usuite.ui.components.rows.LiveRow(
                                                items = liveFiltered,
                                                onOpenDetails = { mi -> openLive(mi.id) },
                                                onPlayDirect = { mi ->
                                                    scope.launch {
                                                        val url = mi.url ?: return@launch
                                                        val headers = buildMap<String, String> {
                                                            val ua = store.userAgent.first(); val ref = store.referer.first()
                                                            if (ua.isNotBlank()) put("User-Agent", ua)
                                                            if (ref.isNotBlank()) put("Referer", ref)
                                                        }
                                                        com.chris.m3usuite.player.PlayerChooser.start(
                                                            context = ctx,
                                                            store = store,
                                                            url = url,
                                                            headers = headers,
                                                            startPositionMs = null
                                                        ) { startMs ->
                                                            val encoded = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8.name())
                                                            navController.navigate("player?url=$encoded&type=live&mediaId=${mi.id}&startMs=${startMs ?: -1}")
                                                        }
                                                    }
                                                }
                                            )
                                        } else {
                                            com.chris.m3usuite.ui.components.rows.ReorderableLiveRow(
                                                items = liveFiltered,
                                                onOpen = { openLive(it) },
                                                onPlay = { id ->
                                                    scope.launch {
                                                        val mi = favLive.firstOrNull { it.id == id } ?: return@launch
                                                        val url = mi.url ?: return@launch
                                                        val headers = buildMap<String, String> {
                                                            val ua = store.userAgent.first(); val ref = store.referer.first()
                                                            if (ua.isNotBlank()) put("User-Agent", ua)
                                                            if (ref.isNotBlank()) put("Referer", ref)
                                                        }
                                                        com.chris.m3usuite.player.PlayerChooser.start(
                                                            context = ctx,
                                                            store = store,
                                                            url = url,
                                                            headers = headers,
                                                            startPositionMs = null
                                                        ) { startMs ->
                                                            val encoded = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8.name())
                                                            navController.navigate("player?url=$encoded&type=live&mediaId=${mi.id}&startMs=${startMs ?: -1}")
                                                        }
                                                    }
                                                },
                                                onAdd = { showLivePicker = true },
                                                onReorder = { newOrder -> scope.launch {
                                                    store.setFavoriteLiveIdsCsv(newOrder.joinToString(","))
                                                    val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                                                    runCatching { SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive) }
                                                } },
                                                onRemove = { removeIds ->
                                                    scope.launch {
                                                        val current = store.favoriteLiveIdsCsv.first().split(',').mapNotNull { it.toLongOrNull() }.toMutableList()
                                                        current.removeAll(removeIds.toSet())
                                                        store.setFavoriteLiveIdsCsv(current.joinToString(","))
                                                        val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                                                        runCatching { SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive) }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    // Live picker sheet: multi-select grid + search + provider chips
    // Disable for kid profiles (read-only favorites)
    if (showLivePicker && !isKid) {
        val scopePick = rememberCoroutineScope()
        var query by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf(favCsv.split(',').mapNotNull { it.toLongOrNull() }.toSet()) }
        var provider by remember { mutableStateOf<String?>(null) }
        val pagingFlow = remember(query, provider) {
            when {
                query.isNotBlank() -> MediaQueryRepository(ctx, store).pagingSearchFilteredFlow(query)
                    .map { data -> data.filter { mi -> mi.type == "live" && (provider?.let { p -> (mi.categoryName ?: "").contains(p, ignoreCase = true) } ?: true) } }
                else -> MediaQueryRepository(ctx, store).pagingByTypeFilteredFlow("live", provider)
            }
        }
        val liveItems = pagingFlow.collectAsLazyPagingItems()
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showLivePicker = false }) {
            val addReq = remember { FocusRequester() }
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sender auswählen", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Suche (TV)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // Provider-Chips (aus categoryName) – einfache Ableitung aus aktueller Seite
                val providers = remember(liveItems.itemCount) {
                    val set = linkedSetOf<String>()
                    for (i in 0 until minOf(liveItems.itemCount, 100)) {
                        val it = liveItems[i]
                        val c = it?.categoryName?.trim()
                        if (!c.isNullOrEmpty()) set += c
                    }
                    set.toList().sorted()
                }
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    item { FilterChip(modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha), selected = provider == null, onClick = { provider = null }, label = { Text("Alle") }) }
                    items(providers) { p -> FilterChip(modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha), selected = provider == p, onClick = { provider = if (provider == p) null else p }, label = { Text(p) }) }
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 180.dp), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val refreshing = liveItems.loadState.refresh is androidx.paging.LoadState.Loading && liveItems.itemCount == 0
                    if (refreshing) {
                        items(12) { _ -> com.chris.m3usuite.ui.fx.ShimmerBox(modifier = Modifier.size(180.dp)) }
                    }
                    items(liveItems.itemCount, key = { idx -> liveItems[idx]?.id ?: idx.toLong() }) { idx ->
                        val mi = liveItems[idx] ?: return@items
                        val isSel = mi.id in selected
                        ChannelPickTile(item = mi, selected = isSel, onToggle = { selected = if (isSel) selected - mi.id else selected + mi.id }, focusRight = addReq)
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
                        runCatching { SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive) }
                        showLivePicker = false
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).focusRequester(addReq).focusable(true)
            ) { AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.BookmarkAdd, contentDescription = "Hinzufügen", onClick = {
                    scopePick.launch {
                        val csv = selected.joinToString(",")
                        store.setFavoriteLiveIdsCsv(csv)
                        val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                        runCatching { SchedulingGateway.refreshFavoritesEpgNow(ctx, aggressive = aggressive) }
                        showLivePicker = false
                    }
                }, size = 28.dp) }
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
    androidx.compose.material3.Card(
        onClick = onToggle,
        shape = shape,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .focusable(true)
            .focusProperties { right = focusRight }
            .border(1.dp, borderBrush, shape)
            .drawWithContent {
                drawContent()
                val grad = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 1f to Color.Transparent)
                drawRect(brush = grad)
            }
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
                com.chris.m3usuite.ui.util.AppAsyncImage(
                    url = url,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(sz)
                        .clip(CircleShape)
                        .border(2.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
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
