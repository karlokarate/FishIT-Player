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
import kotlinx.coroutines.withContext
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.flow.first
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
// removed unused zIndex/IntOffset/animateFloatAsState/mutableStateListOf
import com.chris.m3usuite.data.repo.MediaQueryRepository

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
    val currentProfileId by store.currentProfileId.collectAsState(initial = -1L)
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

    LaunchedEffect(isKid) {
        scope.launch {
            val rawSeries = withContext(kotlinx.coroutines.Dispatchers.IO) { mediaRepo.listByTypeFiltered("series", 2000, 0) }
            val rawMovies = withContext(kotlinx.coroutines.Dispatchers.IO) { mediaRepo.listByTypeFiltered("vod", 2000, 0) }
            val rawTv = withContext(kotlinx.coroutines.Dispatchers.IO) { mediaRepo.listByTypeFiltered("live", 2000, 0) }
            series = sortByYearDesc(rawSeries, { it.year }, { it.name })
            movies = sortByYearDesc(rawMovies, { it.year }, { it.name })
            tv = filterGermanTv(rawTv, { null }, { null }, { it.categoryName }, { it.name })
        }
    }

    // Favorites for live row on Home
    val favCsv by store.favoriteLiveIdsCsv.collectAsState(initial = "")
    LaunchedEffect(favCsv, isKid) {
        val idsList = favCsv.split(',').mapNotNull { it.toLongOrNull() }
        favLive = if (idsList.isEmpty()) emptyList() else withContext(kotlinx.coroutines.Dispatchers.IO) {
            val all = mediaRepo.listByTypeFiltered("live", 6000, 0)
            val map = all.associateBy { it.id }
            idsList.mapNotNull { map[it] }
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
        onSettings = if (isKid) null else {
            {
                val current = navController.currentBackStackEntry?.destination?.route
                if (current != "settings") {
                    navController.navigate("settings") { launchSingleTop = true }
                }
            }
        },
        onRefresh = {
            scope.launch {
                val dao = db.mediaDao()
                val rawSeries = dao.listByType("series", 2000, 0)
                val rawMovies = dao.listByType("vod", 2000, 0)
                val rawTv = dao.listByType("live", 2000, 0)
                series = sortByYearDesc(rawSeries, { it.year }, { it.name })
                movies = sortByYearDesc(rawMovies, { it.year }, { it.name })
                tv = filterGermanTv(rawTv, { null }, { null }, { it.categoryName }, { it.name })
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
        Box(Modifier.fillMaxSize().padding(pads)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                item("hdr_series") {
                    Text("Serien", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp))
                }
                item("row_series") {
                    Box(Modifier.padding(horizontal = 0.dp)) {
                        SeriesRow(items = series, onClick = { mi -> openSeries(mi.id) })
                    }
                }
                item("hdr_movies") {
                    Text("Filme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp))
                }
                item("row_movies") {
                    Box(Modifier.padding(horizontal = 0.dp)) {
                        VodRow(items = movies, onClick = { mi -> openVod(mi.id) })
                    }
                }
                // TV (favorisierte Kanäle). Wenn leer: Plus-Kachel zum Hinzufügen.
                item("row_tv") {
                    Box(Modifier.padding(top = 4.dp)) {
                        if (favLive.isEmpty()) {
                            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                                item {
                                    Card(
                                        modifier = Modifier.size(200.dp, 112.dp).padding(end = 12.dp)
                                            .let { m -> m },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(8.dp)) {
                                            AppIconButton(icon = AppIcon.BookmarkAdd, contentDescription = "Sender hinzufügen", onClick = { showLivePicker = true }, size = 36.dp)
                                        }
                                    }
                                }
                            }
                        } else {
                            com.chris.m3usuite.ui.components.rows.ReorderableLiveRow(
                                items = favLive,
                                onOpen = { openLive(it) },
                                onAdd = { showLivePicker = true },
                                onReorder = { newOrder -> scope.launch { store.setFavoriteLiveIdsCsv(newOrder.joinToString(",")) } },
                                onRemove = { removeIds ->
                                    scope.launch {
                                        val current = store.favoriteLiveIdsCsv.first().split(',').mapNotNull { it.toLongOrNull() }.toMutableList()
                                        current.removeAll(removeIds.toSet())
                                        store.setFavoriteLiveIdsCsv(current.joinToString(","))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    // Live picker sheet: multi-select grid + search + provider chips
    if (showLivePicker) {
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
                    item { FilterChip(selected = provider == null, onClick = { provider = null }, label = { Text("Alle") }) }
                    items(providers) { p -> FilterChip(selected = provider == p, onClick = { provider = if (provider == p) null else p }, label = { Text(p) }) }
                }
                val filtered = remember(allLive, query, provider) {
                    val q = query.trim().lowercase()
                    allLive.filter { item ->
                        val matchQ = if (q.isBlank()) true else item.name.lowercase().contains(q) || (item.categoryName ?: "").lowercase().contains(q)
                        val matchP = provider?.let { p -> (item.categoryName ?: "").contains(p, ignoreCase = true) } ?: true
                        matchQ && matchP
                    }
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
                        showLivePicker = false
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).focusRequester(addReq).focusable(true)
            ) { AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.BookmarkAdd, contentDescription = "Hinzufügen", onClick = {
                    scopePick.launch {
                        val csv = selected.joinToString(",")
                        store.setFavoriteLiveIdsCsv(csv)
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
