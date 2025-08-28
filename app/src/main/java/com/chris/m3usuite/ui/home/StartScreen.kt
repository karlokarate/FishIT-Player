package com.chris.m3usuite.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
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

    var series by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var movies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var tv by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        scope.launch {
            val dao = db.mediaDao()
            val rawSeries = dao.listByType("series", 2000, 0)
            val rawMovies = dao.listByType("vod", 2000, 0)
            val rawTv = dao.listByType("live", 2000, 0)
            series = sortByYearDesc(rawSeries, { it.year }, { it.name })
            movies = sortByYearDesc(rawMovies, { it.year }, { it.name })
            tv = filterGermanTv(rawTv, { null }, { null }, { it.categoryName }, { it.name })
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
                }
            }
        },
        onSettings = { navController.navigate("settings") },
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
        bottomBar = {
            com.chris.m3usuite.ui.home.header.FishITBottomPanel(
                selected = "all",
                onSelect = { id ->
                    val tab = when (id) { "live" -> 0; "vod" -> 1; "series" -> 2; else -> 3 }
                    scope.launch { store.setLibraryTabIndex(tab) }
                    navController.navigate("browse")
                }
            )
        },
        listState = listState
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
                // TV ohne Header
                item("row_tv") {
                    Box(Modifier.padding(top = 4.dp)) {
                        LiveRow(items = tv, onClick = { mi -> openLive(mi.id) })
                    }
                }
            }
        }
    }
}
