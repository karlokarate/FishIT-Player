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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
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
import com.chris.m3usuite.ui.state.rememberRouteGridState
import com.chris.m3usuite.ui.state.rememberRouteListState
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chris.m3usuite.data.db.Profile
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.chris.m3usuite.ui.components.rows.ResumeRow
import com.chris.m3usuite.ui.components.rows.LiveRow
import com.chris.m3usuite.ui.components.rows.SeriesRow
import com.chris.m3usuite.ui.components.rows.VodRow
import com.chris.m3usuite.ui.home.header.FishITHeader
import com.chris.m3usuite.ui.home.header.FishITHeaderHeights
import com.chris.m3usuite.ui.home.header.rememberHeaderAlpha
import androidx.compose.foundation.lazy.rememberLazyListState
import com.chris.m3usuite.ui.home.header.FishITBottomPanel
import com.chris.m3usuite.ui.home.header.FishITBottomHeights
import com.chris.m3usuite.data.db.MediaItem as DbMediaItem
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.skin.focusScaleOnTv

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
    val scope = rememberCoroutineScope()
    val tv = isTv(ctx)
    val focus = LocalFocusManager.current
    val headers = rememberImageHeaders()
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by store.hapticsEnabled.collectAsState(initial = false)

    val storedTab by store.libraryTabIndex.collectAsState(initial = 0)
    var tab by rememberSaveable { mutableIntStateOf(storedTab) }
    LaunchedEffect(storedTab) { if (tab != storedTab) tab = storedTab }
    val tabs = listOf("Live", "VOD", "Series", "Alle")

    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var categories by remember { mutableStateOf<List<String?>>(emptyList()) }

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
    val persistedGerman by store.liveFilterGerman.collectAsState(initial = false)
    val persistedKids by store.liveFilterKids.collectAsState(initial = false)
    val persistedProviders by store.liveFilterProvidersCsv.collectAsState(initial = "")
    val persistedGenres by store.liveFilterGenresCsv.collectAsState(initial = "")
    LaunchedEffect(persistedGerman, persistedKids, persistedProviders, persistedGenres) {
        filterGerman = persistedGerman
        filterKids = persistedKids
        filterProviders = persistedProviders.split(',').filter { it.isNotBlank() }.toSet()
        filterGenres = persistedGenres.split(',').filter { it.isNotBlank() }.toSet()
    }

    // Collapsible-State für Header (global gespeichert)
    val collapsed by store.headerCollapsed.collectAsState(initial = false)

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

    fun submitSearch() { load(); focus.clearFocus() }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(tab, selectedCategory) { load() }

    // Adult-only: Settings sichtbar, Kids versteckt; Selection-Mode für Freigaben (nur Adult)
    val profileId by store.currentProfileId.collectAsState(initial = -1L)
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
    Scaffold(
        snackbarHost = { SnackbarHost(snackHost) }
    ) { paddingValues ->
        val headerListState = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = FishITHeaderHeights.total)
                    .padding(bottom = FishITBottomHeights.bar)
            ) {
            // Top rails (Resume, Live, Series, VOD) – kid-friendly, no headers
            var topResume by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }
            var topLive by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }
            var topSeries by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }
            var topVod by remember { mutableStateOf<List<DbMediaItem>>(emptyList()) }

            // Determine kid profile from existing flag
            val isKidProfile = !showSettings

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
                    topResume = out
                }
                // Live/Series/VOD: lightweight lists (already filtered for kids by MediaQueryRepository)
                runCatching { topLive = mediaRepo.listByTypeFiltered("live", 20, 0) }
                runCatching { topSeries = mediaRepo.listByTypeFiltered("series", 20, 0) }
                runCatching { topVod = mediaRepo.listByTypeFiltered("vod", 20, 0) }
            }

            val railPad = if (isKidProfile) 20.dp else 16.dp
            // Rails inside a small LazyColumn to drive header scrim alpha
            LazyColumn(state = headerListState, contentPadding = PaddingValues(vertical = 0.dp)) {
                if (topResume.isNotEmpty()) item("rail_resume") {
                    Box(Modifier.padding(horizontal = (railPad - 16.dp))) {
                        ResumeRow(items = topResume) { mi ->
                            when (mi.type) {
                                "live" -> openLive(mi.id)
                                "vod" -> openVod(mi.id)
                                "series" -> openSeries(mi.id)
                            }
                        }
                    }
                }
                if (topLive.isNotEmpty()) item("rail_live") {
                    Box(Modifier.padding(horizontal = (railPad - 16.dp))) {
                        LiveRow(items = topLive) { mi -> openLive(mi.id) }
                    }
                }
                if (topSeries.isNotEmpty()) item("rail_series") {
                    Box(Modifier.padding(horizontal = (railPad - 16.dp))) {
                        SeriesRow(items = topSeries) { mi -> openSeries(mi.id) }
                    }
                }
                if (topVod.isNotEmpty()) item("rail_vod") {
                    Box(Modifier.padding(horizontal = (railPad - 16.dp))) {
                        VodRow(items = topVod) { mi -> openVod(mi.id) }
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

            ResumeCollapsible {
                ResumeSectionAuto(
                    navController = navController,
                    limit = 20,
                    onPlayVod = { /* TODO: später Navigation/Player */ },
                    onPlayEpisode = { /* TODO: später Navigation/Player */ },
                    onClearVod = {},
                    onClearEpisode = {}
                )
            }

            // Tabs moved to FishITHeader
            // Inline-Suche/Filter/Kategorien sind zugunsten eines Sheets deaktiviert

            // Content: Grid oder Liste – bekommt die restliche Höhe
            val useGrid = tab != 0 || tv
            if (useGrid) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val gridState = rememberRouteGridState("library_grid_${tab}_${selectedCategory ?: "all"}")
                    LibraryGridContent(
                        tv = tv,
                        mediaItems = mediaItems,
                        ctx = ctx,
                        headers = headers,
                        state = gridState,
                        selectionMode = selectionMode,
                        isSelected = { id, type -> (id to type) in selected },
                        onOpen = { mi ->
                            if (selectionMode) {
                                val key = mi.id to mi.type
                                selected = if (key in selected) selected - key else selected + key
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                when (mi.type) {
                                    "live" -> openLive(mi.id)
                                    "vod" -> openVod(mi.id)
                                    "series" -> openSeries(mi.id)
                                }
                            }
                        }
                    )
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val listState = rememberRouteListState("library_list_${tab}_${selectedCategory ?: "all"}")
                    LibraryListContent(
                        mediaItems = mediaItems,
                        ctx = ctx,
                        headers = headers,
                        state = listState,
                        selectionMode = selectionMode,
                        isSelected = { id, type -> (id to type) in selected },
                        onOpenLive = { id ->
                            if (selectionMode) {
                                val key = id to "live"
                                selected = if (key in selected) selected - key else selected + key
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else openLive(id)
                        }
                    )
                }
            }

            // FAB zum Öffnen der Filter-/Suche
            Box(Modifier.fillMaxWidth()) {
                FloatingActionButton(onClick = { showFilters = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                    Icon(painterResource(android.R.drawable.ic_menu_search), contentDescription = "Filter & Suche")
                }
            }
            }

            // Overlay Header (FishIT)
            val scrimAlpha = rememberHeaderAlpha(headerListState)
            FishITHeader(
                title = "m3uSuite",
                onSettings = { navController.navigate("settings") },
                scrimAlpha = scrimAlpha
            )

            // Bottom persistent panel (TV / Filme / Serien)
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
    }

    // Overlay-Sheet für Suche/Filter/Kategorien
    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Suche & Filter", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    label = { Text("Suche (Titel)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch(); showFilters = false }),
                    modifier = Modifier.fillMaxWidth()
                )
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
                        item { AssistChip(onClick = { selectedCategory = null; load() }, label = { Text("Alle") }) }
                        listItems(categories) { cat ->
                            FilterChip(
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

/* -------------------------- Extracted content composables -------------------------- */

@Composable
private fun LibraryGridContent(
    tv: Boolean,
    mediaItems: List<MediaItem>,
    ctx: android.content.Context,
    headers: com.chris.m3usuite.ui.util.ImageHeaders,
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    selectionMode: Boolean,
    isSelected: (Long, String) -> Boolean,
    onOpen: (MediaItem) -> Unit
) {
    val columns = if (tv) 4 else 2
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        // KEIN weight() hier – der liegt im Aufrufer in der Column
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        gridItems(mediaItems, key = { it.id }) { mi ->
            FocusableCard(onClick = { onOpen(mi) }) {
                Column {
                    AsyncImage(
                        model = buildImageRequest(ctx, mi.poster ?: mi.logo, headers),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (tv) 180.dp else 160.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(mi.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        mi.categoryName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (selectionMode) {
                        val sel = isSelected(mi.id, mi.type)
                        if (sel) {
                            Text("Ausgewählt", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryListContent(
    mediaItems: List<MediaItem>,
    ctx: android.content.Context,
    headers: com.chris.m3usuite.ui.util.ImageHeaders,
    state: androidx.compose.foundation.lazy.LazyListState,
    selectionMode: Boolean,
    isSelected: (Long, String) -> Boolean,
    onOpenLive: (Long) -> Unit
) {
    LazyColumn(
        // KEIN weight() hier – der liegt im Aufrufer in der Column
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(12.dp)
    ) {
        listItems(mediaItems, key = { it.id }) { mi ->
            FocusableCard(onClick = { onOpenLive(mi.id) }) {
                Row {
                    AsyncImage(
                        model = buildImageRequest(ctx, mi.logo ?: mi.poster, headers),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mi.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            mi.categoryName ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1
                        )
                        if (selectionMode) {
                            val sel = isSelected(mi.id, mi.type)
                            if (sel) {
                                Text("Ausgewählt", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
