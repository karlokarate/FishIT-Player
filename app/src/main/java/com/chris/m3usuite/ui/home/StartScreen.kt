package com.chris.m3usuite.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Button
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
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.components.rows.LiveRow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.animation.core.animateFloat
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import com.chris.m3usuite.ui.theme.DesignTokens
import androidx.lifecycle.viewmodel.compose.viewModel

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
    val db = remember { DbProvider.get(ctx) }
    val store = remember { SettingsStore(ctx) }
    val mediaRepo = remember { MediaQueryRepository(ctx, store) }

    // Kid/Adult flag
    val currentProfileId by store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    var isKid by remember { mutableStateOf(false) }
    LaunchedEffect(currentProfileId) {
        isKid = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val p = if (currentProfileId > 0) db.profileDao().byId(currentProfileId) else null
            p?.type == "kid"
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
        val rawSeries = withContext(kotlinx.coroutines.Dispatchers.IO) { mediaRepo.listByTypeFiltered("series", 2000, 0) }
        val rawMovies = withContext(kotlinx.coroutines.Dispatchers.IO) { mediaRepo.listByTypeFiltered("vod", 2000, 0) }
        val rawTv = withContext(kotlinx.coroutines.Dispatchers.IO) { mediaRepo.listByTypeFiltered("live", 2000, 0) }
        series = sortByYearDesc(rawSeries, { it.year }, { it.name }).distinctBy { it.id }
        movies = sortByYearDesc(rawMovies, { it.year }, { it.name }).distinctBy { it.id }
        tv = filterGermanTv(rawTv, { null }, { null }, { it.categoryName }, { it.name }).distinctBy { it.id }
        // initial data loaded → stop continuous spin
        com.chris.m3usuite.ui.fx.FishSpin.setLoading(false)
    }

    // Favorites for live row on Home
    val favCsv by store.favoriteLiveIdsCsv.collectAsStateWithLifecycle(initialValue = "")
    LaunchedEffect(favCsv, isKid) {
        // Always sanitize favorites: numeric, distinct, existing only
        val ids = favCsv.split(',').mapNotNull { it.toLongOrNull() }.distinct()
        favLive = if (ids.isEmpty()) emptyList() else withContext(kotlinx.coroutines.Dispatchers.IO) {
            val all = mediaRepo.listByTypeFiltered("live", 6000, 0)
            val map = all.associateBy { it.id }
            ids.mapNotNull { map[it] }.distinctBy { it.id }
        }
    }

    val listState = rememberLazyListState()
    HomeChromeScaffold(
        title = "m3uSuite",
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
                tv = filterGermanTv(rawTv, { null }, { null }, { it.categoryName }, { it.name }).distinctBy { it.id }
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
        val Accent = if (isKid) DesignTokens.KidAccent else DesignTokens.Accent
        Box(Modifier.fillMaxSize().padding(pads)) {
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
                        val sFiltered = remember(series, debouncedQuery) {
                            val q = debouncedQuery.trim().lowercase()
                            if (q.isBlank()) series else series.filter {
                                it.name.lowercase().contains(q) || (it.plot ?: "").lowercase().contains(q) || (it.categoryName ?: "").lowercase().contains(q)
                            }
                        }
                        if (sFiltered.isEmpty() && homeQuery.isNotBlank()) {
                            Text(
                                "Keine Treffer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        SeriesRow(
                            items = sFiltered,
                            onOpenDetails = { mi -> openSeries(mi.id) },
                            onPlayDirect = { mi ->
                                scope.launch {
                                    val sid = mi.streamId ?: return@launch
                                    val dbLocal = DbProvider.get(ctx)
                                    val storeLocal = store
                                    val last = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        dbLocal.resumeDao().recentEpisodes(50).firstOrNull { it.seriesStreamId == sid }
                                    }
                                    var ep = if (last != null) withContext(kotlinx.coroutines.Dispatchers.IO) { dbLocal.episodeDao().byEpisodeId(last.episodeId) } else {
                                        val seasons = withContext(kotlinx.coroutines.Dispatchers.IO) { dbLocal.episodeDao().seasons(sid) }
                                        val firstSeason = seasons.firstOrNull()
                                        firstSeason?.let { fs -> withContext(kotlinx.coroutines.Dispatchers.IO) { dbLocal.episodeDao().episodes(sid, fs).firstOrNull() } }
                                    }
                                    if (ep == null) {
                                        // First app start: episodes likely not fetched yet → load once, then retry
                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            runCatching { com.chris.m3usuite.data.repo.XtreamRepository(ctx, storeLocal).loadSeriesInfo(sid) }
                                        }
                                        val seasons = withContext(kotlinx.coroutines.Dispatchers.IO) { dbLocal.episodeDao().seasons(sid) }
                                        val firstSeason = seasons.firstOrNull()
                                        if (firstSeason != null) {
                                            ep = withContext(kotlinx.coroutines.Dispatchers.IO) { dbLocal.episodeDao().episodes(sid, firstSeason).firstOrNull() }
                                        }
                                    }
                                    if (ep != null) {
                                        val cfg = com.chris.m3usuite.core.xtream.XtreamConfig(
                                            host = storeLocal.xtHost.first(),
                                            port = storeLocal.xtPort.first(),
                                            username = storeLocal.xtUser.first(),
                                            password = storeLocal.xtPass.first(),
                                            output = storeLocal.xtOutput.first()
                                        )
                                        val playUrl = cfg.seriesEpisodeUrl(ep.episodeId, ep.containerExt)
                                        val headers = buildMap<String,String> {
                                            val ua = storeLocal.userAgent.first(); val ref = storeLocal.referer.first()
                                            if (ua.isNotBlank()) put("User-Agent", ua); if (ref.isNotBlank()) put("Referer", ref)
                                        }
                                        com.chris.m3usuite.player.PlayerChooser.start(
                                            context = ctx,
                                            store = storeLocal,
                                            url = playUrl,
                                            headers = headers,
                                            startPositionMs = last?.positionSecs?.toLong()?.times(1000)
                                        ) { s ->
                                            val encoded = java.net.URLEncoder.encode(playUrl, java.nio.charset.StandardCharsets.UTF_8.name())
                                            navController.navigate("player?url=$encoded&type=series&episodeId=${ep.episodeId}&startMs=${s ?: -1}")
                                        }
                                    }
                                }
                            },
                            onAssignToKid = { mi ->
                                scope.launch {
                                    val kids = withContext(kotlinx.coroutines.Dispatchers.IO) { DbProvider.get(ctx).profileDao().all().filter { it.type == "kid" } }
                                    // Quick assign to all kids for simplicity; could show sheet later
                                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                        kids.forEach { repo.allow(it.id, "series", mi.id) }
                                    }
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
                        val vFiltered = remember(movies, debouncedQuery) {
                            val q = debouncedQuery.trim().lowercase()
                            if (q.isBlank()) movies else movies.filter {
                                it.name.lowercase().contains(q) || (it.plot ?: "").lowercase().contains(q) || (it.categoryName ?: "").lowercase().contains(q)
                            }
                        }
                        if (vFiltered.isEmpty() && homeQuery.isNotBlank()) {
                            Text(
                                "Keine Treffer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        VodRow(
                            items = vFiltered,
                            onOpenDetails = { mi -> openVod(mi.id) },
                            onPlayDirect = { mi ->
                                scope.launch {
                                    val url = mi.url ?: return@launch
                                    val headers = buildMap<String,String> {
                                        val ua = store.userAgent.first(); val ref = store.referer.first()
                                        if (ua.isNotBlank()) put("User-Agent", ua); if (ref.isNotBlank()) put("Referer", ref)
                                    }
                                    com.chris.m3usuite.player.PlayerChooser.start(
                                        context = ctx,
                                        store = store,
                                        url = url,
                                        headers = headers,
                                        startPositionMs = withContext(kotlinx.coroutines.Dispatchers.IO) { DbProvider.get(ctx).resumeDao().getVod(mi.id)?.positionSecs?.toLong()?.times(1000) }
                                    ) { s ->
                                        val encoded = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8.name())
                                        navController.navigate("player?url=$encoded&type=vod&mediaId=${mi.id}&startMs=${s ?: -1}")
                                    }
                                }
                            },
                            onAssignToKid = { mi ->
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val kids = DbProvider.get(ctx).profileDao().all().filter { it.type == "kid" }
                                    val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                    kids.forEach { repo.allow(it.id, "vod", mi.id) }
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
        var allLive by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
        var query by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf(favCsv.split(',').mapNotNull { it.toLongOrNull() }.toSet()) }
        var provider by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(isKid) {
            withContext(kotlinx.coroutines.Dispatchers.IO) { MediaQueryRepository(ctx, store).listByTypeFiltered("live", 6000, 0) }
                .let { list -> allLive = list }
        }
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showLivePicker = false }) {
            val addReq = remember { FocusRequester() }
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sender auswählen", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Suche (TV)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // Provider-Chips (aus categoryName)
                val providers = remember(allLive) { allLive.mapNotNull { it.categoryName?.trim() }.filter { it.isNotEmpty() }.distinct().sorted() }
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    item { FilterChip(modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha), selected = provider == null, onClick = { provider = null }, label = { Text("Alle") }) }
                    items(providers) { p -> FilterChip(modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha), selected = provider == p, onClick = { provider = if (provider == p) null else p }, label = { Text(p) }) }
                }
                val filtered = remember(allLive, query, provider) {
                    val q = query.trim().lowercase()
                    allLive.filter { item ->
                        val matchQ = if (q.isBlank()) true else item.name.lowercase().contains(q) || (item.categoryName ?: "").lowercase().contains(q)
                        val matchP = provider?.let { p -> (item.categoryName ?: "").contains(p, ignoreCase = true) } ?: true
                        matchQ && matchP
                    }.distinctBy { it.id }
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 180.dp), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { mi ->
                        val isSel = mi.id in selected
                        ChannelPickTile(item = mi, selected = isSel, onToggle = { selected = if (isSel) selected - mi.id else selected + mi.id }, focusRight = addReq)
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
                coil3.compose.AsyncImage(
                    model = url,
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
