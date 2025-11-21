package com.chris.m3usuite.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.chris.m3usuite.core.playback.PlayUrlHelper
import com.chris.m3usuite.core.util.isAdultCategory
import com.chris.m3usuite.core.util.isAdultProvider
import com.chris.m3usuite.core.xtream.ProviderLabelStore
import com.chris.m3usuite.data.obx.toMediaItem
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.model.isAdultCategory
import com.chris.m3usuite.navigation.navigateTopLevel
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.ui.focus.OnPrefetchKeys
import com.chris.m3usuite.ui.focus.OnPrefetchPaged
import com.chris.m3usuite.ui.focus.focusScaleOnTv
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.home.LibraryNavConfig
import com.chris.m3usuite.ui.home.LibraryTab
import com.chris.m3usuite.ui.layout.FishHeaderData
import com.chris.m3usuite.ui.layout.FishHeaderHost
import com.chris.m3usuite.ui.layout.FishRow
import com.chris.m3usuite.ui.layout.FishRowPaged
import com.chris.m3usuite.ui.layout.LiveFishTile
import com.chris.m3usuite.ui.layout.SeriesFishTile
import com.chris.m3usuite.ui.layout.VodFishTile
import com.chris.m3usuite.ui.theme.CategoryFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val categories: List<String> = emptyList(),
)

