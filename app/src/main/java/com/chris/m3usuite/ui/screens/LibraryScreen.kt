package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.chris.m3usuite.core.xtream.ProviderLabelStore
import com.chris.m3usuite.core.playback.PlayUrlHelper
import com.chris.m3usuite.data.obx.toMediaItem
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

private enum class ContentTab { Live, Vod, Series }

@Composable
private fun rememberSelectedTab(store: SettingsStore): ContentTab {
    val tabIdx by store.libraryTabIndex.collectAsStateWithLifecycle(initialValue = 0)
    return when (tabIdx) {
        0 -> ContentTab.Live
        1 -> ContentTab.Vod
        else -> ContentTab.Series
    }
}

private data class GroupKeys(
    val providers: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val categories: List<String> = emptyList()
)

@Composable
fun LibraryScreen(
    navController: NavHostController,
    openLive: (Long) -> Unit,
    openVod: (Long) -> Unit,
    openSeries: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val repo = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }
    val mediaRepo = remember { com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, store) }
    val resumeRepo = remember { com.chris.m3usuite.data.repo.ResumeRepository(ctx) }
    val permRepo = remember { com.chris.m3usuite.data.repo.PermissionRepository(ctx, store) }
    val providerLabelStore = remember(ctx) { ProviderLabelStore.get(ctx) }
    val showAdultsEnabled by store.showAdults.collectAsStateWithLifecycle(initialValue = false)

    val scope = rememberCoroutineScope()
    // Lifecycle-aware: tick when screen resumes to force lightweight reloads
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Bump a counter so effects keyed with resumeTick run again on return
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val wm = remember { WorkManager.getInstance(ctx) }

    // Tab-Auswahl synchron zur Start-Optik (BottomPanel)
    val selectedTab = rememberSelectedTab(store)
    val selectedTabKey = when (selectedTab) {
        ContentTab.Live -> "live"
        ContentTab.Vod -> "vod"
        ContentTab.Series -> "series"
    }
    val listState = com.chris.m3usuite.ui.state.rememberRouteListState("library:list:${selectedTab}")

    // Rechte (Whitelist-Editing)
    var canEditWhitelist by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { canEditWhitelist = permRepo.current().canEditWhitelist }

    // Ensure provider/genre/year keys are backfilled/corrected at least once
    LaunchedEffect(Unit) {
        runCatching { com.chris.m3usuite.work.ObxKeyBackfillWorker.scheduleOnce(ctx) }
    }

    // Suche
    var query by remember { mutableStateOf(TextFieldValue("")) }

    // Suchtreffer (nur für aktiven Tab)
    var searchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    // Gruppenschlüssel (nur für aktiven Tab, wenn keine Suche aktiv)
    var groupKeys by remember { mutableStateOf(GroupKeys()) }
    val vodSortNewest by store.libVodSortNewest.collectAsStateWithLifecycle(initialValue = true)
    val seriesSortNewest by store.libSeriesSortNewest.collectAsStateWithLifecycle(initialValue = true)
    val groupByGenreLive by store.libGroupByGenreLive.collectAsStateWithLifecycle(initialValue = false)
    val groupByGenreVod by store.libGroupByGenreVod.collectAsStateWithLifecycle(initialValue = false)
    val groupByGenreSeries by store.libGroupByGenreSeries.collectAsStateWithLifecycle(initialValue = false)

    // Caches für Gruppeninhalte je sichtbarem Tab
    val providerCache = remember { mutableStateMapOf<String, List<MediaItem>>() }
    val genreCache = remember { mutableStateMapOf<String, List<MediaItem>>() }
    val yearCache = remember { mutableStateMapOf<Int, List<MediaItem>>() }
    var cacheVersion by remember { mutableStateOf(0) }
    var lastTab by remember { mutableStateOf(selectedTab) }
    var lastIsSearchBlank by remember { mutableStateOf(true) }

    fun invalidateCaches() {
        providerCache.clear()
        genreCache.clear()
        yearCache.clear()
        cacheVersion++
    }

    // Top rows (recent, newest) per tab
    var recentRow by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var newestRow by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var topYearsRow by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    // Helpers: adults filter + kid/guest whitelist
    suspend fun withoutAdultsFor(tab: ContentTab, items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        val show = store.showAdults.first()
        if (show) return@withContext items
        val kind = when (tab) { ContentTab.Live -> "live"; ContentTab.Vod -> "vod"; ContentTab.Series -> "series" }
        val labelById = runCatching { repo.categories(kind).associateBy({ it.categoryId }, { it.categoryName }) }.getOrElse { emptyMap() }
        items.filter { mi ->
            val name = labelById[mi.categoryId]?.trim().orEmpty()
            !Regex("\\bfor adults\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)
        }
    }

    // Always exclude Adults, regardless of settings (use for non‑adult rows)
    suspend fun excludeAdultsAlways(tab: ContentTab, items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        val kind = when (tab) { ContentTab.Live -> "live"; ContentTab.Vod -> "vod"; ContentTab.Series -> "series" }
        val labelById = runCatching { repo.categories(kind).associateBy({ it.categoryId }, { it.categoryName }) }.getOrElse { emptyMap() }
        items.filter { mi ->
            val name = labelById[mi.categoryId]?.trim().orEmpty()
            !Regex("\\bfor adults\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)
        }
    }
    suspend fun allowedOnly(tab: ContentTab, items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        val type = when (tab) { ContentTab.Live -> "live"; ContentTab.Vod -> "vod"; ContentTab.Series -> "series" }
        items.filter { mediaRepo.isAllowed(type, it.id) }
    }

    suspend fun loadRecentForTab(tab: ContentTab): List<MediaItem> = withContext(Dispatchers.IO) {
        when (tab) {
            ContentTab.Vod -> {
                val obx = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
                val box = obx.boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)
                val marks = resumeRepo.recentVod(60)
                val items = marks.mapNotNull { mark ->
                    val vodId = (mark.mediaId - 2_000_000_000_000L).toInt()
                    val row = box.query(com.chris.m3usuite.data.obx.ObxVod_.vodId.equal(vodId.toLong())).build().findFirst()
                    row?.toMediaItem(ctx)
                }
                // filter allowed for kid profiles + adults toggle
                withoutAdultsFor(ContentTab.Vod, items.filter { mediaRepo.isAllowed("vod", it.id) })
            }
            ContentTab.Series -> {
                val obx = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
                val box = obx.boxFor(com.chris.m3usuite.data.obx.ObxSeries::class.java)
                val marks = resumeRepo.recentEpisodes(60)
                val seriesItems = marks.mapNotNull { mk ->
                    val row = box.query(com.chris.m3usuite.data.obx.ObxSeries_.seriesId.equal(mk.seriesId.toLong())).build().findFirst()
                    row?.toMediaItem(ctx)
                }.distinctBy { it.id }
                withoutAdultsFor(ContentTab.Series, seriesItems.filter { mediaRepo.isAllowed("series", it.id) })
            }
            else -> emptyList()
        }
    }

    suspend fun loadNewestForTab(tab: ContentTab): List<MediaItem> = withContext(Dispatchers.IO) {
        when (tab) {
            ContentTab.Vod -> excludeAdultsAlways(ContentTab.Vod, repo.vodPagedNewest(0, 120).map { it.toMediaItem(ctx) }.filter { mediaRepo.isAllowed("vod", it.id) })
            ContentTab.Series -> withoutAdultsFor(ContentTab.Series, repo.seriesPagedNewest(0, 120).map { it.toMediaItem(ctx) }.filter { mediaRepo.isAllowed("series", it.id) })
            else -> emptyList()
        }
    }

    // Helper: Laden flacher Treffer (Suche)
    suspend fun loadFlat(tab: ContentTab, q: String): List<MediaItem> = withContext(Dispatchers.IO) {
        when (tab) {
            ContentTab.Live -> {
                val rows = if (q.isNotBlank()) repo.searchLiveByName(q.trim(), 0, 240) else repo.livePaged(0, 240)
                val base = withoutAdultsFor(ContentTab.Live, rows.map { it.toMediaItem(ctx) })
                allowedOnly(ContentTab.Live, base)
            }
            ContentTab.Vod -> {
                val rows = if (q.isNotBlank()) repo.searchVodByName(q.trim(), 0, 240) else repo.vodPaged(0, 240)
                val base = excludeAdultsAlways(ContentTab.Vod, rows.map { it.toMediaItem(ctx) })
                allowedOnly(ContentTab.Vod, base)
            }
            ContentTab.Series -> {
                val rows = if (q.isNotBlank()) repo.searchSeriesByName(q.trim(), 0, 240) else repo.seriesPaged(0, 240)
                val base = withoutAdultsFor(ContentTab.Series, rows.map { it.toMediaItem(ctx) })
                allowedOnly(ContentTab.Series, base)
            }
        }
    }

    // Helper: Gruppenschlüssel für aktiven Tab laden (nur ohne Suche)
    suspend fun loadGroupKeys(tab: ContentTab): GroupKeys = withContext(Dispatchers.IO) {
        when (tab) {
            ContentTab.Live -> {
                // Use API categories directly for Live (filtered by seeding whitelist)
                fun extractPrefix(name: String?): String? {
                    if (name.isNullOrBlank()) return null
                    var s = name.trim()
                    if (s.startsWith("[")) {
                        val idx = s.indexOf(']')
                        if (idx > 0) s = s.substring(1, idx)
                    }
                    val m = Regex("^([A-Z\\-]{2,6})").find(s.uppercase()) ?: return null
                    return m.groupValues[1].replace("-", "").trim().takeIf { it.isNotBlank() }
                }
                val allowed = store.seedPrefixesSet()
                val liveCats = repo.categories("live")
                val catIds = liveCats
                    .filter { row -> allowed.contains(extractPrefix(row.categoryName)) }
                    .mapNotNull { it.categoryId }
                GroupKeys(
                    providers = emptyList(),
                    genres = emptyList(),
                    years = emptyList(),
                    categories = catIds
                )
            }
            ContentTab.Vod -> GroupKeys(
                providers = repo.indexProviderKeys("vod"),
                genres = repo.indexGenreKeys("vod"),
                years = repo.indexYearKeys("vod"),
                categories = emptyList()
            )
            ContentTab.Series -> GroupKeys(
                providers = repo.indexProviderKeys("series"),
                genres = repo.indexGenreKeys("series"),
                years = repo.indexYearKeys("series"),
                categories = emptyList()
            )
        }
    }

    // Helper: Items für einen Gruppenschlüssel laden (mit Cache)
    suspend fun loadItemsForProvider(tab: ContentTab, key: String): List<MediaItem> = withContext(Dispatchers.IO) {
        providerCache[key] ?: run {
            val itemsRaw = when (tab) {
                ContentTab.Live -> repo.liveByProviderKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) }
                ContentTab.Vod -> {
                    val list = if (vodSortNewest) repo.vodByProviderKeyNewest(key, 0, 120) else repo.vodByProviderKeyPaged(key, 0, 120)
                    list.map { it.toMediaItem(ctx) }
                }
                ContentTab.Series -> {
                    val list = if (seriesSortNewest) repo.seriesByProviderKeyNewest(key, 0, 120) else repo.seriesByProviderKeyPaged(key, 0, 120)
                    list.map { it.toMediaItem(ctx) }
                }
            }
            val items = allowedOnly(tab, withoutAdultsFor(tab, itemsRaw))
            providerCache[key] = items
            items
        }
    }

    suspend fun loadItemsForGenre(tab: ContentTab, key: String): List<MediaItem> = withContext(Dispatchers.IO) {
        genreCache[key] ?: run {
            val itemsRaw = when (tab) {
                ContentTab.Live -> repo.liveByGenreKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) }
                ContentTab.Vod -> repo.vodByGenreKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) }
                ContentTab.Series -> repo.seriesByGenreKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) }
            }
            val items = allowedOnly(tab, if (tab == ContentTab.Vod) excludeAdultsAlways(tab, itemsRaw) else withoutAdultsFor(tab, itemsRaw))
            genreCache[key] = items
            items
        }
    }

    suspend fun loadItemsForYear(tab: ContentTab, y: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        yearCache[y] ?: run {
            val itemsRaw = when (tab) {
                ContentTab.Vod -> repo.vodByYearKeyPaged(y, 0, 120).map { it.toMediaItem(ctx) }
                ContentTab.Series -> repo.seriesByYearKeyPaged(y, 0, 120).map { it.toMediaItem(ctx) }
                ContentTab.Live -> emptyList()
            }
            val items = allowedOnly(tab, if (tab == ContentTab.Vod) excludeAdultsAlways(tab, itemsRaw) else withoutAdultsFor(tab, itemsRaw))
            yearCache[y] = items
            items
        }
    }

    suspend fun loadItemsForLiveCategory(catId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val key = "live:" + catId
        // reuse providerCache for simplicity with distinct keyspace
        providerCache[key] ?: run {
            val base = withoutAdultsFor(ContentTab.Live, repo.liveByCategoryPaged(catId, 0, 180).map { it.toMediaItem(ctx) })
            val items = allowedOnly(ContentTab.Live, base)
            providerCache[key] = items
            items
        }
    }

    // (Re)Load bei Tab- oder Suchwechsel
    // Also re-run when the screen comes back into focus (resumeTick)
    LaunchedEffect(selectedTab, query.text, resumeTick) {
        val isBlank = query.text.isBlank()
        val tabChanged = selectedTab != lastTab
        val searchModeChanged = isBlank != lastIsSearchBlank
        if (tabChanged || searchModeChanged) {
            invalidateCaches()
        }

        if (isBlank) {
            if (searchResults.isNotEmpty()) searchResults = emptyList()
            val newKeys = loadGroupKeys(selectedTab)
            if (newKeys != groupKeys) {
                groupKeys = newKeys
            }

            val recent = loadRecentForTab(selectedTab)
            if (recent != recentRow) {
                recentRow = recent
            }
            val newest = loadNewestForTab(selectedTab)
            if (newest != newestRow) {
                newestRow = newest
            }
            val yearsRow = when (selectedTab) {
                ContentTab.Vod -> withContext(Dispatchers.IO) { repo.vodByYearsNewest(intArrayOf(2025, 2024), 0, 180).map { it.toMediaItem(ctx) } }
                ContentTab.Series -> withContext(Dispatchers.IO) { repo.seriesByYearsNewest(intArrayOf(2025, 2024), 0, 180).map { it.toMediaItem(ctx) } }
                else -> emptyList()
            }
            if (yearsRow != topYearsRow) {
                topYearsRow = yearsRow
            }
        } else {
            if (groupKeys != GroupKeys()) {
                groupKeys = GroupKeys() // keine Gruppenansicht im Suchmodus
            }
            val results = loadFlat(selectedTab, query.text)
            if (results != searchResults) {
                searchResults = results
            }
            if (recentRow.isNotEmpty()) recentRow = emptyList()
            if (newestRow.isNotEmpty()) newestRow = emptyList()
            if (topYearsRow.isNotEmpty()) topYearsRow = emptyList()
        }
        lastTab = selectedTab
        lastIsSearchBlank = isBlank
    }

    // OBX-Änderungen live spiegeln (nur relevanten Bereich aktualisieren)
    // Restart subscriptions when selected tab changes to avoid stale captures
    LaunchedEffect(selectedTab) {
        // Live
        launch {
            repo.liveChanges().collect {
                if (selectedTab == ContentTab.Live) {
                    if (query.text.isBlank()) {
                        val newKeys = loadGroupKeys(ContentTab.Live)
                        if (newKeys != groupKeys) {
                            groupKeys = newKeys
                        }
                    } else {
                        val results = loadFlat(ContentTab.Live, query.text)
                        if (results != searchResults) {
                            searchResults = results
                        }
                    }
                    invalidateCaches()
                }
            }
        }
        // Vod
        launch {
            repo.vodChanges().collect {
                if (selectedTab == ContentTab.Vod) {
                    if (query.text.isBlank()) {
                        val newKeys = loadGroupKeys(ContentTab.Vod)
                        if (newKeys != groupKeys) {
                            groupKeys = newKeys
                        }
                        val recent = loadRecentForTab(ContentTab.Vod)
                        if (recent != recentRow) {
                            recentRow = recent
                        }
                        val newest = loadNewestForTab(ContentTab.Vod)
                        if (newest != newestRow) {
                            newestRow = newest
                        }
                        val yearsRow = withContext(Dispatchers.IO) { repo.vodByYearsNewest(intArrayOf(2025, 2024), 0, 180).map { it.toMediaItem(ctx) } }
                        if (yearsRow != topYearsRow) {
                            topYearsRow = yearsRow
                        }
                    } else {
                        val results = loadFlat(ContentTab.Vod, query.text)
                        if (results != searchResults) {
                            searchResults = results
                        }
                        if (recentRow.isNotEmpty()) recentRow = emptyList()
                        if (newestRow.isNotEmpty()) newestRow = emptyList()
                        if (topYearsRow.isNotEmpty()) topYearsRow = emptyList()
                    }
                    invalidateCaches()
                }
            }
        }
        // Series
        launch {
            repo.seriesChanges().collect {
                if (selectedTab == ContentTab.Series) {
                    if (query.text.isBlank()) {
                        val newKeys = loadGroupKeys(ContentTab.Series)
                        if (newKeys != groupKeys) {
                            groupKeys = newKeys
                        }
                        val recent = loadRecentForTab(ContentTab.Series)
                        if (recent != recentRow) {
                            recentRow = recent
                        }
                        val newest = loadNewestForTab(ContentTab.Series)
                        if (newest != newestRow) {
                            newestRow = newest
                        }
                        val yearsRow = withContext(Dispatchers.IO) { repo.seriesByYearsNewest(intArrayOf(2025, 2024), 0, 180).map { it.toMediaItem(ctx) } }
                        if (yearsRow != topYearsRow) {
                            topYearsRow = yearsRow
                        }
                    } else {
                        val results = loadFlat(ContentTab.Series, query.text)
                        if (results != searchResults) {
                            searchResults = results
                        }
                        if (recentRow.isNotEmpty()) recentRow = emptyList()
                        if (newestRow.isNotEmpty()) newestRow = emptyList()
                        if (topYearsRow.isNotEmpty()) topYearsRow = emptyList()
                    }
                    invalidateCaches()
                }
            }
        }
    }

    // Delta-Import-Resultat (WorkManager) ohne Polling beobachten
    LaunchedEffect(Unit) {
        wm.getWorkInfosForUniqueWorkLiveData("xtream_delta_import_once")
            .asFlow()
            .collect { infos ->
                val done = infos.any { it.state == WorkInfo.State.SUCCEEDED }
                if (done) {
                    if (query.text.isBlank()) {
                        val newKeys = loadGroupKeys(selectedTab)
                        if (newKeys != groupKeys) {
                            groupKeys = newKeys
                        }
                    } else {
                        val results = loadFlat(selectedTab, query.text)
                        if (results != searchResults) {
                            searchResults = results
                        }
                    }
                    invalidateCaches()
                }
            }
    }

    // Routing-Handler je Tab
    val onOpen: (MediaItem) -> Unit = when (selectedTab) {
        ContentTab.Live -> { m -> openLive(m.id) }
        ContentTab.Vod -> { m -> openVod(m.id) }
        ContentTab.Series -> { m -> openSeries(m.id) }
    }
    val onPlay: (MediaItem) -> Unit = remember(selectedTab, scope, ctx, store, navController) {
        when (selectedTab) {
            ContentTab.Live -> { media ->
                scope.launch {
                    val req = PlayUrlHelper.forLive(ctx, store, media)
                    if (req == null) {
                        onOpen(media)
                        return@launch
                    }
                    PlayerChooser.start(
                        context = ctx,
                        store = store,
                        url = req.url,
                        headers = req.headers,
                        startPositionMs = null,
                        mimeType = req.mimeType
                    ) { startMs, resolvedMime ->
                        val encoded = PlayUrlHelper.encodeUrl(req.url)
                        val mimeArg = resolvedMime?.let { Uri.encode(it) } ?: ""
                        // Mark origin as Live Library for global switching & list overlay (not favorites)
                        navController.navigate("player?url=$encoded&type=live&mediaId=${media.id}&startMs=${startMs ?: -1}&mime=$mimeArg&origin=lib")
                    }
                }
            }
            ContentTab.Vod -> { media ->
                scope.launch {
                    val req = PlayUrlHelper.forVod(ctx, store, media)
                    if (req == null) {
                        onOpen(media)
                        return@launch
                    }
                    val resumeMs = withContext(Dispatchers.IO) {
                        com.chris.m3usuite.data.repo.ResumeRepository(ctx)
                            .recentVod(1)
                            .firstOrNull { it.mediaId == media.id }
                            ?.positionSecs?.toLong()?.times(1000)
                    }
                    PlayerChooser.start(
                        context = ctx,
                        store = store,
                        url = req.url,
                        headers = req.headers,
                        startPositionMs = resumeMs,
                        mimeType = req.mimeType
                    ) { startMs, resolvedMime ->
                        val encoded = PlayUrlHelper.encodeUrl(req.url)
                        val mimeArg = resolvedMime?.let { Uri.encode(it) } ?: ""
                        navController.navigate("player?url=$encoded&type=vod&mediaId=${media.id}&startMs=${startMs ?: -1}&mime=$mimeArg")
                    }
                }
            }
            ContentTab.Series -> { media -> onOpen(media) }
        }
    }

    // Kid-Whitelist-Zuweisung (nur Vod/Series)
    val onAssignVod: (MediaItem) -> Unit = remember(canEditWhitelist) {
        { mi ->
            if (!canEditWhitelist) return@remember
            scope.launch(Dispatchers.IO) {
                val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                val kRepo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                kids.forEach { kRepo.allow(it.id, "vod", mi.id) }
            }
        }
    }
    val onAssignSeries: (MediaItem) -> Unit = remember(canEditWhitelist) {
        { mi ->
            if (!canEditWhitelist) return@remember
            scope.launch(Dispatchers.IO) {
                val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                val kRepo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                kids.forEach { kRepo.allow(it.id, "series", mi.id) }
            }
        }
    }

    HomeChromeScaffold(
        title = "Bibliothek",
        onSettings = null,
        onSearch = { navController.navigate("library?qs=show") { launchSingleTop = true } },
        onProfiles = null,
        listState = listState,
        onLogo = {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != "library?q={q}&qs={qs}") {
                navController.navigate("library?q=&qs=") { launchSingleTop = true }
            }
        },
        bottomBar = {
            com.chris.m3usuite.ui.home.header.FishITBottomPanel(
                selected = when (selectedTab) {
                    ContentTab.Live -> "live"
                    ContentTab.Vod -> "vod"
                    ContentTab.Series -> "series"
                },
                onSelect = { sel ->
                    val idx = when (sel) { "live" -> 0; "vod" -> 1; else -> 2 }
                    scope.launch { store.setLibraryTabIndex(idx) }
                }
            )
        }
    ) { pads ->
        // identische Optik wie Start/Settings: Fish-Background + Paddings
        Box(Modifier.fillMaxSize()) {
            com.chris.m3usuite.ui.fx.FishBackground(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                alpha = 0.06f
            )
        }

        LazyColumn(
            state = listState,
            flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(pads)
        ) {
            // Globale Suche per Header – lokales Suchfeld entfernt

            // Suchergebnisse (ein Row pro aktivem Tab)
            if (false) {
                item {
                    Text(
                        when (selectedTab) {
                            ContentTab.Live -> "Live – Suchtreffer"
                            ContentTab.Vod -> "Filme – Suchtreffer"
                            ContentTab.Series -> "Serien – Suchtreffer"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                item {
                        MediaRowForTab(
                            tab = selectedTab,
                            items = searchResults,
                            onOpenDetails = onOpen,
                            onPlayDirect = onPlay,
                            onAssignToKid = when (selectedTab) {
                                ContentTab.Vod -> onAssignVod
                                ContentTab.Series -> onAssignSeries
                                ContentTab.Live -> null
                            },
                            showAssign = canEditWhitelist && selectedTab != ContentTab.Live
                        )
                    }
                }

            // Gruppenansichten (nur wenn keine Suche aktiv)
            if (query.text.isBlank()) {
                // Provider
                if (selectedTab != ContentTab.Live) {
                    // Top rows for VOD/Series: Zuletzt gesehen, Neu
                    if (recentRow.isNotEmpty()) {
                        item {
                            if (selectedTab == ContentTab.Vod) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    com.chris.m3usuite.ui.components.chips.CategoryChip(key = "recent", label = "Zuletzt gespielt")
                                }
                            } else {
                                Text("Zuletzt gesehen – Serien", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                            }
                        }
                        item {
                            when (selectedTab) {
                                ContentTab.Vod -> com.chris.m3usuite.ui.components.rows.VodRow(
                                    items = recentRow,
                                    stateKey = "library:${selectedTabKey}:recent",
                                    onOpenDetails = onOpen,
                                    onPlayDirect = onPlay,
                                    onAssignToKid = onAssignVod,
                                    showAssign = canEditWhitelist
                                )
                                ContentTab.Series -> com.chris.m3usuite.ui.components.rows.SeriesRow(
                                    items = recentRow,
                                    stateKey = "library:${selectedTabKey}:recent",
                                    onOpenDetails = onOpen,
                                    onPlayDirect = onPlay,
                                    onAssignToKid = onAssignSeries,
                                    showAssign = canEditWhitelist
                                )
                                else -> {}
                            }
                        }
                    }
                    if (newestRow.isNotEmpty()) {
                        val labelNew = if (selectedTab == ContentTab.Vod) "Neu – Aktuell" else "Neu"
                        item {
                            if (selectedTab == ContentTab.Vod) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    com.chris.m3usuite.ui.components.chips.CategoryChip(key = "new", label = labelNew)
                                }
                            } else {
                                Text(labelNew, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                            }
                        }
                        item {
                            when (selectedTab) {
                                ContentTab.Vod -> com.chris.m3usuite.ui.components.rows.VodRow(
                                    items = newestRow,
                                    stateKey = "library:${selectedTabKey}:newest",
                                    onOpenDetails = onOpen,
                                    onPlayDirect = onPlay,
                                    onAssignToKid = onAssignVod,
                                    showNew = true,
                                    showAssign = canEditWhitelist
                                )
                                ContentTab.Series -> com.chris.m3usuite.ui.components.rows.SeriesRow(
                                    items = newestRow,
                                    stateKey = "library:${selectedTabKey}:newest",
                                    onOpenDetails = onOpen,
                                    onPlayDirect = onPlay,
                                    onAssignToKid = onAssignSeries,
                                    showNew = true,
                                    showAssign = canEditWhitelist
                                )
                                else -> {}
                            }
                        }
                    }
                    // Second row: 2025 + 2024 (new to old)
                    if (topYearsRow.isNotEmpty()) {
                        item { Text("2025–2024", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                        item {
                            when (selectedTab) {
                                ContentTab.Vod -> com.chris.m3usuite.ui.components.rows.VodRow(
                                    items = topYearsRow,
                                    stateKey = "library:${selectedTabKey}:years",
                                    onOpenDetails = onOpen,
                                    onPlayDirect = onPlay,
                                    onAssignToKid = onAssignVod,
                                    showAssign = canEditWhitelist
                                )
                                ContentTab.Series -> com.chris.m3usuite.ui.components.rows.SeriesRow(
                                    items = topYearsRow,
                                    stateKey = "library:${selectedTabKey}:years",
                                    onOpenDetails = onOpen,
                                    onPlayDirect = onPlay,
                                    onAssignToKid = onAssignSeries,
                                    showAssign = canEditWhitelist
                                )
                                else -> {}
                            }
                        }
                    }
                }
                // Live: Kategorien (aus API), keine Provider/Genre-Buckets
                if (selectedTab == ContentTab.Live && query.text.isBlank() && groupKeys.categories.isNotEmpty()) {
                    val labelById = runCatching { repo.categories("live").associateBy({ it.categoryId }, { it.categoryName }) }.getOrElse { emptyMap() }
                    fun chipKeyForLiveCategory(label: String): String {
                        val s = label.lowercase()
                        return when {
                            Regex("^\\s*for \\badults\\b").containsMatchIn(s) -> "adult"
                            Regex("\\bsport|dazn|bundesliga|uefa|magenta|sky sport").containsMatchIn(s) -> "action" // use energetic style
                            Regex("\\bnews|nachricht|cnn|bbc|al jazeera").containsMatchIn(s) -> "documentary"
                            Regex("\\bdoku|docu|documentary|history|discovery|nat geo").containsMatchIn(s) -> "documentary"
                            Regex("\\bkids|kinder|nick|kika|disney channel").containsMatchIn(s) -> "kids"
                            Regex("\\bmusic|musik|radio").containsMatchIn(s) -> "show"
                            Regex("\\bcinema|filmreihe|kino").containsMatchIn(s) -> "collection"
                            else -> "other"
                        }
                    }
                    item {
                        Text("Live – Kategorien", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                    items(groupKeys.categories, key = { it }) { catId ->
                        val sectionKey = "library:${selectedTabKey}:category:$catId"
                        val label = (labelById[catId] ?: catId).trim()
                        val chipKey = chipKeyForLiveCategory(label)
                        ExpandableGroupSection(
                            tab = selectedTab,
                            stateKey = sectionKey,
                            refreshSignal = cacheVersion + resumeTick,
                            groupLabel = { label },
                            chipKey = chipKey,
                            groupIcon = { com.chris.m3usuite.ui.components.chips.CategoryChip(key = chipKey, label = label) },
                            expandedDefault = true,
                            loadItems = { loadItemsForLiveCategory(catId) },
                            onOpenDetails = onOpen,
                            onPlayDirect = onPlay,
                            onAssignToKid = null,
                            showAssign = false
                        )
                    }
                }

                // Live: Provider-Gruppen und Genres (deaktiviert – wir nutzen Kategorien)

                // Anbieter-Gruppierung (alle Tabs)
                // Top-level switch: Anbieter vs Genres (per tab)
                if (selectedTab != ContentTab.Vod && selectedTab != ContentTab.Live) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Gruppierung:")
                            val isGenre = when (selectedTab) {
                                ContentTab.Live -> groupByGenreLive
                                ContentTab.Vod -> groupByGenreVod
                                ContentTab.Series -> groupByGenreSeries
                            }
                            val onToggle: (Boolean) -> Unit = { v ->
                                scope.launch {
                                    when (selectedTab) {
                                        ContentTab.Live -> store.setLibGroupByGenreLive(v)
                                        ContentTab.Vod -> store.setLibGroupByGenreVod(v)
                                        ContentTab.Series -> store.setLibGroupByGenreSeries(v)
                                    }
                                }
                            }
                            androidx.compose.material3.FilterChip(
                                selected = !isGenre,
                                onClick = { onToggle(false) },
                                label = { Text("Anbieter") }
                            )
                            androidx.compose.material3.FilterChip(
                                selected = isGenre,
                                onClick = { onToggle(true) },
                                label = { Text("Genres") }
                            )
                        }
                    }
                }

                val showGenres = when (selectedTab) {
                    ContentTab.Live -> groupByGenreLive
                    ContentTab.Vod -> groupByGenreVod
                    ContentTab.Series -> groupByGenreSeries
                }

                if (selectedTab == ContentTab.Vod && query.text.isBlank()) {
                    // Curated VOD rows (provider rows suppressed as requested)
                    // 1) Genres & Themen (alphabetical by label)
                    val curated = run {
                        val base = listOf(
                            "adventure","action","anime","bollywood","classic","documentary","drama","family","fantasy","horror",
                            "kids","comedy","war","martial_arts","romance","sci_fi","show","thriller","christmas","western"
                        )
                        // keep only available keys
                        val avail = groupKeys.genres.toSet()
                        base.filter { it in avail }
                            .sortedBy { com.chris.m3usuite.core.util.CategoryNormalizer.displayLabel(it) }
                    }
                    if (curated.isNotEmpty()) {
                        item {
                            Text("Genres & Themen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                        items(curated, key = { it }) { key ->
                            val sectionKey = "library:${selectedTabKey}:curated:$key"
                            val label = com.chris.m3usuite.core.util.CategoryNormalizer.displayLabel(key)
                            ExpandableGroupSection(
                                tab = selectedTab,
                                stateKey = sectionKey,
                                refreshSignal = cacheVersion + resumeTick,
                                groupLabel = { label },
                                chipKey = key,
                                expandedDefault = true,
                                loadItems = { loadItemsForGenre(selectedTab, key) },
                                onOpenDetails = onOpen,
                                onPlayDirect = onPlay,
                                onAssignToKid = onAssignVod,
                                showAssign = canEditWhitelist
                            )
                        }
                    }

                    // No provider fallback in curated VOD view: keep the genre layout stable

                    // 2) 4K
                    if (groupKeys.genres.contains("4k")) {
                        item { Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { com.chris.m3usuite.ui.components.chips.CategoryChip(key = "4k", label = "4K") } }
                        item {
                            ExpandableGroupSection(
                                tab = selectedTab,
                                stateKey = "library:${selectedTabKey}:curated:4k",
                                refreshSignal = cacheVersion + resumeTick,
                                groupLabel = { "4K" },
                                chipKey = "4k",
                                expandedDefault = true,
                                loadItems = { loadItemsForGenre(selectedTab, "4k") },
                                onOpenDetails = onOpen,
                                onPlayDirect = onPlay,
                                onAssignToKid = onAssignVod,
                                showAssign = canEditWhitelist
                            )
                        }
                    }

                    // 3) Kollektionen
                    if (groupKeys.genres.contains("collection")) {
                        item { Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { com.chris.m3usuite.ui.components.chips.CategoryChip(key = "collection", label = "Kollektionen") } }
                        item {
                            ExpandableGroupSection(
                                tab = selectedTab,
                                stateKey = "library:${selectedTabKey}:curated:collection",
                                refreshSignal = cacheVersion + resumeTick,
                                groupLabel = { "Kollektionen" },
                                chipKey = "collection",
                                expandedDefault = true,
                                loadItems = { loadItemsForGenre(selectedTab, "collection") },
                                onOpenDetails = onOpen,
                                onPlayDirect = onPlay,
                                onAssignToKid = onAssignVod,
                                showAssign = canEditWhitelist
                            )
                        }
                    }

                    // 4) Unkategorisiert
                    if (groupKeys.genres.contains("other")) {
                        item { Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { com.chris.m3usuite.ui.components.chips.CategoryChip(key = "other", label = "Unkategorisiert") } }
                        item {
                            ExpandableGroupSection(
                                tab = selectedTab,
                                stateKey = "library:${selectedTabKey}:curated:other",
                                refreshSignal = cacheVersion + resumeTick,
                                groupLabel = { "Unkategorisiert" },
                                chipKey = "other",
                                expandedDefault = true,
                                loadItems = { loadItemsForGenre(selectedTab, "other") },
                                onOpenDetails = onOpen,
                                onPlayDirect = onPlay,
                                onAssignToKid = onAssignVod,
                                showAssign = canEditWhitelist
                            )
                        }
                    }

                    // 5) Adults at the very bottom if enabled
                    val adultProviders = groupKeys.providers.filter { it.startsWith("adult_") }
                    if (showAdultsEnabled && adultProviders.isNotEmpty()) {
                        item {
                            ExpandableAdultGroupsSection(
                                tab = selectedTab,
                                stateKey = "library:${selectedTabKey}:adults",
                                refreshSignal = cacheVersion + resumeTick,
                                providerKeys = adultProviders,
                                loadItems = { k -> loadItemsForProvider(selectedTab, k) },
                                onOpenDetails = onOpen,
                                onPlayDirect = onPlay,
                                onAssignToKid = onAssignVod,
                                showAssign = canEditWhitelist
                            )
                        }
                    }
                } else if (selectedTab != ContentTab.Live && !showGenres && groupKeys.providers.isNotEmpty()) {
                    item {
                        Text(
                            when (selectedTab) { ContentTab.Live -> "Live – Anbieter"; ContentTab.Vod -> "Filme – Anbieter"; ContentTab.Series -> "Serien – Anbieter" },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    // Sort-Toggle (nur Vod/Series)
                        item {
                            Row(Modifier.padding(horizontal = 16.dp)) {
                                val newest = if (selectedTab == ContentTab.Vod) vodSortNewest else seriesSortNewest
                                androidx.compose.material3.Text("Neueste zuerst")
                                androidx.compose.material3.Switch(
                                    checked = newest,
                                    onCheckedChange = { v ->
                                        scope.launch {
                                            if (selectedTab == ContentTab.Vod) store.setLibVodSortNewest(v) else store.setLibSeriesSortNewest(v)
                                            invalidateCaches()
                                        }
                                    }
                                )
                            }
                        }
                    val providersAll = groupKeys.providers
                    val adultProviders = providersAll.filter { it.startsWith("adult_") }
                    val normalProviders = providersAll.filterNot { it.startsWith("adult_") || it.isBlank() }

                    // Render non-adult provider groups
                    items(normalProviders, key = { it }) { key ->
                        val sectionKey = "library:${selectedTabKey}:provider:$key"
                        val displayLabel = providerLabelStore.labelFor(key)
                        ExpandableGroupSection(
                            tab = selectedTab,
                            stateKey = sectionKey,
                            refreshSignal = cacheVersion + resumeTick,
                            groupLabel = { displayLabel },
                            groupIcon = {
                                com.chris.m3usuite.ui.components.ProviderIconFor(key, displayLabel, sizeDp = 24)
                            },
                            expandedDefault = true,
                            loadItems = { loadItemsForProvider(selectedTab, key) },
                            onOpenDetails = onOpen,
                            onPlayDirect = onPlay,
                            onAssignToKid = when (selectedTab) {
                                ContentTab.Vod -> onAssignVod
                                ContentTab.Series -> onAssignSeries
                                ContentTab.Live -> null
                            },
                            showAssign = canEditWhitelist && selectedTab != ContentTab.Live
                        )
                    }

                    // Adults umbrella (only when enabled and present; VOD only)
                    if (selectedTab == ContentTab.Vod && showAdultsEnabled && adultProviders.isNotEmpty()) {
                        item {
                            ExpandableAdultGroupsSection(
                                tab = selectedTab,
                                stateKey = "library:${selectedTabKey}:adults",
                                refreshSignal = cacheVersion + resumeTick,
                                providerKeys = adultProviders,
                                loadItems = { k -> loadItemsForProvider(selectedTab, k) },
                                onOpenDetails = onOpen,
                                onPlayDirect = onPlay,
                                onAssignToKid = onAssignVod,
                                showAssign = canEditWhitelist
                            )
                        }
                    }
                }

                // Fallback-Kategorien sind global deaktiviert. Alle Inhalte sollen über Provider/Genre/Year-Buckets abgedeckt sein.

                // Jahre (nur zeigen, wenn Provider-Ansicht aktiv und Live-Tab)
                if (groupKeys.years.isNotEmpty() && selectedTab == ContentTab.Live && !showGenres) {
                    item {
                        Text(
                            when (selectedTab) {
                                ContentTab.Vod -> "Filme – Jahre"
                                ContentTab.Series -> "Serien – Jahre"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    items(groupKeys.years, key = { it }) { y ->
                        val sectionKey = "library:${selectedTabKey}:year:$y"
                        ExpandableGroupSection(
                            tab = selectedTab,
                            stateKey = sectionKey,
                            refreshSignal = cacheVersion + resumeTick,
                            groupLabel = { y.toString() },
                            expandedDefault = false,
                            loadItems = { loadItemsForYear(selectedTab, y) },
                            onOpenDetails = onOpen,
                            onPlayDirect = onPlay,
                            onAssignToKid = when (selectedTab) {
                                ContentTab.Vod -> onAssignVod
                                ContentTab.Series -> onAssignSeries
                                ContentTab.Live -> null
                            },
                            showAssign = canEditWhitelist && selectedTab != ContentTab.Live
                        )
                    }
                }

                // Genres (nur wenn Genre-Ansicht aktiv)
                if (groupKeys.genres.isNotEmpty() && showGenres && selectedTab == ContentTab.Series) {
                    item {
                        Text(
                            when (selectedTab) {
                                ContentTab.Live -> "Live – Genres"
                                ContentTab.Vod -> "Filme – Genres"
                                ContentTab.Series -> "Serien – Genres"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    items(groupKeys.genres, key = { it }) { key ->
                        val genreKey = key.ifBlank { "unknown" }
                        val sectionKey = "library:${selectedTabKey}:genre:$genreKey"
                        ExpandableGroupSection(
                            tab = selectedTab,
                            stateKey = sectionKey,
                            refreshSignal = cacheVersion + resumeTick,
                            groupLabel = { key.ifBlank { "Unbekannt" } },
                            expandedDefault = selectedTab == ContentTab.Live,
                            loadItems = { loadItemsForGenre(selectedTab, key) },
                            onOpenDetails = onOpen,
                            onPlayDirect = onPlay,
                            onAssignToKid = when (selectedTab) {
                                ContentTab.Vod -> onAssignVod
                                ContentTab.Series -> onAssignSeries
                                ContentTab.Live -> null
                            },
                            showAssign = canEditWhitelist && selectedTab != ContentTab.Live
                        )
                    }
                }
            }
        }
    }
}

/**
 * Generische, wiederverwendbare Sektion mit expand/collapse und Lazy-Loading der Items.
 * Der Row-Typ (Live/Vod/Series) wird über 'tab' bestimmt – dadurch keine doppelten UI-Bausteine.
 */
@Composable
private fun ExpandableGroupSection(
    tab: ContentTab,
    stateKey: String,
    refreshSignal: Int,
    groupLabel: () -> String,
    chipKey: String? = null,
    groupIcon: (@Composable (() -> Unit))? = null,
    expandedDefault: Boolean,
    loadItems: suspend () -> List<MediaItem>,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: ((MediaItem) -> Unit)?,
    showAssign: Boolean
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(expandedDefault) }
    var items by remember(stateKey) { mutableStateOf<List<MediaItem>>(emptyList()) }

    LaunchedEffect(stateKey, refreshSignal, expanded) {
        if (expanded) {
            try {
                items = loadItems()
            } catch (_: Throwable) {
                // keep previous list on failure
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            when {
                chipKey != null -> {
                    com.chris.m3usuite.ui.components.chips.CategoryChip(key = chipKey, label = groupLabel())
                }
                groupIcon != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) { groupIcon() }
                }
                else -> {
                    Text(groupLabel(), style = MaterialTheme.typography.titleSmall)
                }
            }
            TextButton(onClick = {
                expanded = !expanded
            }) {
                Text(if (expanded) "Weniger" else "Mehr")
            }
        }
        if (expanded) {
            MediaRowForTab(
                tab = tab,
                stateKey = stateKey,
                items = items,
                onOpenDetails = onOpenDetails,
                onPlayDirect = onPlayDirect,
                onAssignToKid = onAssignToKid,
                showAssign = showAssign
            )
        }
    }
}

@Composable
private fun ExpandableAdultGroupsSection(
    tab: ContentTab,
    stateKey: String,
    refreshSignal: Int,
    providerKeys: List<String>,
    loadItems: suspend (String) -> List<MediaItem>,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: ((MediaItem) -> Unit)?,
    showAssign: Boolean
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            com.chris.m3usuite.ui.components.chips.CategoryChip(key = "adult", label = "FOR ADULTS")
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Weniger" else "Mehr")
            }
        }
        if (expanded) {
            // Render each adult provider as its own expandable row
            providerKeys.forEach { key ->
                val sectionKey = "$stateKey:$key"
                val subLabel = remember(key) {
                    key.removePrefix("adult_").replace('_', ' ').replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
                }
                ExpandableGroupSection(
                    tab = tab,
                    stateKey = sectionKey,
                    refreshSignal = refreshSignal,
                    groupLabel = { subLabel },
                    chipKey = key,
                    expandedDefault = true,
                    loadItems = { loadItems(key) },
                    onOpenDetails = onOpenDetails,
                    onPlayDirect = onPlayDirect,
                    onAssignToKid = onAssignToKid,
                    showAssign = showAssign
                )
            }
        }
    }
}

@Composable
private fun MediaRowForTab(
    tab: ContentTab,
    stateKey: String? = null,
    items: List<MediaItem>,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: ((MediaItem) -> Unit)?,
    showAssign: Boolean
) {
    when (tab) {
        ContentTab.Live -> {
            com.chris.m3usuite.ui.components.rows.LiveRow(
                items = items,
                stateKey = stateKey,
                onOpenDetails = { m -> onOpenDetails(m) },
                onPlayDirect = { m -> onPlayDirect(m) }
            )
        }
        ContentTab.Vod -> {
            com.chris.m3usuite.ui.components.rows.VodRow(
                items = items,
                stateKey = stateKey,
                onOpenDetails = { m -> onOpenDetails(m) },
                onPlayDirect = { m -> onPlayDirect(m) },
                onAssignToKid = { m -> onAssignToKid?.invoke(m) },
                showAssign = showAssign
            )
        }
        ContentTab.Series -> {
            com.chris.m3usuite.ui.components.rows.SeriesRow(
                items = items,
                stateKey = stateKey,
                onOpenDetails = { m -> onOpenDetails(m) },
                onPlayDirect = { m -> onPlayDirect(m) },
                onAssignToKid = { m -> onAssignToKid?.invoke(m) },
                showAssign = showAssign
            )
        }
    }
}
