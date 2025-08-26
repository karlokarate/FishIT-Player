package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.common.FocusableCard
import com.chris.m3usuite.ui.common.isTv
import kotlinx.coroutines.launch
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberImageHeaders
import com.chris.m3usuite.ui.components.ResumeSectionAuto
import com.chris.m3usuite.ui.components.CollapsibleHeader

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

    // Kategorie-Sheet
    var showCategorySheet by remember { mutableStateOf(false) }

    // Collapsible-State für Header (global gespeichert)
    val collapsed by store.headerCollapsed.collectAsState(initial = false)

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

    fun submitSearch() { load(); focus.clearFocus() }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(tab, selectedCategory) { load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("m3uSuite") }) }
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Neue ein-/ausklappbare Sektion "Weiter schauen"
            @Composable
            fun ResumeCollapsible(content: @Composable () -> Unit) {
                CollapsibleHeader(
                    store = store,
                    title = { Text("Weiter schauen", style = MaterialTheme.typography.titleMedium) },
                    headerContent = { content() },
                    contentBelow = { _ -> if (collapsed) Spacer(Modifier.height(0.dp)) }
                )
            }

            ResumeCollapsible {
                ResumeSectionAuto(
                    limit = 20,
                    onPlayVod = { /* TODO: später Navigation/Player */ },
                    onPlayEpisode = { /* TODO: später Navigation/Player */ },
                    onClearVod = {},
                    onClearEpisode = {}
                )
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
