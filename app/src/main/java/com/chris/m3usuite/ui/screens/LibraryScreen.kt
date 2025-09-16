package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import com.chris.m3usuite.core.xtream.ProviderLabelStore
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.chris.m3usuite.data.obx.toMediaItem
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavHostController,
    openLive: (Long) -> Unit,
    openVod: (Long) -> Unit,
    openSeries: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val tabIdx by store.libraryTabIndex.collectAsStateWithLifecycle(initialValue = 0)
    val selectedTab = when (tabIdx) { 0 -> "live"; 1 -> "vod"; else -> "series" }
    val repo = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }
    val permRepo = remember { com.chris.m3usuite.data.repo.PermissionRepository(ctx, store) }
    var canEditWhitelist by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { canEditWhitelist = permRepo.current().canEditWhitelist }

    var query by remember { mutableStateOf(TextFieldValue("")) }
    var expandLive by remember { mutableStateOf(true) }
    var expandVod by remember { mutableStateOf(true) }
    var expandSeries by remember { mutableStateOf(true) }

    // OBX-first rows like StartScreen (horizontal rows with vertical scroll)
    // Flat lists (kept for search). Grouped rows below use provider/genre/year keys.
    val live = remember { mutableStateListOf<MediaItem>() }
    val vod = remember { mutableStateListOf<MediaItem>() }
    val series = remember { mutableStateListOf<MediaItem>() }
    var liveProviders by remember { mutableStateOf<List<String>>(emptyList()) }
    var liveGenres by remember { mutableStateOf<List<String>>(emptyList()) }
    var vodProviders by remember { mutableStateOf<List<String>>(emptyList()) }
    var vodGenres by remember { mutableStateOf<List<String>>(emptyList()) }
    var vodYears by remember { mutableStateOf<List<Int>>(emptyList()) }
    var seriesProviders by remember { mutableStateOf<List<String>>(emptyList()) }
    var seriesGenres by remember { mutableStateOf<List<String>>(emptyList()) }
    var seriesYears by remember { mutableStateOf<List<Int>>(emptyList()) }

    suspend fun loadLive(): List<MediaItem> = withContext(Dispatchers.IO) {
        val rows = if (query.text.isNotBlank()) repo.searchLiveByName(query.text.trim(), 0, 240) else repo.livePaged(0, 240)
        rows.map { it.toMediaItem(ctx) }
    }
    suspend fun loadVod(): List<MediaItem> = withContext(Dispatchers.IO) {
        val rows = if (query.text.isNotBlank()) repo.searchVodByName(query.text.trim(), 0, 240) else repo.vodPaged(0, 240)
        rows.map { it.toMediaItem(ctx) }
    }
    suspend fun loadSeries(): List<MediaItem> = withContext(Dispatchers.IO) {
        val rows = if (query.text.isNotBlank()) repo.searchSeriesByName(query.text.trim(), 0, 240) else repo.seriesPaged(0, 240)
        rows.map { it.toMediaItem(ctx) }
    }

    LaunchedEffect(query.text) {
        live.clear(); live.addAll(loadLive())
        vod.clear(); vod.addAll(loadVod())
        series.clear(); series.addAll(loadSeries())
        // Group headers only when not searching
        if (query.text.isBlank()) {
            liveProviders = withContext(Dispatchers.IO) { repo.liveProviderKeys() }
            liveGenres = withContext(Dispatchers.IO) { repo.liveGenreKeys() }
            vodProviders = withContext(Dispatchers.IO) { repo.vodProviderKeys() }
            vodGenres = withContext(Dispatchers.IO) { repo.vodGenreKeys() }
            vodYears = withContext(Dispatchers.IO) { repo.vodYearKeys() }
            seriesProviders = withContext(Dispatchers.IO) { repo.seriesProviderKeys() }
            seriesGenres = withContext(Dispatchers.IO) { repo.seriesGenreKeys() }
            seriesYears = withContext(Dispatchers.IO) { repo.seriesYearKeys() }
        } else {
            liveProviders = emptyList(); liveGenres = emptyList()
            vodProviders = emptyList(); vodGenres = emptyList(); vodYears = emptyList()
            seriesProviders = emptyList(); seriesGenres = emptyList(); seriesYears = emptyList()
        }
    }

    // React to ObjectBox changes (bridge OBX -> Compose)
    LaunchedEffect(Unit) {
        repo.liveChanges().collect {
            live.clear(); live.addAll(loadLive())
        }
    }
    LaunchedEffect(Unit) {
        repo.vodChanges().collect {
            vod.clear(); vod.addAll(loadVod())
            if (query.text.isBlank()) {
                vodProviders = withContext(Dispatchers.IO) { repo.vodProviderKeys() }
                vodGenres = withContext(Dispatchers.IO) { repo.vodGenreKeys() }
                vodYears = withContext(Dispatchers.IO) { repo.vodYearKeys() }
            }
        }
    }
    LaunchedEffect(Unit) {
        repo.seriesChanges().collect {
            series.clear(); series.addAll(loadSeries())
            if (query.text.isBlank()) {
                seriesProviders = withContext(Dispatchers.IO) { repo.seriesProviderKeys() }
                seriesGenres = withContext(Dispatchers.IO) { repo.seriesGenreKeys() }
                seriesYears = withContext(Dispatchers.IO) { repo.seriesYearKeys() }
            }
        }
    }

    // Auto-refresh grouped headers after Xtream delta import completes
    val wm = remember { androidx.work.WorkManager.getInstance(ctx) }
    LaunchedEffect(Unit) {
        var lastId: java.util.UUID? = null
        while (true) {
            try {
                val infos = withContext(Dispatchers.IO) { wm.getWorkInfosForUniqueWork("xtream_delta_import_once").get() }
                val done = infos.firstOrNull { it.state == androidx.work.WorkInfo.State.SUCCEEDED }
                if (done != null && done.id != lastId) {
                    lastId = done.id
                    // Reload lists and headers
                    live.clear(); live.addAll(loadLive())
                    vod.clear(); vod.addAll(loadVod())
                    series.clear(); series.addAll(loadSeries())
                    if (query.text.isBlank()) {
                        liveProviders = withContext(Dispatchers.IO) { repo.liveProviderKeys() }
                        liveGenres = withContext(Dispatchers.IO) { repo.liveGenreKeys() }
                        vodProviders = withContext(Dispatchers.IO) { repo.vodProviderKeys() }
                        vodGenres = withContext(Dispatchers.IO) { repo.vodGenreKeys() }
                        vodYears = withContext(Dispatchers.IO) { repo.vodYearKeys() }
                        seriesProviders = withContext(Dispatchers.IO) { repo.seriesProviderKeys() }
                        seriesGenres = withContext(Dispatchers.IO) { repo.seriesGenreKeys() }
                        seriesYears = withContext(Dispatchers.IO) { repo.seriesYearKeys() }
                    }
                }
            } catch (_: Throwable) { /* ignore */ }
            kotlinx.coroutines.delay(1200)
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    HomeChromeScaffold(
        title = "Bibliothek",
        onSettings = null,
        onSearch = null,
        onProfiles = null,
        onRefresh = null,
        listState = listState,
        onLogo = {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != "library") {
                navController.navigate("library") { launchSingleTop = true }
            }
        },
        bottomBar = {
            com.chris.m3usuite.ui.home.header.FishITBottomPanel(
                selected = selectedTab,
                onSelect = { sel ->
                    val idx = when (sel) { "live" -> 0; "vod" -> 1; else -> 2 }
                    scope.launch { store.setLibraryTabIndex(idx) }
                }
            )
        }
    ) { pads ->
        // Background fish like Start/Settings
        Box(Modifier.fillMaxSize()) {
            com.chris.m3usuite.ui.fx.FishBackground(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                alpha = 0.06f
            )
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 12.dp), modifier = Modifier.fillMaxSize().padding(pads)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Suche (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            // Live grouped: Provider rows
            if (selectedTab == "live" && query.text.isBlank() && liveProviders.isNotEmpty()) {
                item { Text("Live – Anbieter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                items(liveProviders) { key ->
                    var expanded by remember(key) { mutableStateOf(true) }
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ProviderLabelStore.get(ctx).labelFor(key), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger" else "Mehr") }
                        }
                        if (expanded) {
                            val itemsFor = remember(key) { mutableStateListOf<MediaItem>() }
                            LaunchedEffect(key) {
                                val rows = withContext(Dispatchers.IO) { repo.liveByProviderKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) } }
                                itemsFor.clear(); itemsFor.addAll(rows)
                            }
                            com.chris.m3usuite.ui.components.rows.LiveRow(
                                items = itemsFor,
                                onOpenDetails = { m -> openLive(m.id) },
                                onPlayDirect = { m -> openLive(m.id) }
                            )
                        }
                    }
                }
            }
            // Live grouped: Genre rows
            if (selectedTab == "live" && query.text.isBlank() && liveGenres.isNotEmpty()) {
                item { Text("Live – Genres", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                items(liveGenres) { key ->
                    var expanded by remember(key) { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ProviderLabelStore.get(ctx).labelFor(key), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger" else "Mehr") }
                        }
                        if (expanded) {
                            val itemsFor = remember(key) { mutableStateListOf<MediaItem>() }
                            LaunchedEffect(key) {
                                val rows = withContext(Dispatchers.IO) { repo.liveByGenreKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) } }
                                itemsFor.clear(); itemsFor.addAll(rows)
                            }
                            com.chris.m3usuite.ui.components.rows.LiveRow(
                                items = itemsFor,
                                onOpenDetails = { m -> openLive(m.id) },
                                onPlayDirect = { m -> openLive(m.id) }
                            )
                        }
                    }
                }
            }

            // Filme grouped: Provider
            if (selectedTab == "vod" && query.text.isBlank() && vodProviders.isNotEmpty()) {
                item { Text("Filme – Anbieter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                items(vodProviders) { key ->
                    var expanded by remember(key) { mutableStateOf(true) }
                    val itemsFor = remember(key) { mutableStateListOf<MediaItem>() }
                    LaunchedEffect(key) {
                        val rows = withContext(Dispatchers.IO) { repo.vodByProviderKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) } }
                        itemsFor.clear(); itemsFor.addAll(rows)
                    }
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ProviderLabelStore.get(ctx).labelFor(key), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger" else "Mehr") }
                        }
                        if (expanded) {
                            com.chris.m3usuite.ui.components.rows.VodRow(
                                items = itemsFor,
                                onOpenDetails = { m -> openVod(m.id) },
                                onPlayDirect = { m -> openVod(m.id) },
                                onAssignToKid = { mi ->
                                    if (!canEditWhitelist) return@VodRow
                                    kotlinx.coroutines.runBlocking {
                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                                            val kRepo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                            kids.forEach { kRepo.allow(it.id, "vod", mi.id) }
                                        }
                                    }
                                },
                                showAssign = canEditWhitelist
                            )
                        }
                    }
                }
            }
            // Filme grouped: Jahre
            if (selectedTab == "vod" && query.text.isBlank() && vodYears.isNotEmpty()) {
                item { Text("Filme – Jahre", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                items(vodYears) { y ->
                    var expanded by remember(y) { mutableStateOf(false) }
                    val itemsFor = remember(y) { mutableStateListOf<MediaItem>() }
                    LaunchedEffect(y) {
                        val rows = withContext(Dispatchers.IO) { repo.vodByYearKeyPaged(y, 0, 120).map { it.toMediaItem(ctx) } }
                        itemsFor.clear(); itemsFor.addAll(rows)
                    }
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(y.toString(), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger" else "Mehr") }
                        }
                        if (expanded) {
                            com.chris.m3usuite.ui.components.rows.VodRow(
                                items = itemsFor,
                                onOpenDetails = { m -> openVod(m.id) },
                                onPlayDirect = { m -> openVod(m.id) },
                                onAssignToKid = { mi ->
                                    if (!canEditWhitelist) return@VodRow
                                    kotlinx.coroutines.runBlocking {
                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                                            val kRepo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                            kids.forEach { kRepo.allow(it.id, "vod", mi.id) }
                                        }
                                    }
                                },
                                showAssign = canEditWhitelist
                            )
                        }
                    }
                }
            }
            // Filme grouped: Genres
            if (selectedTab == "vod" && query.text.isBlank() && vodGenres.isNotEmpty()) {
                item { Text("Filme – Genres", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                items(vodGenres) { key ->
                    var expanded by remember(key) { mutableStateOf(false) }
                    val itemsFor = remember(key) { mutableStateListOf<MediaItem>() }
                    LaunchedEffect(key) {
                        val rows = withContext(Dispatchers.IO) { repo.vodByGenreKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) } }
                        itemsFor.clear(); itemsFor.addAll(rows)
                    }
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ProviderLabelStore.get(ctx).labelFor(key), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger" else "Mehr") }
                        }
                        if (expanded) {
                            com.chris.m3usuite.ui.components.rows.VodRow(
                                items = itemsFor,
                                onOpenDetails = { m -> openVod(m.id) },
                                onPlayDirect = { m -> openVod(m.id) },
                                onAssignToKid = { mi ->
                                    if (!canEditWhitelist) return@VodRow
                                    kotlinx.coroutines.runBlocking {
                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                                            val kRepo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                            kids.forEach { kRepo.allow(it.id, "vod", mi.id) }
                                        }
                                    }
                                },
                                showAssign = canEditWhitelist
                            )
                        }
                    }
                }
            }

            // Serien grouped: Provider
            if (selectedTab == "series" && query.text.isBlank() && seriesProviders.isNotEmpty()) {
                item { Text("Serien – Anbieter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                items(seriesProviders) { key ->
                    var expanded by remember(key) { mutableStateOf(true) }
                    val itemsFor = remember(key) { mutableStateListOf<MediaItem>() }
                    LaunchedEffect(key) {
                        val rows = withContext(Dispatchers.IO) { repo.seriesByProviderKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) } }
                        itemsFor.clear(); itemsFor.addAll(rows)
                    }
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ProviderLabelStore.get(ctx).labelFor(key), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger" else "Mehr") }
                        }
                        if (expanded) {
                            com.chris.m3usuite.ui.components.rows.SeriesRow(
                                items = itemsFor,
                                onOpenDetails = { m -> openSeries(m.id) },
                                onPlayDirect = { m -> openSeries(m.id) },
                                onAssignToKid = { mi ->
                                    if (!canEditWhitelist) return@SeriesRow
                                    kotlinx.coroutines.runBlocking {
                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                                            val kRepo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                            kids.forEach { kRepo.allow(it.id, "series", mi.id) }
                                        }
                                    }
                                },
                                showAssign = canEditWhitelist
                            )
                        }
                    }
                }
            }
            // Serien grouped: Jahre
            if (selectedTab == "series" && query.text.isBlank() && seriesYears.isNotEmpty()) {
                item { Text("Serien – Jahre", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                items(seriesYears) { y ->
                    var expanded by remember(y) { mutableStateOf(false) }
                    val itemsFor = remember(y) { mutableStateListOf<MediaItem>() }
                    LaunchedEffect(y) {
                        val rows = withContext(Dispatchers.IO) { repo.seriesByYearKeyPaged(y, 0, 120).map { it.toMediaItem(ctx) } }
                        itemsFor.clear(); itemsFor.addAll(rows)
                    }
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(y.toString(), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger" else "Mehr") }
                        }
                        if (expanded) {
                            com.chris.m3usuite.ui.components.rows.SeriesRow(
                                items = itemsFor,
                                onOpenDetails = { m -> openSeries(m.id) },
                                onPlayDirect = { m -> openSeries(m.id) },
                                onAssignToKid = { mi ->
                                    if (!canEditWhitelist) return@SeriesRow
                                    kotlinx.coroutines.runBlocking {
                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                                            val kRepo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                            kids.forEach { kRepo.allow(it.id, "series", mi.id) }
                                        }
                                    }
                                },
                                showAssign = canEditWhitelist
                            )
                        }
                    }
                }
            }
            // Serien grouped: Genres
            if (selectedTab == "series" && query.text.isBlank() && seriesGenres.isNotEmpty()) {
                item { Text("Serien – Genres", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                items(seriesGenres) { key ->
                    var expanded by remember(key) { mutableStateOf(false) }
                    val itemsFor = remember(key) { mutableStateListOf<MediaItem>() }
                    LaunchedEffect(key) {
                        val rows = withContext(Dispatchers.IO) { repo.seriesByGenreKeyPaged(key, 0, 120).map { it.toMediaItem(ctx) } }
                        itemsFor.clear(); itemsFor.addAll(rows)
                    }
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(key.ifBlank { "Unbekannt" }, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger" else "Mehr") }
                        }
                        if (expanded) {
                            com.chris.m3usuite.ui.components.rows.SeriesRow(
                                items = itemsFor,
                                onOpenDetails = { m -> openSeries(m.id) },
                                onPlayDirect = { m -> openSeries(m.id) },
                                onAssignToKid = { mi ->
                                    if (!canEditWhitelist) return@SeriesRow
                                    kotlinx.coroutines.runBlocking {
                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val kids = com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" }
                                            val kRepo = com.chris.m3usuite.data.repo.KidContentRepository(ctx)
                                            kids.forEach { kRepo.allow(it.id, "series", mi.id) }
                                        }
                                    }
                                },
                                showAssign = canEditWhitelist
                            )
                        }
                    }
                }
            }
        }
    }
}
