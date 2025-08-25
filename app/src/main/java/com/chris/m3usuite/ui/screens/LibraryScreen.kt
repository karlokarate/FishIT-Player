package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.MediaItem
import com.chris.m3usuite.data.db.ResumeEpisodeView
import com.chris.m3usuite.data.db.ResumeVodView
import com.chris.m3usuite.player.ExternalPlayer
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.common.FocusableCard
import com.chris.m3usuite.ui.common.isTv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberImageHeaders

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    openLive: (Long) -> Unit,
    openVod: (Long) -> Unit,
    openSeries: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val db = remember { DbProvider.get(ctx) }
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()
    val tv = isTv(ctx)
    val focus = LocalFocusManager.current
    val headers = rememberImageHeaders()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Live", "VOD", "Series", "Alle")

    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var categories by remember { mutableStateOf<List<String?>>(emptyList()) }

    var resumeVod by remember { mutableStateOf<List<ResumeVodView>>(emptyList()) }
    var resumeEps by remember { mutableStateOf<List<ResumeEpisodeView>>(emptyList()) }

    // Kategorie-Sheet
    var showCategorySheet by remember { mutableStateOf(false) }

    // „Weiter schauen“ ein-/ausblendbar
    var showContinueWatching by rememberSaveable { mutableStateOf(true) }

    fun fmtSecs(s: Int): String {
        val ss = max(0, s); val h = ss / 3600; val m = (ss % 3600) / 60; val sec = ss % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    fun playVodResumeNow(v: ResumeVodView) {
        val startMs = v.positionSecs.toLong() * 1000
        v.url?.let { ExternalPlayer.open(ctx, it, startPositionMs = startMs) }
    }
    suspend fun buildEpisodeUrlAndPlay(e: ResumeEpisodeView) {
        val host = store.xtHost.first(); val user = store.xtUser.first(); val pass = store.xtPass.first()
        val out  = store.xtOutput.first(); val port = store.xtPort.first()
        if (host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
            val cfg = XtreamConfig(host, port, user, pass, out)
            ExternalPlayer.open(ctx, cfg.seriesEpisodeUrl(e.episodeId, e.containerExt), startPositionMs = e.positionSecs.toLong() * 1000)
        }
    }

    fun load() = scope.launch {
        val dao = db.mediaDao()
        val type = when (tab) { 0 -> "live"; 1 -> "vod"; 2 -> "series"; else -> null }
        val list = when {
            type == null -> if (searchQuery.text.isBlank()) {
                dao.listByType("live", 4000, 0) + dao.listByType("vod", 4000, 0) + dao.listByType("series", 4000, 0)
            } else dao.globalSearch(searchQuery.text, 6000, 0)
            selectedCategory != null -> dao.byTypeAndCategory(type, selectedCategory)
            searchQuery.text.isNotBlank() -> dao.globalSearch(searchQuery.text, 6000, 0).filter { it.type == type }
            else -> dao.listByType(type, 6000, 0)
        }
        mediaItems = list
        categories = if (type != null) dao.categoriesByType(type) else emptyList()
    }

    fun loadResume() = scope.launch {
        val rDao = db.resumeDao()
        resumeVod = rDao.recentVod(limit = 12)
        resumeEps = rDao.recentEpisodes(limit = 12)
    }

    fun submitSearch() { load(); focus.clearFocus() }

    // Entfernen einzelner Resume-Einträge
    fun removeVodResume(v: ResumeVodView) = scope.launch {
        db.resumeDao().clearVod(v.mediaId)
        loadResume()
    }
    fun removeEpisodeResume(e: ResumeEpisodeView) = scope.launch {
        db.resumeDao().clearEpisode(e.episodeId)
        loadResume()
    }

    LaunchedEffect(Unit) { load(); loadResume() }
    LaunchedEffect(tab, selectedCategory) { load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("m3uSuite") }) }
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Weiter schauen (schließbar + LongPress-Remove)
            if (showContinueWatching && (resumeVod.isNotEmpty() || resumeEps.isNotEmpty())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Weiter schauen", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showContinueWatching = false }) { Text("Schließen") }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(resumeVod.size) { i ->
                        val v = resumeVod[i]
                        FocusableCard(
                            modifier = Modifier
                                .width(if (tv) 220.dp else 180.dp)
                                .height(if (tv) 300.dp else 260.dp)
                                .combinedClickable(
                                    onClick = { playVodResumeNow(v) },
                                    onLongClick = { removeVodResume(v) }
                                ),
                            onClick = { /* handled in combinedClickable */ }
                        ) {
                            Column {
                                AsyncImage(
                                    model = buildImageRequest(ctx, v.poster, headers),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (tv) 200.dp else 160.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(v.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Fortsetzen ${fmtSecs(v.positionSecs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    items(resumeEps.size) { i ->
                        val e = resumeEps[i]
                        FocusableCard(
                            modifier = Modifier
                                .width(if (tv) 220.dp else 180.dp)
                                .height(if (tv) 300.dp else 260.dp)
                                .combinedClickable(
                                    onClick = { scope.launch { buildEpisodeUrlAndPlay(e) } },
                                    onLongClick = { removeEpisodeResume(e) }
                                ),
                            onClick = { /* handled in combinedClickable */ }
                        ) {
                            Column {
                                AsyncImage(
                                    model = buildImageRequest(ctx, e.poster, headers),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (tv) 200.dp else 160.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("S${e.season}E${e.episodeNum}  ${e.title}", maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Fortsetzen ${fmtSecs(e.positionSecs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Tabs
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i,
                        onClick = {
                            tab = i
                            selectedCategory = null
                            searchQuery = TextFieldValue("")
                            load()
                        },
                        text = { Text(t) }
                    )
                }
            }

            // Suche + Kategorien
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                label = { Text("Suche (Titel)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .onPreviewKeyEvent { ev ->
                        if (ev.key == Key.Enter && ev.type == KeyEventType.KeyUp) { submitSearch(); true } else false
                    }
            )

            if (tab in 0..2 && categories.isNotEmpty()) {
                // Schnellwahl: alle Kategorien horizontal scrollbar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = { selectedCategory = null; load() }, label = { Text("Alle") })
                    AssistChip(onClick = { showCategorySheet = true }, label = { Text("Alle Kategorien…") })
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listItems(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat; load() },
                            label = { Text(cat ?: "Unbekannt") }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Content: Grid oder Liste – bekommt die restliche Höhe
            val useGrid = tab != 0 || tv
            if (useGrid) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LibraryGridContent(
                        tv = tv,
                        mediaItems = mediaItems,
                        ctx = ctx,
                        headers = headers,
                        onOpen = { mi ->
                            when (mi.type) {
                                "live" -> openLive(mi.id)
                                "vod" -> openVod(mi.id)
                                "series" -> openSeries(mi.id)
                            }
                        }
                    )
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LibraryListContent(
                        mediaItems = mediaItems,
                        ctx = ctx,
                        headers = headers,
                        onOpenLive = { id -> openLive(id) }
                    )
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
}

/* -------------------------- Extracted content composables -------------------------- */

@Composable
private fun LibraryGridContent(
    tv: Boolean,
    mediaItems: List<MediaItem>,
    ctx: android.content.Context,
    headers: com.chris.m3usuite.ui.util.ImageHeaders,
    onOpen: (MediaItem) -> Unit
) {
    val columns = if (tv) 4 else 2
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        // KEIN weight() hier – der liegt im Aufrufer in der Column
        modifier = Modifier.fillMaxSize(),
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
    onOpenLive: (Long) -> Unit
) {
    LazyColumn(
        // KEIN weight() hier – der liegt im Aufrufer in der Column
        modifier = Modifier.fillMaxSize(),
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
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
