package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.Image
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import coil3.compose.AsyncImage
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.MediaItem
import com.chris.m3usuite.data.repo.MediaQueryRepository
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.common.FocusableCard
import com.chris.m3usuite.ui.common.isTv
import kotlinx.coroutines.launch
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberImageHeaders
import com.chris.m3usuite.ui.components.ResumeSectionAuto
import com.chris.m3usuite.ui.components.CollapsibleHeader
// removed grid/list per new design
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.chris.m3usuite.data.db.Profile
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.chris.m3usuite.ui.components.rows.ResumeRow
import com.chris.m3usuite.ui.components.rows.LiveRow
import com.chris.m3usuite.ui.components.rows.SeriesRow
import com.chris.m3usuite.ui.components.rows.VodRow
import com.chris.m3usuite.ui.home.header.FishITHeaderHeights
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import androidx.compose.foundation.lazy.rememberLazyListState
import com.chris.m3usuite.ui.home.header.FishITBottomPanel
import com.chris.m3usuite.ui.home.header.FishITBottomHeights
import com.chris.m3usuite.data.db.MediaItem as DbMediaItem
import kotlinx.serialization.json.Json
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.skin.focusScaleOnTv
// removed duplicate imports
import com.chris.m3usuite.ui.theme.DesignTokens
import androidx.compose.animation.core.animateFloat
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import android.os.SystemClock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    navController: NavHostController,
    openLive: (Long) -> Unit,
    openVod: (Long) -> Unit,
    openSeries: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { DbProvider.get(ctx) }
    val store = remember { SettingsStore(ctx) }
    val mediaRepo = remember { MediaQueryRepository(ctx, store) }
    val kidRepo = remember { KidContentRepository(ctx) }
    val permRepo = remember { com.chris.m3usuite.data.repo.PermissionRepository(ctx, store) }
    val scope = rememberCoroutineScope()
    val tv = isTv(ctx)
    val focus = LocalFocusManager.current
    val headers = rememberImageHeaders()
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by store.hapticsEnabled.collectAsState(initial = false)

    val storedTab by store.libraryTabIndex.collectAsStateWithLifecycle(initialValue = 0)
    var tab by rememberSaveable { mutableIntStateOf(storedTab) }
    LaunchedEffect(storedTab) { if (tab != storedTab) tab = storedTab }
    val tabs = listOf("Live", "VOD", "Series", "Alle")

    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var categories by remember { mutableStateOf<List<String?>>(emptyList()) }
    var canSeeResume by remember { mutableStateOf(true) }
    var canSearch by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val p = permRepo.current()
        canSeeResume = p.canSeeResume
        canSearch = p.canSearch
    }

    // Filter-/Such-Sheet
    var showFilters by rememberSaveable { mutableStateOf(false) }
    // Kategorien-Sheet (vollständige Liste)
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }

    // LiveTV – optionale Unterfilter (Phase 11): Sprache/Provider/Genre/Kids
    val liveProviders = listOf("NF" to listOf("nf", "netflix"), "AMZ" to listOf("amz", "amazon"), "PRMT" to listOf("p ", " p ", "param", "prmt", "paramount"), "DISNEY" to listOf("disney", "d+") )
    val liveGenres = listOf("Sport", "News", "Doku", "Film", "Serie", "Musik", "Kinder")
    var filterGerman by rememberSaveable { mutableStateOf(false) }
    var filterKids by rememberSaveable { mutableStateOf(false) }
    var filterProviders by rememberSaveable { mutableStateOf(setOf<String>()) } // keys from liveProviders labels
    var filterGenres by rememberSaveable { mutableStateOf(setOf<String>()) }
    // Load persisted filter state
    val persistedGerman by store.liveFilterGerman.collectAsStateWithLifecycle(initialValue = false)
    val persistedKids by store.liveFilterKids.collectAsStateWithLifecycle(initialValue = false)
    val persistedProviders by store.liveFilterProvidersCsv.collectAsStateWithLifecycle(initialValue = "")
    val persistedGenres by store.liveFilterGenresCsv.collectAsStateWithLifecycle(initialValue = "")
    LaunchedEffect(persistedGerman, persistedKids, persistedProviders, persistedGenres) {
        filterGerman = persistedGerman
        filterKids = persistedKids
        filterProviders = persistedProviders.split(',').filter { it.isNotBlank() }.toSet()
        filterGenres = persistedGenres.split(',').filter { it.isNotBlank() }.toSet()
    }

    // Live categories collapse/expand state (reactive at top-level, not inside LazyListScope)
    val liveCollapsedCsv by store.liveCatCollapsedCsv.collectAsStateWithLifecycle(initialValue = "")
    val liveExpandedOrderCsv by store.liveCatExpandedOrderCsv.collectAsStateWithLifecycle(initialValue = "")

    // VOD/Series categories collapse/expand state
    val vodCatCollapsedCsv by store.vodCatCollapsedCsv.collectAsStateWithLifecycle(initialValue = "")
    val vodCatExpandedOrderCsv by store.vodCatExpandedOrderCsv.collectAsStateWithLifecycle(initialValue = "")
    val seriesCatCollapsedCsv by store.seriesCatCollapsedCsv.collectAsStateWithLifecycle(initialValue = "")
    val seriesCatExpandedOrderCsv by store.seriesCatExpandedOrderCsv.collectAsStateWithLifecycle(initialValue = "")

    // Collapsible-State für Header (global gespeichert)
    val collapsed by store.headerCollapsed.collectAsStateWithLifecycle(initialValue = false)

    fun isGerman(mi: MediaItem): Boolean {
        val s = (mi.name + " " + (mi.categoryName ?: "")).lowercase()
        return Regex("\\bde\\b|\\bger\\b|deutsch|german").containsMatchIn(s)
    }
    fun isKids(mi: MediaItem): Boolean {
        val s = (mi.name + " " + (mi.categoryName ?: "")).lowercase()
        return Regex("kid|kinder|children|cartoon|toon").containsMatchIn(s)
    }
    fun hasProvider(mi: MediaItem, key: String): Boolean {
        val tokens = liveProviders.find { it.first == key }?.second ?: return false
        val s = mi.name.lowercase()
        return tokens.any { t -> s.contains(t) }
    }
    fun hasGenre(mi: MediaItem, g: String): Boolean {
        val s = (mi.name + " " + (mi.categoryName ?: "")).lowercase()
        return when (g) {
            "Sport" -> s.contains("sport")
            "News" -> s.contains("news") || s.contains("nachricht")
            "Doku" -> s.contains("doku") || s.contains("docu")
            "Film" -> s.contains("film") || s.contains("movie")
            "Serie" -> s.contains("serie") || s.contains("series")
            "Musik" -> s.contains("musik") || s.contains("music")
            "Kinder" -> isKids(mi)
            else -> false
        }
    }

    fun applyLiveFilters(list: List<MediaItem>): List<MediaItem> {
        var r = list
        if (filterGerman) r = r.filter { isGerman(it) }
        if (filterKids) r = r.filter { isKids(it) }
        if (filterProviders.isNotEmpty()) r = r.filter { mi -> filterProviders.any { hasProvider(mi, it) } }
        if (filterGenres.isNotEmpty()) r = r.filter { mi -> filterGenres.any { hasGenre(mi, it) } }
        return r
    }

    fun load() = scope.launch {
        val type = when (tab) { 0 -> "live"; 1 -> "vod"; 2 -> "series"; else -> null }
        val list = when {
            type == null -> if (searchQuery.text.isBlank()) {
                mediaRepo.listByTypeFiltered("live", 4000, 0) +
                mediaRepo.listByTypeFiltered("vod", 4000, 0) +
                mediaRepo.listByTypeFiltered("series", 4000, 0)
            } else mediaRepo.globalSearchFiltered(searchQuery.text, 6000, 0)
            selectedCategory != null -> mediaRepo.byTypeAndCategoryFiltered(type, selectedCategory)
            searchQuery.text.isNotBlank() -> mediaRepo.globalSearchFiltered(searchQuery.text, 6000, 0).filter { it.type == type }
            else -> mediaRepo.listByTypeFiltered(type, 6000, 0)
        }
        mediaItems = if (type == "live") applyLiveFilters(list) else list
        categories = if (type != null) mediaRepo.categoriesByTypeFiltered(type) else emptyList()
    }

    // Debounce tippen → ruckelfreie Live-Suche
    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery.text }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest { load() }
    }

    // Paging source for large lists (type/category or search)
    val currentType = when (tab) { 0 -> "live"; 1 -> "vod"; 2 -> "series"; else -> null }
    val pagingFlow = remember(currentType, selectedCategory, searchQuery.text) {
        when {
            searchQuery.text.isNotBlank() -> mediaRepo.pagingSearchFilteredFlow(searchQuery.text)
            currentType != null -> mediaRepo.pagingByTypeFilteredFlow(currentType, selectedCategory)
            else -> null
        }
    }
    val pagingItems = pagingFlow?.collectAsLazyPagingItems()
    LaunchedEffect(Unit) { com.chris.m3usuite.metrics.RouteTag.set("library") }
    // Time to first viewport for paged content
    val ttfvStart = remember { SystemClock.uptimeMillis() }
    LaunchedEffect(pagingItems?.loadState?.refresh, pagingItems?.itemCount) {
        val items = pagingItems ?: return@LaunchedEffect
        val ls = items.loadState.refresh
        if (ls is androidx.paging.LoadState.NotLoading && items.itemCount > 0) {
            val ms = SystemClock.uptimeMillis() - ttfvStart
            android.util.Log.d("Perf", "TTFV library: ${ms} ms (items=${items.itemCount})")
        }
    }

    fun submitSearch() { load(); focus.clearFocus() }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(tab, selectedCategory) { load() }

    // Adult-only: Settings sichtbar, Kids versteckt; Selection-Mode für Freigaben (nur Adult)
    val profileId by store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    var showSettings by remember { mutableStateOf(true) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedSaver = androidx.compose.runtime.saveable.Saver<Set<Pair<Long, String>>, List<String>>(
        save = { set -> set.map { "${it.first}:${it.second}" } },
        restore = { list -> list.map { s -> val p = s.split(':', limit = 2); p[0].toLong() to p[1] }.toSet() }
    )
    var selected by rememberSaveable(stateSaver = selectedSaver) { mutableStateOf(setOf<Pair<Long, String>>()) }
    var showGrantSheet by rememberSaveable { mutableStateOf(false) }
    var showRevokeSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(profileId) {
        val prof = db.profileDao().byId(profileId)
        showSettings = prof?.type != "kid"
        if (!showSettings) { selectionMode = false; selected = emptySet() }
    }

    val rotationLocked by store.rotationLocked.collectAsState(initial = false)

    val snackHost = remember { SnackbarHostState() }
    val headerListState = rememberLazyListState()
    HomeChromeScaffold(
        title = "FishIT Player",
        onSettings = {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != "settings") {
                navController.navigate("settings") { launchSingleTop = true }
            }
        },
        onSearch = { showFilters = true },
        onProfiles = {
            scope.launch {
                store.setCurrentProfileId(-1)
                navController.navigate("gate") {
                    popUpTo("library") { inclusive = true }
                    launchSingleTop = true
                }
            }
        },
        onRefresh = { scope.launch { load() } },
        listState = headerListState,
        onLogo = {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != "library") {
                navController.navigate("library") { launchSingleTop = true }
            }
        },
        bottomBar = {
            FishITBottomPanel(
                selected = when (tab) { 0 -> "live"; 1 -> "vod"; 2 -> "series"; else -> "all" },
                onSelect = { id ->
                    val i = when (id) { "live" -> 0; "vod" -> 1; "series" -> 2; else -> 3 }
                    tab = i
                    scope.launch { store.setLibraryTabIndex(i) }
                    selectedCategory = null
                    searchQuery = TextFieldValue("")
                    load()
                }
            )
        }
    ) { pads ->
        Box(Modifier.fillMaxSize().padding(pads)) {
            val isKidProfile = !showSettings
            val Accent = if (isKidProfile) DesignTokens.KidAccent else DesignTokens.Accent
            // Background layers
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
                            colors = listOf(Accent.copy(alpha = 0.12f), Color.Transparent),
                            radius = with(LocalDensity.current) { 720.dp.toPx() }
                        )
                    )
            )
            com.chris.m3usuite.ui.fx.FishBackground(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(580.dp),
                alpha = 0.05f
            )
            Column(
                Modifier
                    .fillMaxSize()
            ) {
            // Top rails (Resume, Live, Series, VOD) – kid-friendly, no headers
            var topResume by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }
            var topLive by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }
            var topSeries by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }
            var topVod by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }

            // Determine kid profile from existing flag
            val isKidProfile = !showSettings

            // Grouped source lists for rows
            var allLive by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }
            var allVod by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }
            var allSeries by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }
            // Precomputed list of series with new episodes since last snapshot
            var newSeriesFromEpisodes by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }

            LaunchedEffect(isKidProfile) {
                // Resume: merge recent VOD and Series episodes by updatedAt, map to MediaItem, limit 5
                runCatching {
                    val rDao = db.resumeDao()
                    val vod = withContext(Dispatchers.IO) { rDao.recentVod(5) }
                    val eps = withContext(Dispatchers.IO) { rDao.recentEpisodes(5) }
                    val merged = (vod.map { it.updatedAt to it } + eps.map { it.updatedAt to it })
                        .sortedByDescending { it.first }
                    val out = mutableListOf<DbMediaItem>()
                    withContext(Dispatchers.IO) {
                        val mDao = db.mediaDao()
                        for ((_, any) in merged) {
                            if (out.size >= 5) break
                            when (any) {
                                is com.chris.m3usuite.data.db.ResumeVodView -> {
                                    val mi = mDao.byId(any.mediaId)
                                    if (mi != null && out.none { it.id == mi.id }) out += mi
                                }
                                is com.chris.m3usuite.data.db.ResumeEpisodeView -> {
                                    val mi = mDao.seriesByStreamId(any.seriesStreamId)
                                    if (mi != null && out.none { it.id == mi.id }) out += mi
                                }
                            }
                        }
                    }
                    // Filter resume items by effective allow-set for non-adult profiles
                    val prof = withContext(Dispatchers.IO) { DbProvider.get(ctx).profileDao().byId(store.currentProfileId.first()) }
                    topResume = if (prof?.type == "adult") out else withContext(Dispatchers.IO) {
                        out.filter { mi ->
                            com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, store).isAllowed(mi.type, mi.id)
                        }
                    }
                }
                // Live/Series/VOD: lightweight lists (already filtered for kids by MediaQueryRepository)
                runCatching { allLive = mediaRepo.listByTypeFiltered("live", 4000, 0).distinctBy { it.id } }
                runCatching { allSeries = mediaRepo.listByTypeFiltered("series", 4000, 0).distinctBy { it.id } }
                runCatching {
                    val filtered = mediaRepo.listByTypeFiltered("vod", 4000, 0).distinctBy { it.id }
                    allVod = if (filtered.isNotEmpty()) filtered else db.mediaDao().listByType("vod", 4000, 0).distinctBy { it.id }
                }
                // top rows remain a small snapshot
                topLive = allLive.take(50)
                topSeries = allSeries.take(50)
                topVod = allVod.take(50)
            }

            // Compute “Neue Episoden” series whenever allSeries changes
            LaunchedEffect(allSeries) {
                if (allSeries.isEmpty()) {
                    newSeriesFromEpisodes = emptyList()
                    return@LaunchedEffect
                }
                runCatching {
                    val prevCsv = store.episodeSnapshotIdsCsv.first()
                    val prev = prevCsv.split(',').filter { it.isNotBlank() }.toSet()
                    val (currentList, newSeriesSet) = withContext(Dispatchers.IO) {
                        val dao = db.episodeDao()
                        val currentIds = mutableSetOf<String>()
                        val newSeries = mutableSetOf<Int>()
                        val sids = allSeries.mapNotNull { it.streamId }.toSet()
                        for (sid in sids) {
                            val seasons = dao.seasons(sid)
                            var markNew = false
                            for (season in seasons) {
                                for (e in dao.episodes(sid, season)) {
                                    val idStr = e.episodeId.toString()
                                    currentIds += idStr
                                    if (!prev.contains(idStr)) markNew = true
                                }
                                if (markNew) break
                            }
                            if (markNew) newSeries += sid
                        }
                        currentIds.toList() to newSeries.toSet()
                    }
                    newSeriesFromEpisodes = allSeries.filter { it.streamId in newSeriesSet }.distinctBy { it.streamId }.take(50)
                    store.setEpisodeSnapshotIdsCsv(currentList.joinToString(","))
                }.onFailure { newSeriesFromEpisodes = emptyList() }
            }

            val railPad = if (isKidProfile) 20.dp else 16.dp
            // Rails inside a LazyColumn that occupies available height, driving header scrim alpha
            LazyColumn(
                state = headerListState,
                contentPadding = PaddingValues(vertical = 0.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                // Build rows grouped by current selection
                val selectedId = when (tab) { 0 -> "live"; 1 -> "vod"; 2 -> "series"; else -> "all" }
                // Helper mini header (match StartScreen optics)
                fun header(label: String) {
                    item("hdr_$label") {
                        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp))
                    }
                }
                fun parseAddedAt(mi: DbMediaItem): Long? = runCatching {
                    val json = mi.extraJson ?: return@runCatching null
                    val map = kotlinx.serialization.json.Json.decodeFromString<Map<String,String>>(json)
                    map["addedAt"]?.toLongOrNull()
                }.getOrNull()
                val now = System.currentTimeMillis()
                val thresholdMs = 7L * 24L * 60L * 60L * 1000L // 7 Tage
                fun isNew(mi: DbMediaItem): Boolean = parseAddedAt(mi)?.let { now - it <= thresholdMs } == true
                when (selectedId) {
                    "vod" -> {
                        if (topResume.isNotEmpty()) {
                            header("Zuletzt angeschaut")
                            item("vod_resume") {
                                Box(Modifier.padding(horizontal = (railPad - 16.dp))) {
                                    val onlyVod = topResume.filter { it.type == "vod" }.take(50)
                                    ResumeRow(items = onlyVod) { mi -> openVod(mi.id) }
                                }
                            }
                        }
                        // Neu hinzugefügt – bevorzugt über addedAt, Fallback: lokale ID desc
                        if (allVod.isNotEmpty()) {
                            val byAdded = allVod.filter { isNew(it) }.sortedByDescending { parseAddedAt(it) ?: 0L }
                            val newest = (if (byAdded.isNotEmpty()) byAdded else allVod.sortedByDescending { it.id }).take(50)
                            header("Neu hinzugefügt")
                            item("vod_new") { Box(Modifier.padding(horizontal = (railPad - 16.dp))) {
                                VodRow(
                                    items = newest,
                                    onOpenDetails = { mi -> openVod(mi.id) },
                                    onPlayDirect = { mi ->
                                        scope.launch {
                                            val url = mi.url ?: return@launch
                                            val headers = buildMap<String,String> {
                                                val ua = store.userAgent.first(); val ref = store.referer.first()
                                                if (ua.isNotBlank()) put("User-Agent", ua); if (ref.isNotBlank()) put("Referer", ref)
                                            }
                                            com.chris.m3usuite.player.PlayerChooser.start(context = ctx, store = store, url = url, headers = headers, startPositionMs = withContext(Dispatchers.IO) { db.resumeDao().getVod(mi.id)?.positionSecs?.toLong()?.times(1000) }) { s -> com.chris.m3usuite.player.ExternalPlayer.open(context = ctx, url = url, startPositionMs = s) }
                                        }
                                    },
                                    onAssignToKid = { mi ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                val kids = db.profileDao().all().filter { it.type == "kid" }
                                                val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                                kids.forEach { repo.allow(it.id, "vod", mi.id) }
                                            }
                                            android.widget.Toast.makeText(ctx, "Für Kinder freigegeben", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    showNew = byAdded.isNotEmpty()
                                )
                            } }
                        }
                        // Kategorien (collapsible rows), wie Live
                        run {
                            fun keyOf(cat: String?): String = (cat ?: "").trim()
                            fun labelOf(key: String): String = if (key.isEmpty()) "Unbekannt" else key
                            val allCats = allVod.map { keyOf(it.categoryName) }.distinct().sortedBy { it.lowercase() }

                            val collapsedCsv = vodCatCollapsedCsv
                            val expandedOrderCsv = vodCatExpandedOrderCsv
                            val collapsedSet = collapsedCsv.split(',').filter { it.isNotEmpty() }.toMutableSet()
                            val expandedCats = allCats.filterNot { it in collapsedSet }
                            val collapsedCats = allCats.filter { it in collapsedSet }
                            val expandedOrdered = run {
                                val head = expandedOrderCsv.split(',').filter { it.isNotEmpty() && it in expandedCats }
                                val tail = expandedCats.filterNot { it in head }.sortedBy { it.lowercase() }
                                head + tail
                            }

                            // Controls row
                            if (allCats.isNotEmpty()) {
                                header("VOD-Kategorien")
                                item("vod_controls") {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("VOD-Kategorien", style = MaterialTheme.typography.titleMedium)
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    store.setVodCatCollapsedCsv(allCats.joinToString(","))
                                                    store.setVodCatExpandedOrderCsv("")
                                                }
                                            },
                                            enabled = expandedCats.isNotEmpty()
                                        ) { Text("Alle einklappen") }
                                    }
                                }
                            }

                            // Expanded categories as rows
                            expandedOrdered.forEach { catKey ->
                                val list = allVod.filter { keyOf(it.categoryName) == catKey }.distinctBy { it.id }
                                if (list.isNotEmpty()) {
                                    val catLabel = labelOf(catKey)
                                    item("vod_cat_chip_$catKey") {
                                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 2.dp)) {
                                            FilterChip(
                                                modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha),
                                                selected = false,
                                                onClick = {
                                                    scope.launch {
                                                        val newCollapsed = (collapsedSet + catKey).joinToString(",")
                                                        val newOrder = expandedOrderCsv.split(',').filterNot { it == catKey }.joinToString(",")
                                                        store.setVodCatCollapsedCsv(newCollapsed)
                                                        store.setVodCatExpandedOrderCsv(newOrder)
                                                    }
                                                },
                                                label = { Text(catLabel) }
                                            )
                                        }
                                    }
                                    item("vod_cat_row_$catKey") {
                                        Box(Modifier.padding(horizontal = (railPad - 16.dp))) {
                                            VodRow(
                                                items = list,
                                                onOpenDetails = { mi -> openVod(mi.id) },
                                                onPlayDirect = { mi ->
                                                    scope.launch {
                                                        val url = mi.url ?: return@launch
                                                        val headers = buildMap<String,String> { val ua = store.userAgent.first(); val ref = store.referer.first(); if (ua.isNotBlank()) put("User-Agent", ua); if (ref.isNotBlank()) put("Referer", ref) }
                                                        com.chris.m3usuite.player.PlayerChooser.start(context = ctx, store = store, url = url, headers = headers, startPositionMs = withContext(Dispatchers.IO){ db.resumeDao().getVod(mi.id)?.positionSecs?.toLong()?.times(1000) }) { s -> com.chris.m3usuite.player.ExternalPlayer.open(context = ctx, url = url, startPositionMs = s) }
                                                    }
                                                },
                                                onAssignToKid = { mi ->
                                                    scope.launch(Dispatchers.IO) {
                                                        val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                                        db.profileDao().all().filter { it.type == "kid" }.forEach { repo.allow(it.id, "vod", mi.id) }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Collapsed chips
                            if (collapsedCats.isNotEmpty()) {
                                item("vod_collapsed_header") { Spacer(Modifier.height(8.dp)) }
                                collapsedCats.sortedBy { it.lowercase() }.forEach { catKey ->
                                    val catLabel = labelOf(catKey)
                                    item("vod_collapsed_$catKey") {
                                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
                                            FilterChip(
                                                modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha),
                                                selected = true,
                                                onClick = {
                                                    scope.launch {
                                                        val newCollapsed = (collapsedSet - catKey).joinToString(",")
                                                        val current = expandedOrderCsv.split(',').filter { it.isNotEmpty() }.toMutableList().apply { removeAll { it == catKey } }
                                                        current.add(0, catKey)
                                                        store.setVodCatCollapsedCsv(newCollapsed)
                                                        store.setVodCatExpandedOrderCsv(current.joinToString(","))
                                                    }
                                                },
                                                label = { Text(catLabel) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "series" -> {
                        // Zuletzt geschaut (unique per Serie)
                        if (topResume.isNotEmpty()) {
                            header("Zuletzt geschaut")
                            val seriesUnique = topResume.filter { it.type == "series" }.distinctBy { it.streamId ?: it.id }.take(50)
                            item("series_resume") { Box(Modifier.padding(horizontal = (railPad - 16.dp))) { SeriesRow(items = seriesUnique, onOpenDetails = { mi -> openSeries(mi.id) }, onPlayDirect = { mi ->
                                scope.launch {
                                    val sid = mi.streamId ?: return@launch
                                    val last = withContext(Dispatchers.IO) { db.resumeDao().recentEpisodes(50).firstOrNull { it.seriesStreamId == sid } }
                                    val ep = if (last != null) withContext(Dispatchers.IO) { db.episodeDao().byEpisodeId(last.episodeId) } else {
                                        val seasons = withContext(Dispatchers.IO) { db.episodeDao().seasons(sid) }
                                        val fs = seasons.firstOrNull(); fs?.let { withContext(Dispatchers.IO) { db.episodeDao().episodes(sid, it).firstOrNull() } }
                                    }
                                    if (ep != null) {
                                        val cfg = com.chris.m3usuite.core.xtream.XtreamConfig(store.xtHost.first(), store.xtPort.first(), store.xtUser.first(), store.xtPass.first(), store.xtOutput.first())
                                        val playUrl = cfg.seriesEpisodeUrl(ep.episodeId, ep.containerExt)
                                        val headers = buildMap<String,String> { val ua = store.userAgent.first(); val ref = store.referer.first(); if (ua.isNotBlank()) put("User-Agent", ua); if (ref.isNotBlank()) put("Referer", ref) }
                                        com.chris.m3usuite.player.PlayerChooser.start(context = ctx, store = store, url = playUrl, headers = headers, startPositionMs = last?.positionSecs?.toLong()?.times(1000)) { s ->
                                            val encoded = java.net.URLEncoder.encode(playUrl, java.nio.charset.StandardCharsets.UTF_8.name())
                                            navController.navigate("player?url=$encoded&type=series&episodeId=${ep.episodeId}&startMs=${s ?: -1}")
                                        }
                                    }
                                }
                            }, onAssignToKid = { mi -> scope.launch(Dispatchers.IO) { val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx); db.profileDao().all().filter { it.type=="kid" }.forEach { repo.allow(it.id, "series", mi.id) } } }) } }
                        }
                        if (allSeries.isNotEmpty()) {
                            header("Neu hinzugefügt")
                            val newest = allSeries.filter { isNew(it) }.sortedByDescending { parseAddedAt(it) ?: 0L }.take(50)
                            item("series_new") { Box(Modifier.padding(horizontal = (railPad - 16.dp))) { SeriesRow(items = newest, onOpenDetails = { mi -> openSeries(mi.id) }, onPlayDirect = { mi -> /* see above pattern */
                                scope.launch {
                                    val sid = mi.streamId ?: return@launch
                                    val last = withContext(Dispatchers.IO) { db.resumeDao().recentEpisodes(50).firstOrNull { it.seriesStreamId == sid } }
                                    val ep = if (last != null) withContext(Dispatchers.IO) { db.episodeDao().byEpisodeId(last.episodeId) } else {
                                        val seasons = withContext(Dispatchers.IO) { db.episodeDao().seasons(sid) }
                                        val fs = seasons.firstOrNull(); fs?.let { withContext(Dispatchers.IO) { db.episodeDao().episodes(sid, it).firstOrNull() } }
                                    }
                                    if (ep != null) {
                                        val cfg = com.chris.m3usuite.core.xtream.XtreamConfig(store.xtHost.first(), store.xtPort.first(), store.xtUser.first(), store.xtPass.first(), store.xtOutput.first())
                                        val playUrl = cfg.seriesEpisodeUrl(ep.episodeId, ep.containerExt)
                                        val headers = buildMap<String,String> { val ua = store.userAgent.first(); val ref = store.referer.first(); if (ua.isNotBlank()) put("User-Agent", ua); if (ref.isNotBlank()) put("Referer", ref) }
                                        com.chris.m3usuite.player.PlayerChooser.start(context = ctx, store = store, url = playUrl, headers = headers, startPositionMs = last?.positionSecs?.toLong()?.times(1000)) { s ->
                                            val encoded = java.net.URLEncoder.encode(playUrl, java.nio.charset.StandardCharsets.UTF_8.name())
                                            navController.navigate("player?url=$encoded&type=series&episodeId=${ep.episodeId}&startMs=${s ?: -1}")
                                        }
                                    }
                                }
                            }, onAssignToKid = { mi -> scope.launch(Dispatchers.IO) { val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx); db.profileDao().all().filter { it.type=="kid" }.forEach { repo.allow(it.id, "series", mi.id) } } }, showNew = true) } }
                        }
                        // Neue Episoden seit letztem Start (Snapshot-Vergleich)
                        if (newSeriesFromEpisodes.isNotEmpty()) {
                            header("Neue Episoden")
                            item("series_new_episodes") { Box(Modifier.padding(horizontal = (railPad - 16.dp))) { SeriesRow(items = newSeriesFromEpisodes, onOpenDetails = { mi -> openSeries(mi.id) }, onPlayDirect = { mi -> /* same as above */
                                scope.launch {
                                    val sid = mi.streamId ?: return@launch
                                    val last = withContext(Dispatchers.IO) { db.resumeDao().recentEpisodes(50).firstOrNull { it.seriesStreamId == sid } }
                                    val ep = if (last != null) withContext(Dispatchers.IO) { db.episodeDao().byEpisodeId(last.episodeId) } else {
                                        val seasons = withContext(Dispatchers.IO) { db.episodeDao().seasons(sid) }
                                        val fs = seasons.firstOrNull(); fs?.let { withContext(Dispatchers.IO) { db.episodeDao().episodes(sid, it).firstOrNull() } }
                                    }
                                    if (ep != null) {
                                        val cfg = com.chris.m3usuite.core.xtream.XtreamConfig(store.xtHost.first(), store.xtPort.first(), store.xtUser.first(), store.xtPass.first(), store.xtOutput.first())
                                        val playUrl = cfg.seriesEpisodeUrl(ep.episodeId, ep.containerExt)
                                        val headers = buildMap<String,String> { val ua = store.userAgent.first(); val ref = store.referer.first(); if (ua.isNotBlank()) put("User-Agent", ua); if (ref.isNotBlank()) put("Referer", ref) }
                                        com.chris.m3usuite.player.PlayerChooser.start(context = ctx, store = store, url = playUrl, headers = headers, startPositionMs = last?.positionSecs?.toLong()?.times(1000)) { s ->
                                            val encoded = java.net.URLEncoder.encode(playUrl, java.nio.charset.StandardCharsets.UTF_8.name())
                                            navController.navigate("player?url=$encoded&type=series&episodeId=${ep.episodeId}&startMs=${s ?: -1}")
                                        }
                                    }
                                }
                            }, onAssignToKid = { mi -> scope.launch(Dispatchers.IO) { val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx); db.profileDao().all().filter { it.type=="kid" }.forEach { repo.allow(it.id, "series", mi.id) } } }, showNew = true) } }
                        }
                        // Kategorien (collapsible rows), wie Live
                        run {
                            fun keyOf(cat: String?): String = (cat ?: "").trim()
                            fun labelOf(key: String): String = if (key.isEmpty()) "Unbekannt" else key
                            val allCats = allSeries.map { keyOf(it.categoryName) }.distinct().sortedBy { it.lowercase() }

                            val collapsedCsv = seriesCatCollapsedCsv
                            val expandedOrderCsv = seriesCatExpandedOrderCsv
                            val collapsedSet = collapsedCsv.split(',').filter { it.isNotEmpty() }.toMutableSet()
                            val expandedCats = allCats.filterNot { it in collapsedSet }
                            val collapsedCats = allCats.filter { it in collapsedSet }
                            val expandedOrdered = run {
                                val head = expandedOrderCsv.split(',').filter { it.isNotEmpty() && it in expandedCats }
                                val tail = expandedCats.filterNot { it in head }.sortedBy { it.lowercase() }
                                head + tail
                            }

                            if (allCats.isNotEmpty()) {
                                header("Serien-Kategorien")
                                item("series_controls") {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Serien-Kategorien", style = MaterialTheme.typography.titleMedium)
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    store.setSeriesCatCollapsedCsv(allCats.joinToString(","))
                                                    store.setSeriesCatExpandedOrderCsv("")
                                                }
                                            },
                                            enabled = expandedCats.isNotEmpty()
                                        ) { Text("Alle einklappen") }
                                    }
                                }
                            }

                            // Expanded categories as rows
                            expandedOrdered.forEach { catKey ->
                                val list = allSeries.filter { keyOf(it.categoryName) == catKey }.distinctBy { it.id }
                                if (list.isNotEmpty()) {
                                    val catLabel = labelOf(catKey)
                                    item("series_cat_chip_$catKey") {
                                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 2.dp)) {
                                            FilterChip(
                                                modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha),
                                                selected = false,
                                                onClick = {
                                                    scope.launch {
                                                        val newCollapsed = (collapsedSet + catKey).joinToString(",")
                                                        val newOrder = expandedOrderCsv.split(',').filterNot { it == catKey }.joinToString(",")
                                                        store.setSeriesCatCollapsedCsv(newCollapsed)
                                                        store.setSeriesCatExpandedOrderCsv(newOrder)
                                                    }
                                                },
                                                label = { Text(catLabel) }
                                            )
                                        }
                                    }
                                    item("series_cat_row_$catKey") {
                                        Box(Modifier.padding(horizontal = (railPad - 16.dp))) {
                                            SeriesRow(
                                                items = list,
                                                onOpenDetails = { mi -> openSeries(mi.id) },
                                                onPlayDirect = { mi ->
                                                    scope.launch {
                                                        val sid = mi.streamId ?: return@launch
                                                        val last = withContext(Dispatchers.IO) { db.resumeDao().recentEpisodes(50).firstOrNull { it.seriesStreamId == sid } }
                                                        val ep = if (last != null) withContext(Dispatchers.IO) { db.episodeDao().byEpisodeId(last.episodeId) } else {
                                                            val seasons = withContext(Dispatchers.IO) { db.episodeDao().seasons(sid) }
                                                            val fs = seasons.firstOrNull(); fs?.let { withContext(Dispatchers.IO) { db.episodeDao().episodes(sid, it).firstOrNull() } }
                                                        }
                                                        if (ep != null) {
                                                            val cfg = com.chris.m3usuite.core.xtream.XtreamConfig(store.xtHost.first(), store.xtPort.first(), store.xtUser.first(), store.xtPass.first(), store.xtOutput.first())
                                                            val playUrl = cfg.seriesEpisodeUrl(ep.episodeId, ep.containerExt)
                                                            val headers = buildMap<String,String> { val ua = store.userAgent.first(); val ref = store.referer.first(); if (ua.isNotBlank()) put("User-Agent", ua); if (ref.isNotBlank()) put("Referer", ref) }
                                                            com.chris.m3usuite.player.PlayerChooser.start(context = ctx, store = store, url = playUrl, headers = headers, startPositionMs = last?.positionSecs?.toLong()?.times(1000)) { s ->
                                                                val encoded = java.net.URLEncoder.encode(playUrl, java.nio.charset.StandardCharsets.UTF_8.name())
                                                                navController.navigate("player?url=$encoded&type=series&episodeId=${ep.episodeId}&startMs=${s ?: -1}")
                                                            }
                                                        }
                                                    }
                                                },
                                                onAssignToKid = { mi ->
                                                    scope.launch(Dispatchers.IO) {
                                                        val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                                        db.profileDao().all().filter { it.type == "kid" }.forEach { repo.allow(it.id, "series", mi.id) }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Collapsed category chips
                            if (collapsedCats.isNotEmpty()) {
                                item("series_collapsed_header") { Spacer(Modifier.height(8.dp)) }
                                collapsedCats.sortedBy { it.lowercase() }.forEach { catKey ->
                                    val catLabel = labelOf(catKey)
                                    item("series_collapsed_$catKey") {
                                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
                                            FilterChip(
                                                modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha),
                                                selected = true,
                                                onClick = {
                                                    scope.launch {
                                                        val newCollapsed = (collapsedSet - catKey).joinToString(",")
                                                        val current = expandedOrderCsv.split(',').filter { it.isNotEmpty() }.toMutableList().apply { removeAll { it == catKey } }
                                                        current.add(0, catKey)
                                                        store.setSeriesCatCollapsedCsv(newCollapsed)
                                                        store.setSeriesCatExpandedOrderCsv(current.joinToString(","))
                                                    }
                                                },
                                                label = { Text(catLabel) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "live" -> {
                        // Live-TV nach Kategorien (group-title) gruppieren, mit einklappbaren Zeilen + persistenter Ordnung
                        // Category key helper: persist null as empty string
                        fun keyOf(cat: String?): String = cat ?: ""
                        fun labelOf(key: String): String = if (key.isEmpty()) "Unbekannt" else key

                        // All categories present in current list (stable set)
                        val allCats: List<String> = allLive.map { keyOf(it.categoryName?.trim()) }
                            .distinct()
                            .sortedBy { it.lowercase() }

                        // Persisted state (reactive from top-level)
                        val collapsedCsv = liveCollapsedCsv
                        val expandedOrderCsv = liveExpandedOrderCsv
                        val collapsedSet = collapsedCsv.split(',').filter { it.isNotEmpty() }.toMutableSet()
                        val expandedOrder = expandedOrderCsv.split(',').filter { it.isNotEmpty() }

                        // Partition categories
                        val expandedCats = allCats.filterNot { it in collapsedSet }
                        val collapsedCats = allCats.filter { it in collapsedSet }

                        // Expanded display order: recently expanded first, then remaining expanded cats alphabetically
                        val expandedOrdered = run {
                            val head = expandedOrder.filter { it in expandedCats }
                            val tail = expandedCats.filterNot { it in head }.sortedBy { it.lowercase() }
                            head + tail
                        }

                        // Controls row: collapse all
                        item("live_controls") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Live-Kategorien", style = MaterialTheme.typography.titleMedium)
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val allCsv = allCats.joinToString(",")
                                            store.setLiveCatCollapsedCsv(allCsv)
                                            store.setLiveCatExpandedOrderCsv("")
                                        }
                                    },
                                    enabled = expandedCats.isNotEmpty()
                                ) { Text("Alle einklappen") }
                            }
                        }

                        // Render expanded categories as rows (with clickable category chip above each row)
                        expandedOrdered.forEach { catKey ->
                            val catLabel = labelOf(catKey)
                            val list = allLive.filter { keyOf(it.categoryName?.trim()) == catKey }
                                .distinctBy { it.id }
                                .take(200)
                            if (list.isNotEmpty()) {
                                // Category chip above row: click -> collapse and persist
                                item("live_cat_chip_$catKey") {
                                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 2.dp)) {
                                        FilterChip(
                                            modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha),
                                            selected = false,
                                            onClick = {
                                                scope.launch {
                                                    val newCollapsed = (collapsedSet + catKey).joinToString(",")
                                                    // Remove from expanded order (if present)
                                                    val newOrder = expandedOrder.filterNot { it == catKey }.joinToString(",")
                                                    store.setLiveCatCollapsedCsv(newCollapsed)
                                                    store.setLiveCatExpandedOrderCsv(newOrder)
                                                }
                                            },
                                            label = { Text(catLabel) }
                                        )
                                    }
                                }
                                item("live_cat_row_$catKey") {
                                    Box(Modifier.padding(horizontal = (railPad - 16.dp))) {
                                        LiveRow(
                                            items = list,
                                            onOpenDetails = { mi -> openLive(mi.id) },
                                            onPlayDirect = { mi ->
                                                scope.launch {
                                                    val url = mi.url ?: return@launch
                                                    val headers = buildMap<String, String> {
                                                        val ua = store.userAgent.first(); val ref = store.referer.first()
                                                        if (ua.isNotBlank()) put("User-Agent", ua)
                                                        if (ref.isNotBlank()) put("Referer", ref)
                                                    }
                                                    com.chris.m3usuite.player.PlayerChooser.start(context = ctx, store = store, url = url, headers = headers, startPositionMs = null) { startMs ->
                                                        val encoded = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8.name())
                                                        navController.navigate("player?url=$encoded&type=live&mediaId=${mi.id}&startMs=${startMs ?: -1}")
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Collapsed categories area at bottom: show as vertical list of chips to re-expand
                        if (collapsedCats.isNotEmpty()) {
                            item("live_collapsed_header") {
                                Spacer(Modifier.height(8.dp))
                            }
                            collapsedCats.sortedBy { it.lowercase() }.forEach { catKey ->
                                val catLabel = labelOf(catKey)
                                item("live_collapsed_$catKey") {
                                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
                                        FilterChip(
                                            modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha),
                                            selected = true,
                                            onClick = {
                                                // Expand: remove from collapsed set, add to head of expanded order
                                                scope.launch {
                                                    val newCollapsed = (collapsedSet - catKey).joinToString(",")
                                                    val currentOrder = expandedOrder.toMutableList().apply { removeAll { it == catKey } }
                                                    currentOrder.add(0, catKey)
                                                    store.setLiveCatCollapsedCsv(newCollapsed)
                                                    store.setLiveCatExpandedOrderCsv(currentOrder.joinToString(","))
                                                }
                                            },
                                            label = { Text(catLabel) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // All: kurzer Mix wie vorher
                        if (canSeeResume && topResume.isNotEmpty()) { header("Weiter schauen"); item("all_resume") { com.chris.m3usuite.ui.common.AccentCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ResumeRow(items = topResume) { mi -> when (mi.type) { "live" -> openLive(mi.id); "vod" -> openVod(mi.id); else -> openSeries(mi.id) } } } } }
                        if (topLive.isNotEmpty()) { header("TV"); item("all_live") { com.chris.m3usuite.ui.common.AccentCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { LiveRow(items = topLive, onOpenDetails = { mi -> openLive(mi.id) }, onPlayDirect = { mi ->
                                scope.launch {
                                    val url = mi.url ?: return@launch
                                    val headers = buildMap<String, String> {
                                        val ua = store.userAgent.first(); val ref = store.referer.first()
                                        if (ua.isNotBlank()) put("User-Agent", ua)
                                        if (ref.isNotBlank()) put("Referer", ref)
                                    }
                                    com.chris.m3usuite.player.PlayerChooser.start(context = ctx, store = store, url = url, headers = headers, startPositionMs = null) { startMs ->
                                        val encoded = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8.name())
                                        navController.navigate("player?url=$encoded&type=live&mediaId=${mi.id}&startMs=${startMs ?: -1}")
                                    }
                                }
                            } ) } } }
                        if (topSeries.isNotEmpty()) { header("Serien"); item("all_series") { com.chris.m3usuite.ui.common.AccentCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { SeriesRow(items = topSeries, onOpenDetails = { mi -> openSeries(mi.id) }, onPlayDirect = { mi -> /* see pattern */
                            scope.launch {
                                val sid = mi.streamId ?: return@launch
                                val last = withContext(Dispatchers.IO) { db.resumeDao().recentEpisodes(50).firstOrNull { it.seriesStreamId == sid } }
                                val ep = if (last != null) withContext(Dispatchers.IO) { db.episodeDao().byEpisodeId(last.episodeId) } else {
                                    val seasons = withContext(Dispatchers.IO) { db.episodeDao().seasons(sid) }
                                    val fs = seasons.firstOrNull(); fs?.let { withContext(Dispatchers.IO) { db.episodeDao().episodes(sid, it).firstOrNull() } }
                                }
                                if (ep != null) {
                                    val cfg = com.chris.m3usuite.core.xtream.XtreamConfig(store.xtHost.first(), store.xtPort.first(), store.xtUser.first(), store.xtPass.first(), store.xtOutput.first())
                                    val playUrl = cfg.seriesEpisodeUrl(ep.episodeId, ep.containerExt)
                                    val headers = buildMap<String,String> { val ua = store.userAgent.first(); val ref = store.referer.first(); if (ua.isNotBlank()) put("User-Agent", ua); if (ref.isNotBlank()) put("Referer", ref) }
                                    com.chris.m3usuite.player.PlayerChooser.start(context = ctx, store = store, url = playUrl, headers = headers, startPositionMs = last?.positionSecs?.toLong()?.times(1000)) { s ->
                                        val encoded = java.net.URLEncoder.encode(playUrl, java.nio.charset.StandardCharsets.UTF_8.name())
                                        navController.navigate("player?url=$encoded&type=series&episodeId=${ep.episodeId}&startMs=${s ?: -1}")
                                    }
                                }
                            }
                        }, onAssignToKid = { mi -> scope.launch(Dispatchers.IO) { val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx); db.profileDao().all().filter { it.type=="kid" }.forEach { repo.allow(it.id, "series", mi.id) } } }) } } }
                        if (topVod.isNotEmpty()) { header("Filme"); item("all_vod") { com.chris.m3usuite.ui.common.AccentCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { VodRow(items = topVod, onOpenDetails = { mi -> openVod(mi.id) }, onPlayDirect = { mi -> scope.launch { val url = mi.url ?: return@launch; val headers = buildMap<String,String>{ val ua = store.userAgent.first(); val ref = store.referer.first(); if (ua.isNotBlank()) put("User-Agent", ua); if (ref.isNotBlank()) put("Referer", ref) }; com.chris.m3usuite.player.PlayerChooser.start(context = ctx, store = store, url = url, headers = headers, startPositionMs = withContext(Dispatchers.IO){ db.resumeDao().getVod(mi.id)?.positionSecs?.toLong()?.times(1000) }) { s -> val encoded = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8.name()); navController.navigate("player?url=$encoded&type=vod&mediaId=${mi.id}&startMs=${s ?: -1}") } } }, onAssignToKid = { mi -> scope.launch(Dispatchers.IO) { val repo = com.chris.m3usuite.data.repo.KidContentRepository(ctx); db.profileDao().all().filter { it.type=="kid" }.forEach { repo.allow(it.id, "vod", mi.id) } } }) } } }
                    }
                }
            }

            // Neue ein-/ausklappbare Sektion "Weiter schauen"
            @Composable
            fun ResumeCollapsible(content: @Composable () -> Unit) {
                CollapsibleHeader(
                    store = store,
                    title = { Text("Weiter schauen", style = MaterialTheme.typography.titleMedium) },
                    headerContent = { content() },
                    contentBelow = { _ -> /* nichts zusätzlich */ }
                )
            }

            if (canSeeResume) {
                // Render outside of LazyColumn to avoid DSL scope issues
                ResumeCollapsible {
                    ResumeSectionAuto(
                        navController = navController,
                        limit = 20,
                        onPlayVod = { /* TODO */ },
                        onPlayEpisode = { /* TODO */ },
                        onClearVod = {},
                        onClearEpisode = {}
                    )
                }
                // Paged grid for large result sets (search or type browsing)
                // Show grid only for global search or "Alle" view; keep rows for Live/VOD/Series tabs
                if (pagingItems != null && (currentType == null || searchQuery.text.isNotBlank())) {
                    Text("Alle Ergebnisse", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 180.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(min = 240.dp)
                    ) {
                        val lp = pagingItems
                        if (lp != null) {
                            val refreshing = lp.loadState.refresh is androidx.paging.LoadState.Loading && lp.itemCount == 0
                            if (refreshing) {
                                items(12) { _ ->
                                    androidx.compose.foundation.layout.Column {
                                        com.chris.m3usuite.ui.fx.ShimmerBox(modifier = Modifier.aspectRatio(16f / 9f))
                                        Spacer(Modifier.padding(4.dp))
                                        com.chris.m3usuite.ui.fx.ShimmerBox(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                            items(lp.itemCount, key = { idx -> lp[idx]?.id ?: idx.toLong() }) { idx ->
                                val mi = lp[idx] ?: return@items
                                FocusableCard(onClick = {
                                    when (mi.type) {
                                        "live" -> openLive(mi.id)
                                        "vod" -> openVod(mi.id)
                                        "series" -> openSeries(mi.id)
                                    }
                                }) {
                                    val imageHeaders = headers
                                    AsyncImage(
                                        model = buildImageRequest(ctx, mi.poster ?: mi.logo ?: mi.backdrop, imageHeaders),
                                        contentDescription = mi.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.aspectRatio(16f / 9f)
                                    )
                                    Text(
                                        text = mi.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            val appending = lp.loadState.append is androidx.paging.LoadState.Loading
                            if (appending) {
                                items(6) { _ ->
                                    androidx.compose.foundation.layout.Column {
                                        com.chris.m3usuite.ui.fx.ShimmerBox(modifier = Modifier.aspectRatio(16f / 9f))
                                        Spacer(Modifier.padding(4.dp))
                                        com.chris.m3usuite.ui.fx.ShimmerBox(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tabs moved to FishITHeader
            // Inline-Suche/Filter/Kategorien sind zugunsten eines Sheets deaktiviert

            }
            // FAB overlay (does not consume layout height)
            if (canSearch) FloatingActionButton(onClick = { showFilters = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(painterResource(android.R.drawable.ic_menu_search), contentDescription = "Filter & Suche")
            }
        }
    }

    // Overlay-Sheet für Suche/Filter/Kategorien
    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Suche & Filter", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { if (canSearch) searchQuery = it },
                    singleLine = true,
                    label = { Text("Suche (Titel)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch(); if (canSearch) showFilters = false }),
                    enabled = canSearch,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!canSearch) {
                    Text("Suche für dieses Profil deaktiviert", color = MaterialTheme.colorScheme.secondary)
                }
                if (tab == 0) {
                    val activeFilters = (if (filterGerman) 1 else 0) + (if (filterKids) 1 else 0) + filterProviders.size + filterGenres.size
                    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BadgedBox(badge = {
                            if (activeFilters > 0) Badge { Text(activeFilters.toString()) }
                        }) {
                            AssistChip(
                                onClick = { filtersExpanded = !filtersExpanded },
                                label = { Text(if (filtersExpanded) "Filter ausblenden" else "Filter anzeigen") },
                                leadingIcon = { Icon(painterResource(android.R.drawable.ic_menu_manage), contentDescription = "Filter") }
                            )
                        }
                        TextButton(
                            onClick = {
                                filterGerman = false; filterKids = false; filterProviders = emptySet(); filterGenres = emptySet()
                                scope.launch {
                                    store.setLiveFilterGerman(false)
                                    store.setLiveFilterKids(false)
                                    store.setLiveFilterProvidersCsv("")
                                    store.setLiveFilterGenresCsv("")
                                }
                                load()
                            },
                            enabled = activeFilters > 0
                        ) { Text("Reset") }
                    }
                    if (filtersExpanded) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = filterGerman,
                                onClick = { filterGerman = !filterGerman; scope.launch { store.setLiveFilterGerman(filterGerman) }; load() },
                                label = { Text("Deutsch") }
                            )
                            FilterChip(
                                selected = filterKids,
                                onClick = { filterKids = !filterKids; scope.launch { store.setLiveFilterKids(filterKids) }; load() },
                                label = { Text("Kids") }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            liveProviders.forEach { (key, _) ->
                                val sel = key in filterProviders
                                FilterChip(
                                    selected = sel,
                                    onClick = {
                                        filterProviders = if (sel) filterProviders - key else filterProviders + key
                                        scope.launch { store.setLiveFilterProvidersCsv(filterProviders.joinToString(",")) }
                                        load()
                                    },
                                    label = { Text(key) },
                                    leadingIcon = {
                                        val sample = mediaItems.firstOrNull { hasProvider(it, key) }
                                        val url = sample?.logo ?: sample?.poster
                                        if (url != null) {
                                            AsyncImage(
                                                model = buildImageRequest(ctx, url, headers),
                                                contentDescription = "Provider $key",
                                                modifier = Modifier.size(18.dp),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(painterResource(android.R.drawable.ic_menu_info_details), contentDescription = "Provider $key")
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            liveGenres.forEach { g ->
                                val sel = g in filterGenres
                                FilterChip(
                                    selected = sel,
                                    onClick = {
                                        filterGenres = if (sel) filterGenres - g else filterGenres + g
                                        scope.launch { store.setLiveFilterGenresCsv(filterGenres.joinToString(",")) }
                                        load()
                                    },
                                    label = { Text(g) }
                                )
                            }
                        }
                    }
                }
                if (tab in 0..2) {
                    Text("Kategorien", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { AssistChip(modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha), onClick = { selectedCategory = null; load() }, label = { Text("Alle") }) }
                        listItems(categories) { cat ->
                            FilterChip(
                                modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha),
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat; load() },
                                label = { Text(cat ?: "Unbekannt") }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = { showFilters = false }) { Text("Schließen") }
                }
            }
        }
    }

    // Vollständige Kategorienliste als BottomSheet
    if (showCategorySheet) {
        ModalBottomSheet(onDismissRequest = { showCategorySheet = false }) {
            Text(
                "Kategorien",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    ListItem(
                        headlineContent = { Text("Alle") },
                        overlineContent = { Text("Filter zurücksetzen") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedCategory = null
                                showCategorySheet = false
                                scope.launch { load() }
                            }
                    )
                    HorizontalDivider()
                }
                listItems(categories) { cat ->
                    val label = cat ?: "Unbekannt"
                    ListItem(
                        headlineContent = { Text(label) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedCategory = cat
                                showCategorySheet = false
                                scope.launch { load() }
                            }
                    )
                }
            }
        }
    }

    // Freigabe-Sheet: Kids auswählen und Freigaben anwenden
    @Composable
    fun KidSelectSheet(onConfirm: suspend (kidIds: List<Long>) -> Unit, onDismiss: () -> Unit) {
        var kids by remember { mutableStateOf<List<Profile>>(emptyList()) }
        LaunchedEffect(profileId) {
            val list = withContext(Dispatchers.IO) { DbProvider.get(ctx).profileDao().all().filter { it.type == "kid" } }
            kids = list
        }
        var checked by remember { mutableStateOf(setOf<Long>()) }
        ModalBottomSheet(onDismissRequest = onDismiss) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Text("Kinder auswählen") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { checked = kids.map { it.id }.toSet() }, enabled = kids.isNotEmpty()) { Text("Alle auswählen") }
                        TextButton(onClick = { checked = emptySet() }, enabled = checked.isNotEmpty()) { Text("Keine auswählen") }
                    }
                }
                listItems(kids, key = { it.id }) { k: Profile ->
                    val isC = k.id in checked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tvClickable {
                                val v = !isC
                                checked = if (v) checked + k.id else checked - k.id
                            },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(k.name)
                        Switch(checked = isC, onCheckedChange = { v -> checked = if (v) checked + k.id else checked - k.id })
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(modifier = Modifier.weight(1f).focusScaleOnTv(), onClick = onDismiss) { Text("Abbrechen") }
                        Button(modifier = Modifier.weight(1f).focusScaleOnTv(), onClick = { scope.launch { onConfirm(checked.toList()); onDismiss() } }, enabled = checked.isNotEmpty()) { Text("OK") }
                    }
                }
            }
        }
    }

    if (showGrantSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) {
            // gruppiert nach Typ
            val byType = selected.groupBy({ it.second }, { it.first })
            for ((t, ids) in byType) {
                kidIds.forEach { kidId -> kidRepo.allowBulk(kidId, t, ids) }
            }
        }
        scope.launch { snackHost.showSnackbar("Freigaben: ${selected.size} Einträge für ${kidIds.size} Kinder") }
        selected = emptySet(); selectionMode = false; showGrantSheet = false
    }, onDismiss = { showGrantSheet = false })

    if (showRevokeSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) {
            val byType = selected.groupBy({ it.second }, { it.first })
            for ((t, ids) in byType) {
                kidIds.forEach { kidId -> kidRepo.disallowBulk(kidId, t, ids) }
            }
        }
        scope.launch { snackHost.showSnackbar("Entfernt: ${selected.size} Einträge aus ${kidIds.size} Kinderprofil(en)") }
        selected = emptySet(); selectionMode = false; showRevokeSheet = false
    }, onDismiss = { showRevokeSheet = false })
}

/* Grid/List content removed to enforce single-rail layout */