@Composable
fun LibraryScreen(
    navController: NavHostController,
    openLive: (Long) -> Unit,
    openVod: (Long) -> Unit,
    openSeries: (Long) -> Unit,
    openTelegram: ((Long) -> Unit)? = null,
) {
    LaunchedEffect(Unit) {
        com.chris.m3usuite.metrics.RouteTag
            .set("browse")
        com.chris.m3usuite.core.debug.GlobalDebug
            .logTree("browse:root")
    }
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val repo =
        remember {
            com.chris.m3usuite.data.repo
                .XtreamObxRepository(ctx, store)
        }
    val mediaRepo =
        remember {
            com.chris.m3usuite.data.repo
                .MediaQueryRepository(ctx, store)
        }
    val tgRepo =
        remember {
            com.chris.m3usuite.data.repo
                .TelegramContentRepository(ctx, store)
        }
    val resumeRepo =
        remember {
            com.chris.m3usuite.data.repo
                .ResumeRepository(ctx)
        }
    val permRepo =
        remember {
            com.chris.m3usuite.data.repo
                .PermissionRepository(ctx, store)
        }
    val providerLabelStore = remember(ctx) { ProviderLabelStore.get(ctx) }
    val showAdultsEnabled by store.showAdults.collectAsStateWithLifecycle(initialValue = false)

    val scope = rememberCoroutineScope()
    // Lifecycle-aware: tick when screen resumes to force lightweight reloads
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs =
            androidx.lifecycle.LifecycleEventObserver { _, ev ->
                if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    // Bump a counter so effects keyed with resumeTick run again on return
                    resumeTick++
                }
            }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val wm = remember { WorkManager.getInstance(ctx) }

    // Centralized playback launcher for Library; opens internal player via nav and marks origin=lib for live
    val playbackLauncher =
        com.chris.m3usuite.playback.rememberPlaybackLauncher(
            onOpenInternal = { pr ->
                val encoded = PlayUrlHelper.encodeUrl(pr.url)
                val mimeArg = pr.mimeType?.let { Uri.encode(it) } ?: ""
                when (pr.type) {
                    "live" ->
                        navController.navigate(
                            "player?url=$encoded&type=live&mediaId=${pr.mediaId ?: -1}&startMs=${pr.startPositionMs ?: -1}&mime=$mimeArg&origin=lib",
                        )
                    "vod" ->
                        navController.navigate(
                            "player?url=$encoded&type=vod&mediaId=${pr.mediaId ?: -1}&startMs=${pr.startPositionMs ?: -1}&mime=$mimeArg",
                        )
                    "series" ->
                        navController.navigate(
                            "player?url=$encoded&type=series&seriesId=${pr.seriesId ?: -1}&season=${pr.season ?: -1}&episodeNum=${pr.episodeNum ?: -1}&episodeId=${pr.episodeId ?: -1}&startMs=${pr.startPositionMs ?: -1}&mime=$mimeArg",
                        )
                }
            },
        )

    // UiState for library content (combined): Loading until first load; Empty when no groups/rows; Success otherwise
    var uiState by remember { mutableStateOf<com.chris.m3usuite.ui.state.UiState<Unit>>(com.chris.m3usuite.ui.state.UiState.Loading) }

    // Tab-Auswahl synchron zur Start-Optik (BottomPanel)
    val selectedTab = rememberSelectedTab(store)
    val selectedTabKey =
        when (selectedTab) {
            ContentTab.Live -> "live"
            ContentTab.Vod -> "vod"
            ContentTab.Series -> "series"
        }
    val listState =
        com.chris.m3usuite.ui.state
            .rememberRouteListState("library:list:$selectedTab")
    var adultsExpanded by rememberSaveable(selectedTab, showAdultsEnabled) { mutableStateOf(showAdultsEnabled) }

    // Rechte (Whitelist-Editing)
    var canEditWhitelist by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { canEditWhitelist = permRepo.current().canEditWhitelist }

    // Ensure provider/genre/year keys are backfilled/corrected at least once
    LaunchedEffect(Unit) {
        runCatching {
            com.chris.m3usuite.work.ObxKeyBackfillWorker
                .scheduleOnce(ctx)
        }
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

    // Telegram content
    var telegramContent by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val tgEnabled by store.tgEnabled.collectAsStateWithLifecycle(initialValue = false)

    var liveCategoryLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(selectedTab, groupKeys) {
        if (selectedTab == ContentTab.Live && groupKeys.categories.isNotEmpty()) {
            liveCategoryLabels =
                runCatching {
                    withContext(Dispatchers.IO) {
                        repo
                            .categories("live")
                            .mapNotNull { cat ->
                                val id = cat.categoryId ?: return@mapNotNull null
                                val label = cat.categoryName?.trim().takeUnless { it.isNullOrEmpty() } ?: id
                                id to label
                            }.filterNot { (id, label) -> !showAdultsEnabled && isAdultCategory(id, label) }
                            .toMap()
                    }
                }.getOrElse { emptyMap() }
        } else {
            liveCategoryLabels = emptyMap()
        }
    }

    // Helpers: adults filter + kid/guest whitelist
    suspend fun withoutAdultsFor(
        tab: ContentTab,
        items: List<MediaItem>,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val show = store.showAdults.first()
            if (show) return@withContext items
            val kind =
                when (tab) {
                    ContentTab.Live -> "live"
                    ContentTab.Vod -> "vod"
                    ContentTab.Series -> "series"
                }
            val labelById =
                runCatching {
                    repo
                        .categories(
                            kind,
                        ).associateBy({ it.categoryId }, { it.categoryName })
                }.getOrElse { emptyMap() }
            items
                .map { mi ->
                    if (mi.categoryName == null && mi.categoryId != null) mi.copy(categoryName = labelById[mi.categoryId]) else mi
                }.filterNot { it.isAdultCategory() }
        }

    // Always exclude Adults, regardless of settings (use for non‑adult rows)
    suspend fun excludeAdultsAlways(
        tab: ContentTab,
        items: List<MediaItem>,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val kind =
                when (tab) {
                    ContentTab.Live -> "live"
                    ContentTab.Vod -> "vod"
                    ContentTab.Series -> "series"
                }
            val labelById =
                runCatching {
                    repo
                        .categories(
                            kind,
                        ).associateBy({ it.categoryId }, { it.categoryName })
                }.getOrElse { emptyMap() }
            items
                .map { mi ->
                    if (mi.categoryName == null && mi.categoryId != null) mi.copy(categoryName = labelById[mi.categoryId]) else mi
                }.filterNot { it.isAdultCategory() }
        }

    suspend fun allowedOnly(
        tab: ContentTab,
        items: List<MediaItem>,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val type =
                when (tab) {
                    ContentTab.Live -> "live"
                    ContentTab.Vod -> "vod"
                    ContentTab.Series -> "series"
                }
            items.filter { mediaRepo.isAllowed(type, it.id) }
        }

    suspend fun loadRecentForTab(tab: ContentTab): List<MediaItem> =
        withContext(Dispatchers.IO) {
            when (tab) {
                ContentTab.Vod -> {
                    val obx =
                        com.chris.m3usuite.data.obx.ObxStore
                            .get(ctx)
                    val box = obx.boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)
                    val marks = resumeRepo.recentVod(60)
                    val items =
                        marks.mapNotNull { mark ->
                            val vodId = (mark.mediaId - 2_000_000_000_000L).toInt()
                            val row =
                                box
                                    .query(
                                        com.chris.m3usuite.data.obx.ObxVod_.vodId
                                            .equal(vodId.toLong()),
                                    ).build()
                                    .findFirst()
                            row?.toMediaItem(ctx)
                        }
                    // filter allowed for kid profiles + adults toggle
                    withoutAdultsFor(ContentTab.Vod, items.filter { mediaRepo.isAllowed("vod", it.id) })
                }
                ContentTab.Series -> {
                    val obx =
                        com.chris.m3usuite.data.obx.ObxStore
                            .get(ctx)
                    val box = obx.boxFor(com.chris.m3usuite.data.obx.ObxSeries::class.java)
                    val marks = resumeRepo.recentEpisodes(60)
                    val seriesItems =
                        marks
                            .mapNotNull { mk ->
                                val row =
                                    box
                                        .query(
                                            com.chris.m3usuite.data.obx.ObxSeries_.seriesId
                                                .equal(mk.seriesId.toLong()),
                                        ).build()
                                        .findFirst()
                                row?.toMediaItem(ctx)
                            }.distinctBy { it.id }
                    withoutAdultsFor(ContentTab.Series, seriesItems.filter { mediaRepo.isAllowed("series", it.id) })
                }
                else -> emptyList()
            }
        }

    suspend fun loadNewestForTab(tab: ContentTab): List<MediaItem> =
        withContext(Dispatchers.IO) {
            when (tab) {
                ContentTab.Vod ->
                    excludeAdultsAlways(
                        ContentTab.Vod,
                        repo
                            .vodPagedNewest(0, 120)
                            .map {
                                it.toMediaItem(ctx)
                            }.filter { mediaRepo.isAllowed("vod", it.id) },
                    )
                ContentTab.Series ->
                    withoutAdultsFor(
                        ContentTab.Series,
                        repo
                            .seriesPagedNewest(0, 120)
                            .map {
                                it.toMediaItem(ctx)
                            }.filter { mediaRepo.isAllowed("series", it.id) },
                    )
                else -> emptyList()
            }
        }

    // Helper: Laden flacher Treffer (Suche)
    suspend fun loadFlat(
        tab: ContentTab,
        q: String,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
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
    suspend fun loadGroupKeys(tab: ContentTab): GroupKeys =
        withContext(Dispatchers.IO) {
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
                        return m.groupValues[1]
                            .replace("-", "")
                            .trim()
                            .takeIf { it.isNotBlank() }
                    }
                    val allowed = store.seedPrefixesSet()
                    val liveCats =
                        repo
                            .categories("live")
                            .filterNot { row -> !showAdultsEnabled && isAdultCategory(row.categoryId, row.categoryName) }
                    val catIds =
                        liveCats
                            .filter { row -> allowed.contains(extractPrefix(row.categoryName)) }
                            .mapNotNull { it.categoryId }
                    GroupKeys(
                        providers = emptyList(),
                        genres = emptyList(),
                        years = emptyList(),
                        categories = catIds,
                    )
                }
                ContentTab.Vod -> {
                    val showA = store.showAdults.first()
                    val providers = repo.indexProviderKeys("vod").filter { showA || !isAdultProvider(it) }
                    val genres = repo.indexGenreKeys("vod").filter { showA || !isAdultProvider(it) }
                    GroupKeys(
                        providers = providers,
                        genres = genres,
                        years = repo.indexYearKeys("vod"),
                        categories = emptyList(),
                    )
                }
                ContentTab.Series -> {
                    val showA = store.showAdults.first()
                    val providers = repo.indexProviderKeys("series").filter { showA || !isAdultProvider(it) }
                    val genres = repo.indexGenreKeys("series").filter { showA || !isAdultProvider(it) }
                    GroupKeys(
                        providers = providers,
                        genres = genres,
                        years = repo.indexYearKeys("series"),
                        categories = emptyList(),
                    )
                }
            }
        }

    // Helper: Items für einen Gruppenschlüssel laden (mit Cache)
    suspend fun loadItemsForProvider(
        tab: ContentTab,
        key: String,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            providerCache[key] ?: run {
                val itemsRaw =
                    when (tab) {
                        ContentTab.Live -> repo.liveByProviderKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) }
                        ContentTab.Vod -> {
                            val list =
                                if (vodSortNewest) {
                                    repo.vodByProviderKeyNewest(
                                        key,
                                        0,
                                        120,
                                    )
                                } else {
                                    repo.vodByProviderKeyPaged(key, 0, 120)
                                }
                            list.map { it.toMediaItem(ctx) }
                        }
                        ContentTab.Series -> {
                            val list =
                                if (seriesSortNewest) {
                                    repo.seriesByProviderKeyNewest(
                                        key,
                                        0,
                                        120,
                                    )
                                } else {
                                    repo.seriesByProviderKeyPaged(key, 0, 120)
                                }
                            list.map { it.toMediaItem(ctx) }
                        }
                    }
                val items = allowedOnly(tab, withoutAdultsFor(tab, itemsRaw))
                providerCache[key] = items
                items
            }
        }

    suspend fun loadItemsForGenre(
        tab: ContentTab,
        key: String,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            genreCache[key] ?: run {
                val itemsRaw =
                    when (tab) {
                        ContentTab.Live -> repo.liveByGenreKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) }
                        ContentTab.Vod -> repo.vodByGenreKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) }
                        ContentTab.Series -> repo.seriesByGenreKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) }
                    }
                val items =
                    allowedOnly(
                        tab,
                        if (tab ==
                            ContentTab.Vod
                        ) {
                            excludeAdultsAlways(tab, itemsRaw)
                        } else {
                            withoutAdultsFor(tab, itemsRaw)
                        },
                    )
                genreCache[key] = items
                items
            }
        }

    suspend fun loadItemsForYear(
        tab: ContentTab,
        y: Int,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            yearCache[y] ?: run {
                val itemsRaw =
                    when (tab) {
                        ContentTab.Vod -> repo.vodByYearKeyPaged(y, 0, 120).map { it.toMediaItem(ctx) }
                        ContentTab.Series -> repo.seriesByYearKeyPaged(y, 0, 120).map { it.toMediaItem(ctx) }
                        ContentTab.Live -> emptyList()
                    }
                val items =
                    allowedOnly(
                        tab,
                        if (tab ==
                            ContentTab.Vod
                        ) {
                            excludeAdultsAlways(tab, itemsRaw)
                        } else {
                            withoutAdultsFor(tab, itemsRaw)
                        },
                    )
                yearCache[y] = items
                items
            }
        }

    suspend fun loadItemsForLiveCategory(catId: String): List<MediaItem> =
        withContext(Dispatchers.IO) {
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
        uiState = com.chris.m3usuite.ui.state.UiState.Loading
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
            val yearsRow =
                when (selectedTab) {
                    ContentTab.Vod -> {
                        val rows =
                            withContext(
                                Dispatchers.IO,
                            ) { repo.vodByYearsNewest(intArrayOf(2025, 2024), 0, 180).map { it.toMediaItem(ctx) } }
                        withoutAdultsFor(ContentTab.Vod, rows.filter { mediaRepo.isAllowed("vod", it.id) })
                    }
                    ContentTab.Series -> {
                        val rows =
                            withContext(
                                Dispatchers.IO,
                            ) { repo.seriesByYearsNewest(intArrayOf(2025, 2024), 0, 180).map { it.toMediaItem(ctx) } }
                        withoutAdultsFor(ContentTab.Series, rows.filter { mediaRepo.isAllowed("series", it.id) })
                    }
                    else -> emptyList()
                }
            if (yearsRow != topYearsRow) {
                topYearsRow = yearsRow
            }

            run {
                val hasGroups =
                    groupKeys.providers.isNotEmpty() ||
                        groupKeys.genres.isNotEmpty() ||
                        groupKeys.years.isNotEmpty() ||
                        groupKeys.categories.isNotEmpty()
                val hasRows = recentRow.isNotEmpty() || newestRow.isNotEmpty() || topYearsRow.isNotEmpty()
                val hasTelegram = tgEnabled && telegramContent.isNotEmpty()
                uiState =
                    if (hasGroups ||
                        hasRows ||
                        hasTelegram
                    ) {
                        com.chris.m3usuite.ui.state.UiState
                            .Success(Unit)
                    } else {
                        com.chris.m3usuite.ui.state.UiState.Empty
                    }
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
                        val yearsRow =
                            withContext(
                                Dispatchers.IO,
                            ) { repo.vodByYearsNewest(intArrayOf(2025, 2024), 0, 180).map { it.toMediaItem(ctx) } }
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
                        val yearsRow =
                            withContext(
                                Dispatchers.IO,
                            ) { repo.seriesByYearsNewest(intArrayOf(2025, 2024), 0, 180).map { it.toMediaItem(ctx) } }
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
        wm
            .getWorkInfosForUniqueWorkLiveData("xtream_delta_import_once")
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

    // Load Telegram content when enabled
    LaunchedEffect(tgEnabled, resumeTick) {
        if (tgEnabled) {
            tgRepo.getAllTelegramContent().collect { items ->
                telegramContent = items
            }
        } else {
            telegramContent = emptyList()
        }
    }

    // Routing-Handler je Tab
    val onOpen: (MediaItem) -> Unit =
        when (selectedTab) {
            ContentTab.Live -> { m -> openLive(m.id) }
            ContentTab.Vod -> { m -> openVod(m.id) }
            ContentTab.Series -> { m -> openSeries(m.id) }
        }
    val onPlay: (MediaItem) -> Unit =
        remember(selectedTab, scope, ctx, store, navController) {
            when (selectedTab) {
                ContentTab.Live -> { media ->
                    scope.launch {
                        val req = PlayUrlHelper.forLive(ctx, store, media)
                        if (req == null) {
                            onOpen(media)
                            return@launch
                        }
                        playbackLauncher.launch(
                            com.chris.m3usuite.playback.PlayRequest(
                                type = "live",
                                mediaId = media.id,
                                url = req.url,
                                headers = req.headers,
                                mimeType = req.mimeType,
                                title = media.name,
                            ),
                        )
                    }
                }
                ContentTab.Vod -> { media ->
                    scope.launch {
                        val req = PlayUrlHelper.forVod(ctx, store, media)
                        if (req == null) {
                            onOpen(media)
                            return@launch
                        }
                        playbackLauncher.launch(
                            com.chris.m3usuite.playback.PlayRequest(
                                type = "vod",
                                mediaId = media.id,
                                url = req.url,
                                headers = req.headers,
                                mimeType = req.mimeType,
                                title = media.name,
                            ),
                        )
                    }
                }
                ContentTab.Series -> { media -> onOpen(media) }
            }
        }

    // Kid-Whitelist-Zuweisung (nur Vod/Series)
    val onAssignVod: (MediaItem) -> Unit =
        remember(canEditWhitelist) {
            { mi ->
                if (!canEditWhitelist) return@remember
                scope.launch(Dispatchers.IO) {
                    val kids =
                        com.chris.m3usuite.data.repo
                            .ProfileObxRepository(ctx)
                            .all()
                            .filter { it.type == "kid" }
                    val kRepo =
                        com.chris.m3usuite.data.repo
                            .KidContentRepository(ctx)
                    kids.forEach { kRepo.allow(it.id, "vod", mi.id) }
                }
            }
        }
    val onAssignSeries: (MediaItem) -> Unit =
        remember(canEditWhitelist) {
            { mi ->
                if (!canEditWhitelist) return@remember
                scope.launch(Dispatchers.IO) {
                    val kids =
                        com.chris.m3usuite.data.repo
                            .ProfileObxRepository(ctx)
                            .all()
                            .filter { it.type == "kid" }
                    val kRepo =
                        com.chris.m3usuite.data.repo
                            .KidContentRepository(ctx)
                    kids.forEach { kRepo.allow(it.id, "series", mi.id) }
                }
            }
        }

    val navigateToSettings =
        remember(navController) {
            {
                val current = navController.currentBackStackEntry?.destination?.route
                if (current != "settings") {
                    navController.navigate("settings") { launchSingleTop = true }
                }
            }
        }

    FishHeaderHost(modifier = Modifier.fillMaxSize()) {
        HomeChromeScaffold(
            title = "Bibliothek",
            onSettings = navigateToSettings,
            onSearch = { navController.navigateTopLevel("library?qs=show") },
            onProfiles = null,
            listState = listState,
            onLogo = {
                val current = navController.currentBackStackEntry?.destination?.route
                if (current != "library?q={q}&qs={qs}") {
                    navController.navigateTopLevel("library?q=&qs=")
                }
            },
            libraryNav =
                LibraryNavConfig(
                    selected =
                        when (selectedTab) {
                            ContentTab.Live -> LibraryTab.Live
                            ContentTab.Vod -> LibraryTab.Vod
                            ContentTab.Series -> LibraryTab.Series
                        },
                    onSelect = { tab ->
                        val idx =
                            when (tab) {
                                LibraryTab.Live -> 0
                                LibraryTab.Vod -> 1
                                LibraryTab.Series -> 2
                            }
                        scope.launch { store.setLibraryTabIndex(idx) }
                    },
                ),
        ) { pads ->
            run {
                when (val s = uiState) {
                    is com.chris.m3usuite.ui.state.UiState.Loading -> {
                        com.chris.m3usuite.ui.state
                            .LoadingState()
                        return@HomeChromeScaffold
                    }
                    is com.chris.m3usuite.ui.state.UiState.Empty -> {
                        com.chris.m3usuite.ui.state
                            .EmptyState()
                        return@HomeChromeScaffold
                    }
                    is com.chris.m3usuite.ui.state.UiState.Error -> {
                        com.chris.m3usuite.ui.state
                            .ErrorState(s.message, s.retry)
                        return@HomeChromeScaffold
                    }
                    is com.chris.m3usuite.ui.state.UiState.Success -> Unit
                }
            }

            val stateHolder = rememberSaveableStateHolder()
            stateHolder.SaveableStateProvider(key = "library/tab/$selectedTabKey") {
                Box(modifier = Modifier.fillMaxSize()) {
                    com.chris.m3usuite.ui.fx.FishBackground(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                        alpha = 0.06f,
                    )

                    LazyColumn(
                        state = listState,
                        flingBehavior =
                            androidx.compose.foundation.gestures.snapping
                                .rememberSnapFlingBehavior(listState),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(pads),
                    ) {
                        // Globale Suche per Header – lokales Suchfeld entfernt

                        // Suchergebnisse (ein Row pro aktivem Tab) – Paging + UiState gate
                        if (query.text.isNotBlank()) {
                            val searchStateKey = "library:$selectedTabKey:search"
                            item {
                                val searchHeader =
                                    when (selectedTab) {
                                        ContentTab.Live ->
                                            FishHeaderData.Text(
                                                anchorKey = searchStateKey,
                                                text = "Live – Suchtreffer",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                            )
                                        ContentTab.Vod ->
                                            FishHeaderData.Text(
                                                anchorKey = searchStateKey,
                                                text = "Filme – Suchtreffer",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                            )
                                        ContentTab.Series ->
                                            FishHeaderData.Text(
                                                anchorKey = searchStateKey,
                                                text = "Serien – Suchtreffer",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                            )
                                    }
                                val flow =
                                    remember(selectedTab, query.text) {
                                        val kind =
                                            when (selectedTab) {
                                                ContentTab.Live -> "live"
                                                ContentTab.Vod -> "vod"
                                                ContentTab.Series -> "series"
                                            }
                                        mediaRepo.pagingSearchFilteredFlow(kind, query.text)
                                    }
                                val itemsPaged = flow.collectAsLazyPagingItems()
                                val countFlow =
                                    remember(itemsPaged) {
                                        com.chris.m3usuite.ui.state
                                            .combinedPagingCountFlow(itemsPaged)
                                    }
                                val searchUi by com.chris.m3usuite.ui.state
                                    .collectAsUiState(countFlow) { total -> total == 0 }
                                when (val s = searchUi) {
                                    is com.chris.m3usuite.ui.state.UiState.Loading -> {
                                        com.chris.m3usuite.ui.state
                                            .LoadingState(Modifier.padding(24.dp))
                                    }
                                    is com.chris.m3usuite.ui.state.UiState.Empty -> {
                                        com.chris.m3usuite.ui.state
                                            .EmptyState(modifier = Modifier.padding(24.dp))
                                    }
                                    is com.chris.m3usuite.ui.state.UiState.Error -> {
                                        com.chris.m3usuite.ui.state
                                            .ErrorState(text = s.message, onRetry = { itemsPaged.retry() })
                                    }
                                    is com.chris.m3usuite.ui.state.UiState.Success -> {
                                        val allowAssign = selectedTab != ContentTab.Live && canEditWhitelist
                                        val onPrefetchPaged: OnPrefetchPaged? =
                                            if (selectedTab == ContentTab.Live) {
                                                { indices, lp ->
                                                    val count = lp.itemCount
                                                    if (count > 0) {
                                                        val sids =
                                                            indices
                                                                .filter { it in 0 until count }
                                                                .mapNotNull { idx -> lp.peek(idx)?.takeIf { it?.type == "live" }?.streamId }
                                                        if (sids.isNotEmpty()) {
                                                            repo.prefetchEpgForVisible(sids, perStreamLimit = 2, parallelism = 4)
                                                        }
                                                    }
                                                }
                                            } else {
                                                null
                                            }
                                        libraryMediaRowPaged(
                                            tab = selectedTab,
                                            items = itemsPaged,
                                            stateKey = searchStateKey,
                                            edgeLeftExpandChrome = true,
                                            header = searchHeader,
                                            allowAssign = allowAssign,
                                            onPrefetchPaged = onPrefetchPaged,
                                            onOpenDetails = onOpen,
                                            onPlayDirect = onPlay,
                                            onAssignToKid =
                                                when (selectedTab) {
                                                    ContentTab.Vod -> onAssignVod
                                                    ContentTab.Series -> onAssignSeries
                                                    ContentTab.Live -> null
                                                },
                                        )
                                    }
                                }
                            }
                        }

                        // Gruppenansichten (nur wenn keine Suche aktiv)
                        if (query.text.isBlank()) {
                            // Provider
                            if (selectedTab != ContentTab.Live) {
                                // Top rows for VOD/Series: Zuletzt gesehen, Neu
                                if (recentRow.isNotEmpty()) {
                                    item {
                                        val headerRecent =
                                            when (selectedTab) {
                                                ContentTab.Vod ->
                                                    FishHeaderData.Chip(
                                                        anchorKey = "library:$selectedTabKey:recent",
                                                        label = "Zuletzt gespielt",
                                                    )
                                                ContentTab.Series ->
                                                    FishHeaderData.Text(
                                                        anchorKey = "library:$selectedTabKey:recent",
                                                        text = "Zuletzt gesehen – Serien",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = Color.White,
                                                    )
                                                ContentTab.Live ->
                                                    FishHeaderData.Text(
                                                        anchorKey = "library:$selectedTabKey:recent",
                                                        text = "LiveTV – Zuletzt genutzt",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = Color.White,
                                                    )
                                            }
                                        val allowAssign = selectedTab != ContentTab.Live && canEditWhitelist
                                        libraryMediaRow(
                                            tab = selectedTab,
                                            items = recentRow,
                                            stateKey = "library:$selectedTabKey:recent",
                                            edgeLeftExpandChrome = true,
                                            initialFocusEligible = true,
                                            allowAssign = allowAssign,
                                            header = headerRecent,
                                            onOpenDetails = onOpen,
                                            onPlayDirect = onPlay,
                                            onAssignToKid =
                                                when (selectedTab) {
                                                    ContentTab.Vod -> onAssignVod
                                                    ContentTab.Series -> onAssignSeries
                                                    ContentTab.Live -> null
                                                },
                                        )
                                    }
                                }
                                if (newestRow.isNotEmpty()) {
                                    val labelNew = if (selectedTab == ContentTab.Vod) "Neu – Aktuell" else "Neu"
                                    val highlightIds = newestRow.map { it.id }.toSet()
                                    item {
                                        val headerNew =
                                            when (selectedTab) {
                                                ContentTab.Vod ->
                                                    FishHeaderData.Chip(
                                                        anchorKey = "library:$selectedTabKey:newest",
                                                        label = labelNew,
                                                    )
                                                ContentTab.Series ->
                                                    FishHeaderData.Text(
                                                        anchorKey = "library:$selectedTabKey:newest",
                                                        text = labelNew,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = Color.White,
                                                    )
                                                ContentTab.Live ->
                                                    FishHeaderData.Text(
                                                        anchorKey = "library:$selectedTabKey:newest",
                                                        text = labelNew,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = Color.White,
                                                    )
                                            }
                                        val allowAssign = selectedTab != ContentTab.Live && canEditWhitelist
                                        libraryMediaRow(
                                            tab = selectedTab,
                                            items = newestRow,
                                            stateKey = "library:$selectedTabKey:newest",
                                            edgeLeftExpandChrome = true,
                                            initialFocusEligible = recentRow.isEmpty(),
                                            allowAssign = allowAssign,
                                            header = headerNew,
                                            highlightIds = highlightIds,
                                            onOpenDetails = onOpen,
                                            onPlayDirect = onPlay,
                                            onAssignToKid =
                                                when (selectedTab) {
                                                    ContentTab.Vod -> onAssignVod
                                                    ContentTab.Series -> onAssignSeries
                                                    ContentTab.Live -> null
                                                },
                                        )
                                    }
                                }
                                // Second row: 2025 + 2024 (new to old)
                                if (topYearsRow.isNotEmpty()) {
                                    item {
                                        val headerYears =
                                            when (selectedTab) {
                                                ContentTab.Vod ->
                                                    FishHeaderData.Chip(
                                                        anchorKey = "library:$selectedTabKey:years",
                                                        label = "2025–2024",
                                                    )
                                                else ->
                                                    FishHeaderData.Text(
                                                        anchorKey = "library:$selectedTabKey:years",
                                                        text = "2025–2024",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = Color.White,
                                                    )
                                            }
                                        val allowAssign = selectedTab != ContentTab.Live && canEditWhitelist
                                        libraryMediaRow(
                                            tab = selectedTab,
                                            items = topYearsRow,
                                            stateKey = "library:$selectedTabKey:years",
                                            edgeLeftExpandChrome = true,
                                            initialFocusEligible = recentRow.isEmpty() && newestRow.isEmpty(),
                                            allowAssign = allowAssign,
                                            header = headerYears,
                                            onOpenDetails = onOpen,
                                            onPlayDirect = onPlay,
                                            onAssignToKid =
                                                when (selectedTab) {
                                                    ContentTab.Vod -> onAssignVod
                                                    ContentTab.Series -> onAssignSeries
                                                    ContentTab.Live -> null
                                                },
                                        )
                                    }
                                }
                            }

                            // Telegram content row (when enabled and available)
                            if (tgEnabled && telegramContent.isNotEmpty() && selectedTab == ContentTab.Vod) {
                                item {
                                    val onTelegramClick: (MediaItem) -> Unit = { media ->
                                        // Navigate to Telegram detail screen instead of playing directly
                                        if (openTelegram != null) {
                                            openTelegram(media.id)
                                        } else {
                                            // Fallback: play directly if no detail screen handler is provided
                                            scope.launch {
                                                TelegramLogRepository.info(
                                                    source = "LibraryScreen",
                                                    message = "User started Telegram playback from LibraryScreen",
                                                    details =
                                                        mapOf(
                                                            "mediaId" to media.id.toString(),
                                                            "title" to media.name,
                                                            "playUrl" to (media.url ?: "null"),
                                                        ),
                                                )

                                                playbackLauncher.launch(
                                                    com.chris.m3usuite.playback.PlayRequest(
                                                        type = "vod",
                                                        mediaId = media.id,
                                                        url = media.url ?: "",
                                                        headers = emptyMap(),
                                                        mimeType = null,
                                                        title = media.name,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                    com.chris.m3usuite.ui.layout.FishTelegramRow(
                                        items = telegramContent.take(120),
                                        stateKey = "library:$selectedTabKey:telegram",
                                        title = "Telegram",
                                        modifier = Modifier,
                                        onItemClick = onTelegramClick,
                                    )
                                }
                            }
                            // Live: Kategorien (aus API), keine Provider/Genre-Buckets
                            if (selectedTab == ContentTab.Live && query.text.isBlank() && groupKeys.categories.isNotEmpty()) {
                                val labelById = liveCategoryLabels

                                fun chipKeyForLiveCategory(label: String): String {
                                    val s = label.lowercase()
                                    return when {
                                        Regex("^\\s*for \\badults\\b").containsMatchIn(s) -> "adult"
                                        Regex("\\bsport|dazn|bundesliga|uefa|magenta|sky sport").containsMatchIn(s) -> "action"
                                        Regex("\\bnews|nachricht|cnn|bbc|al jazeera").containsMatchIn(s) -> "documentary"
                                        Regex("\\bdoku|docu|documentary|history|discovery|nat geo").containsMatchIn(s) -> "documentary"
                                        Regex("\\bkids|kinder|nick|kika|disney channel").containsMatchIn(s) -> "kids"
                                        Regex("\\bmusic|musik|radio").containsMatchIn(s) -> "show"
                                        Regex("\\bcinema|filmreihe|kino").containsMatchIn(s) -> "collection"
                                        else -> "other"
                                    }
                                }
                                items(groupKeys.categories, key = { it }) { catId ->
                                    val sectionKey = "library:$selectedTabKey:category:$catId"
                                    val label = (labelById[catId] ?: catId).trim()
                                    chipKeyForLiveCategory(label)
                                    val headerData =
                                        FishHeaderData.Chip(
                                            anchorKey = sectionKey,
                                            label = label,
                                        )
                                    ExpandableGroupSection(
                                        tab = selectedTab,
                                        stateKey = sectionKey,
                                        refreshSignal = cacheVersion + resumeTick,
                                        header = headerData,
                                        expandedDefault = true,
                                        loadItems = { loadItemsForLiveCategory(catId) },
                                        onOpenDetails = onOpen,
                                        onPlayDirect = onPlay,
                                        onAssignToKid = null,
                                        showAssign = false,
                                    )
                                }
                            }

                            val providersAll = groupKeys.providers
                            val adultProviders = providersAll.filter { it.startsWith("adult_") }

                            if (selectedTab != ContentTab.Live && (selectedTab == ContentTab.Series || selectedTab == ContentTab.Vod)) {
                                item {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        val newest = if (selectedTab == ContentTab.Vod) vodSortNewest else seriesSortNewest
                                        Text(
                                            "Neueste zuerst",
                                            style =
                                                MaterialTheme.typography.labelLarge.copy(
                                                    fontFamily = CategoryFonts.Cinzel,
                                                    fontWeight = FontWeight.SemiBold,
                                                    letterSpacing = 0.6.sp,
                                                ),
                                            color = Color.White,
                                        )
                                        androidx.compose.material3.Switch(
                                            checked = newest,
                                            onCheckedChange = { v ->
                                                scope.launch {
                                                    if (selectedTab == ContentTab.Vod) {
                                                        store.setLibVodSortNewest(v)
                                                    } else {
                                                        store.setLibSeriesSortNewest(v)
                                                    }
                                                    invalidateCaches()
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            val showGenres =
                                when (selectedTab) {
                                    ContentTab.Live -> groupByGenreLive
                                    ContentTab.Vod -> groupByGenreVod
                                    ContentTab.Series -> groupByGenreSeries
                                }

                            if (selectedTab == ContentTab.Vod && query.text.isBlank()) {
                                // Curated VOD rows (provider rows suppressed as requested)
                                // 1) Genres & Themen (alphabetical by label)
                                val curated =
                                    run {
                                        val base =
                                            listOf(
                                                "adventure",
                                                "action",
                                                "anime",
                                                "bollywood",
                                                "classic",
                                                "documentary",
                                                "drama",
                                                "family",
                                                "fantasy",
                                                "horror",
                                                "kids",
                                                "comedy",
                                                "war",
                                                "martial_arts",
                                                "romance",
                                                "sci_fi",
                                                "show",
                                                "thriller",
                                                "christmas",
                                                "western",
                                            )
                                        // keep only available keys
                                        val avail = groupKeys.genres.toSet()
                                        base
                                            .filter { it in avail }
                                            .sortedBy {
                                                com.chris.m3usuite.core.util.CategoryNormalizer
                                                    .displayLabel(it)
                                            }
                                    }
                                if (curated.isNotEmpty()) {
                                    items(curated, key = { it }) { key ->
                                        val sectionKey = "library:$selectedTabKey:curated:$key"
                                        val label =
                                            com.chris.m3usuite.core.util.CategoryNormalizer
                                                .displayLabel(key)
                                        val headerData =
                                            FishHeaderData.Chip(
                                                anchorKey = sectionKey,
                                                label = label,
                                            )
                                        ExpandableGroupSection(
                                            tab = selectedTab,
                                            stateKey = sectionKey,
                                            refreshSignal = cacheVersion + resumeTick,
                                            header = headerData,
                                            expandedDefault = true,
                                            loadItems = { loadItemsForGenre(selectedTab, key) },
                                            onOpenDetails = onOpen,
                                            onPlayDirect = onPlay,
                                            onAssignToKid = onAssignVod,
                                            showAssign = canEditWhitelist,
                                        )
                                    }
                                }

                                // No provider fallback in curated VOD view: keep the genre layout stable

                                // 2) 4K
                                if (groupKeys.genres.contains("4k")) {
                                    item {
                                        val sectionKey = "library:$selectedTabKey:curated:4k"
                                        val headerData =
                                            FishHeaderData.Chip(
                                                anchorKey = sectionKey,
                                                label = "4K",
                                            )
                                        ExpandableGroupSection(
                                            tab = selectedTab,
                                            stateKey = sectionKey,
                                            refreshSignal = cacheVersion + resumeTick,
                                            header = headerData,
                                            expandedDefault = true,
                                            loadItems = { loadItemsForGenre(selectedTab, "4k") },
                                            onOpenDetails = onOpen,
                                            onPlayDirect = onPlay,
                                            onAssignToKid = onAssignVod,
                                            showAssign = canEditWhitelist,
                                        )
                                    }
                                }

                                // 3) Kollektionen
                                if (groupKeys.genres.contains("collection")) {
                                    item {
                                        val sectionKey = "library:$selectedTabKey:curated:collection"
                                        val headerData =
                                            FishHeaderData.Chip(
                                                anchorKey = sectionKey,
                                                label = "Kollektionen",
                                            )
                                        ExpandableGroupSection(
                                            tab = selectedTab,
                                            stateKey = sectionKey,
                                            refreshSignal = cacheVersion + resumeTick,
                                            header = headerData,
                                            expandedDefault = true,
                                            loadItems = { loadItemsForGenre(selectedTab, "collection") },
                                            onOpenDetails = onOpen,
                                            onPlayDirect = onPlay,
                                            onAssignToKid = onAssignVod,
                                            showAssign = canEditWhitelist,
                                        )
                                    }
                                }

                                // 4) Unkategorisiert
                                if (groupKeys.genres.contains("other")) {
                                    item {
                                        val sectionKey = "library:$selectedTabKey:curated:other"
                                        val headerData =
                                            FishHeaderData.Chip(
                                                anchorKey = sectionKey,
                                                label = "Unkategorisiert",
                                            )
                                        ExpandableGroupSection(
                                            tab = selectedTab,
                                            stateKey = sectionKey,
                                            refreshSignal = cacheVersion + resumeTick,
                                            header = headerData,
                                            expandedDefault = true,
                                            loadItems = { loadItemsForGenre(selectedTab, "other") },
                                            onOpenDetails = onOpen,
                                            onPlayDirect = onPlay,
                                            onAssignToKid = onAssignVod,
                                            showAssign = canEditWhitelist,
                                        )
                                    }
                                }

                                // 5) Adults handled in dedicated section below when enabled
                            } else if (selectedTab != ContentTab.Live && !showGenres && providersAll.isNotEmpty()) {
                                val normalProviders = providersAll.filterNot { it.startsWith("adult_") || it.isBlank() }

                                // Render non-adult provider groups
                                items(normalProviders, key = { it }) { key ->
                                    val sectionKey = "library:$selectedTabKey:provider:$key"
                                    val displayLabel = providerLabelStore.labelFor(key)
                                    val headerData =
                                        FishHeaderData.Provider(
                                            anchorKey = sectionKey,
                                            label = displayLabel,
                                        )
                                    ExpandableGroupSection(
                                        tab = selectedTab,
                                        stateKey = sectionKey,
                                        refreshSignal = cacheVersion + resumeTick,
                                        header = headerData,
                                        expandedDefault = true,
                                        loadItems = { loadItemsForProvider(selectedTab, key) },
                                        onOpenDetails = onOpen,
                                        onPlayDirect = onPlay,
                                        onAssignToKid =
                                            when (selectedTab) {
                                                ContentTab.Vod -> onAssignVod
                                                ContentTab.Series -> onAssignSeries
                                                ContentTab.Live -> null
                                            },
                                        showAssign = canEditWhitelist && selectedTab != ContentTab.Live,
                                    )
                                }
                            }

                            // Adults umbrella (only when enabled and present; VOD only)
                            if (selectedTab == ContentTab.Vod && showAdultsEnabled && adultProviders.isNotEmpty()) {
                                item {
                                    Row(
                                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        com.chris.m3usuite.ui.components.chips
                                            .CategoryChip(key = "adult", label = "FOR ADULTS")
                                        TextButton(modifier = Modifier.focusScaleOnTv(), onClick = {
                                            adultsExpanded = !adultsExpanded
                                        }) { Text(if (adultsExpanded) "Weniger" else "Mehr") }
                                    }
                                }
                                if (adultsExpanded) {
                                    items(adultProviders, key = { it }) { key ->
                                        val sectionKey = "library:$selectedTabKey:adults:$key"
                                        val subLabel =
                                            key.removePrefix("adult_").replace('_', ' ').replaceFirstChar { c ->
                                                if (c.isLowerCase()) c.titlecase() else c.toString()
                                            }
                                        val headerData =
                                            FishHeaderData.Chip(
                                                anchorKey = sectionKey,
                                                label = subLabel,
                                            )
                                        ExpandableGroupSection(
                                            tab = selectedTab,
                                            stateKey = sectionKey,
                                            refreshSignal = cacheVersion + resumeTick,
                                            header = headerData,
                                            expandedDefault = true,
                                            loadItems = { loadItemsForProvider(selectedTab, key) },
                                            onOpenDetails = onOpen,
                                            onPlayDirect = onPlay,
                                            onAssignToKid = onAssignVod,
                                            showAssign = canEditWhitelist,
                                        )
                                    }
                                }
                            }

                            // Fallback-Kategorien sind global deaktiviert. Alle Inhalte sollen über Provider/Genre/Year-Buckets abgedeckt sein.

                            // Jahre (nur zeigen, wenn Provider-Ansicht aktiv und Live-Tab)
                            if (groupKeys.years.isNotEmpty() && selectedTab == ContentTab.Live && !showGenres) {
                                items(groupKeys.years, key = { it }) { y ->
                                    val sectionKey = "library:$selectedTabKey:year:$y"
                                    val headerData =
                                        FishHeaderData.Text(
                                            anchorKey = sectionKey,
                                            text = y.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                        )
                                    ExpandableGroupSection(
                                        tab = selectedTab,
                                        stateKey = sectionKey,
                                        refreshSignal = cacheVersion + resumeTick,
                                        header = headerData,
                                        expandedDefault = false,
                                        loadItems = { loadItemsForYear(selectedTab, y) },
                                        onOpenDetails = onOpen,
                                        onPlayDirect = onPlay,
                                        onAssignToKid =
                                            when (selectedTab) {
                                                ContentTab.Vod -> onAssignVod
                                                ContentTab.Series -> onAssignSeries
                                                ContentTab.Live -> null
                                            },
                                        showAssign = canEditWhitelist && selectedTab != ContentTab.Live,
                                    )
                                }
                            }

                            // Genres (nur wenn Genre-Ansicht aktiv)
                            if (groupKeys.genres.isNotEmpty() && showGenres && selectedTab == ContentTab.Series) {
                                items(groupKeys.genres, key = { it }) { key ->
                                    val genreKey = key.ifBlank { "unknown" }
                                    val sectionKey = "library:$selectedTabKey:genre:$genreKey"
                                    val label = key.ifBlank { "Unbekannt" }
                                    val headerData =
                                        FishHeaderData.Chip(
                                            anchorKey = sectionKey,
                                            label = label,
                                        )
                                    ExpandableGroupSection(
                                        tab = selectedTab,
                                        stateKey = sectionKey,
                                        refreshSignal = cacheVersion + resumeTick,
                                        header = headerData,
                                        expandedDefault = selectedTab == ContentTab.Live,
                                        loadItems = { loadItemsForGenre(selectedTab, key) },
                                        onOpenDetails = onOpen,
                                        onPlayDirect = onPlay,
                                        onAssignToKid =
                                            when (selectedTab) {
                                                ContentTab.Vod -> onAssignVod
                                                ContentTab.Series -> onAssignSeries
                                                ContentTab.Live -> null
                                            },
                                        showAssign = canEditWhitelist && selectedTab != ContentTab.Live,
                                    )
                                }
                            }
                        }
                    } // Box
                } // SaveableStateProvider
            } // HomeChromeScaffold
        } // FishHeaderHost
    }
} // LibraryScreen

@Composable
private fun libraryMediaRow(
    tab: ContentTab,
    items: List<MediaItem>,
    stateKey: String?,
    edgeLeftExpandChrome: Boolean,
    initialFocusEligible: Boolean = true,
    allowAssign: Boolean = false,
    header: FishHeaderData?,
    highlightIds: Set<Long> = emptySet(),
    onPrefetchKeys: OnPrefetchKeys? = null,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: ((MediaItem) -> Unit)?,
) {
    if (items.isEmpty()) return
    when (tab) {
        ContentTab.Live ->
            FishRow(
                items = items,
                stateKey = stateKey,
                edgeLeftExpandChrome = edgeLeftExpandChrome,
                initialFocusEligible = initialFocusEligible,
                onPrefetchKeys = onPrefetchKeys,
                header = header,
            ) { media ->
                LiveFishTile(
                    media = media,
                    onOpenDetails = onOpenDetails,
                    onPlayDirect = onPlayDirect,
                )
            }
        ContentTab.Vod ->
            FishRow(
                items = items,
                stateKey = stateKey,
                edgeLeftExpandChrome = edgeLeftExpandChrome,
                initialFocusEligible = initialFocusEligible,
                header = header,
            ) { media ->
                VodFishTile(
                    media = media,
                    isNew = highlightIds.contains(media.id),
                    allowAssign = allowAssign,
                    onOpenDetails = onOpenDetails,
                    onPlayDirect = onPlayDirect,
                    onAssignToKid = onAssignToKid ?: {},
                )
            }
        ContentTab.Series ->
            FishRow(
                items = items,
                stateKey = stateKey,
                edgeLeftExpandChrome = edgeLeftExpandChrome,
                initialFocusEligible = initialFocusEligible,
                header = header,
            ) { media ->
                SeriesFishTile(
                    media = media,
                    isNew = highlightIds.contains(media.id),
                    allowAssign = allowAssign,
                    onOpenDetails = onOpenDetails,
                    onPlayDirect = onPlayDirect,
                    onAssignToKid = onAssignToKid ?: {},
                )
            }
    }
}

@Composable
private fun libraryMediaRowPaged(
    tab: ContentTab,
    items: LazyPagingItems<MediaItem>,
    stateKey: String?,
    edgeLeftExpandChrome: Boolean,
    header: FishHeaderData?,
    allowAssign: Boolean,
    onPrefetchPaged: OnPrefetchPaged?,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: ((MediaItem) -> Unit)?,
) {
    when (tab) {
        ContentTab.Live ->
            FishRowPaged(
                items = items,
                stateKey = stateKey,
                edgeLeftExpandChrome = edgeLeftExpandChrome,
                onPrefetchPaged = onPrefetchPaged,
                header = header,
            ) { _, media ->
                LiveFishTile(
                    media = media,
                    onOpenDetails = onOpenDetails,
                    onPlayDirect = onPlayDirect,
                )
            }
        ContentTab.Vod ->
            FishRowPaged(
                items = items,
                stateKey = stateKey,
                edgeLeftExpandChrome = edgeLeftExpandChrome,
                onPrefetchPaged = onPrefetchPaged,
                header = header,
            ) { _, media ->
                VodFishTile(
                    media = media,
                    isNew = false,
                    allowAssign = allowAssign,
                    onOpenDetails = onOpenDetails,
                    onPlayDirect = onPlayDirect,
                    onAssignToKid = onAssignToKid ?: {},
                )
            }
        ContentTab.Series ->
            FishRowPaged(
                items = items,
                stateKey = stateKey,
                edgeLeftExpandChrome = edgeLeftExpandChrome,
                onPrefetchPaged = onPrefetchPaged,
                header = header,
            ) { _, media ->
                SeriesFishTile(
                    media = media,
                    isNew = false,
                    allowAssign = allowAssign,
                    onOpenDetails = onOpenDetails,
                    onPlayDirect = onPlayDirect,
                    onAssignToKid = onAssignToKid ?: {},
                )
            }
    }
}

@Composable
private fun ExpandableGroupSection(
    tab: ContentTab,
    stateKey: String,
    refreshSignal: Int,
    header: FishHeaderData,
    expandedDefault: Boolean,
    loadItems: suspend () -> List<MediaItem>,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: ((MediaItem) -> Unit)?,
    showAssign: Boolean,
) {
    var items by remember(stateKey) { mutableStateOf<List<MediaItem>>(emptyList()) }
    LaunchedEffect(stateKey, refreshSignal) {
        runCatching { items = loadItems() }
    }
    val allowAssign = showAssign && tab != ContentTab.Live
    libraryMediaRow(
        tab = tab,
        items = items,
        stateKey = stateKey,
        edgeLeftExpandChrome = true,
        initialFocusEligible = expandedDefault,
        allowAssign = allowAssign,
        header = header,
        onOpenDetails = onOpenDetails,
        onPlayDirect = onPlayDirect,
        onAssignToKid = onAssignToKid,
    )
}
