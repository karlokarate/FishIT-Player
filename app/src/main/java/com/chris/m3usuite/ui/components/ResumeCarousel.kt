package com.chris.m3usuite.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavController
import com.chris.m3usuite.player.ExternalPlayer
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.ResumeEpisodeView
import com.chris.m3usuite.data.db.ResumeVodView
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

// Resume-UI: Material3

// resume-ui: Default sentinels to detect empty callbacks for clear actions
private val DefaultOnClearVod: (ResumeVodView) -> Unit = {}
private val DefaultOnClearEpisode: (ResumeEpisodeView) -> Unit = {}

// resume-ui: chooser Intern vs Extern (NavController + ExternalPlayer)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeSectionAuto(
    navController: NavController,
    limit: Int = 20,
    onPlayVod: (ResumeVodView) -> Unit = {},
    onPlayEpisode: (ResumeEpisodeView) -> Unit = {},
    onClearVod: (ResumeVodView) -> Unit = DefaultOnClearVod,
    onClearEpisode: (ResumeEpisodeView) -> Unit = DefaultOnClearEpisode
) {
    val ctx = LocalContext.current
    val db = remember { DbProvider.get(ctx) }
    val store = remember { SettingsStore(ctx) }
    val mediaRepo = remember { com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, store) }

    var vod by remember { mutableStateOf<List<ResumeVodView>>(emptyList()) }
    var eps by remember { mutableStateOf<List<ResumeEpisodeView>>(emptyList()) }
    // resume-ui: Chooser BottomSheet Intern vs Extern
    var chooserItem by remember { mutableStateOf<ResumeVodView?>(null) }
    var chooserEpisode by remember { mutableStateOf<ResumeEpisodeView?>(null) }
    // store already defined above

    // resume-ui: Laden beider Listen auf IO-Thread
    LaunchedEffect(Unit) {
        try {
            val dao = db.resumeDao()
            val v = withContext(Dispatchers.IO) { dao.recentVod(limit) }
            val e = withContext(Dispatchers.IO) { dao.recentEpisodes(limit) }
            // Filter by effective allow-set for non-adult profiles
            val prof = withContext(Dispatchers.IO) { DbProvider.get(ctx).profileDao().byId(store.currentProfileId.first()) }
            if (prof?.type == "adult") {
                vod = v; eps = e
            } else {
                val allowedVodIds = withContext(Dispatchers.IO) { mediaRepo.listByTypeFiltered("vod", 100000, 0).map { it.id }.toSet() }
                val seriesAllowedIds = withContext(Dispatchers.IO) { mediaRepo.listByTypeFiltered("series", 100000, 0).map { it.id }.toSet() }
                val filteredVod = v.filter { it.mediaId in allowedVodIds }
                // Map episode -> series mediaItem by seriesStreamId
                val filteredEp = withContext(Dispatchers.IO) {
                    e.filter { rev ->
                        val seriesMi = db.mediaDao().seriesByStreamId(rev.seriesStreamId)
                        seriesMi != null && (seriesMi.id in seriesAllowedIds)
                    }
                }
                vod = filteredVod
                eps = filteredEp
            }
        } catch (_: Throwable) {
            // Ignorieren – bleibt leer
        }
    }

    val scope = rememberCoroutineScope()
    // rows keep simple; haptics handled inside ResumeCard per onLongClick

    Column(modifier = Modifier.fillMaxWidth()) {
        if (chooserItem != null) {
            ModalBottomSheet(
                onDismissRequest = { chooserItem = null }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Wie abspielen?", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val encoded = Uri.encode(chooserItem!!.url!!)
                        val start = chooserItem!!.positionSecs.toLong() * 1000L
                        navController.navigate("player?url=$encoded&type=vod&mediaId=${chooserItem!!.mediaId}&startMs=$start")
                        chooserItem = null
                    }) { Text("Intern") }
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = {
                        scope.launch {
                            val headers = com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeaders(store)
                            ExternalPlayer.open(ctx, chooserItem!!.url!!, headers = headers)
                            chooserItem = null
                        }
                    }) { Text("Extern") }
                }
            }
        }
        if (chooserEpisode != null) {
            ModalBottomSheet(onDismissRequest = { chooserEpisode = null }) {
                val ep = chooserEpisode!!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Wie abspielen?", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    // Build URL from Xtream config
                    val cfg = remember { mutableStateOf<XtreamConfig?>(null) }
                    LaunchedEffect(Unit) {
                        val host = store.xtHost.first(); val user = store.xtUser.first(); val pass = store.xtPass.first(); val out = store.xtOutput.first(); val port = store.xtPort.first()
                        cfg.value = if (host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) XtreamConfig(host, port, user, pass, out) else null
                    }
                    val start = ep.positionSecs.toLong() * 1000L
                    val playUrl = cfg.value?.seriesEpisodeUrl(ep.episodeId, ep.containerExt)
                    Button(onClick = {
                        if (playUrl != null) {
                            val encoded = Uri.encode(playUrl)
                            navController.navigate("player?url=$encoded&type=series&episodeId=${ep.episodeId}&startMs=$start")
                        }
                        chooserEpisode = null
                    }, enabled = playUrl != null) { Text("Intern") }
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = {
                        scope.launch {
                            if (playUrl != null) {
                                val headers = com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeaders(store)
                                ExternalPlayer.open(ctx, playUrl, headers = headers)
                            }
                            chooserEpisode = null
                        }
                    }, enabled = playUrl != null) { Text("Extern") }
                }
            }
        }
        if (vod.isNotEmpty()) {
            SectionTitle(text = "Weiter schauen – Filme")
            // resume-ui: Fallback – wenn onClearVod leer ist, DB aufräumen, sonst Callback verwenden
            val resolvedVodClear: (ResumeVodView) -> Unit = if (onClearVod === DefaultOnClearVod) {
                { item ->
                    try {
                        val dao = db.resumeDao()
                        scope.launch(Dispatchers.IO) { dao.clearVod(item.mediaId) }
                    } catch (_: Throwable) { /* ignore */ }
                }
            } else onClearVod

            ResumeVodRow(
                items = vod,
                // statt sofort Play → Chooser öffnen
                onPlay = { item -> chooserItem = item },
                onClear = resolvedVodClear
            )
            Spacer(Modifier.height(12.dp))
        }
        if (eps.isNotEmpty()) {
            SectionTitle(text = "Weiter schauen – Serien")
            // resume-ui: Fallback – wenn onClearEpisode leer ist, DB aufräumen
            val resolvedEpClear: (ResumeEpisodeView) -> Unit = if (onClearEpisode === DefaultOnClearEpisode) {
                { item ->
                    try {
                        val dao = db.resumeDao()
                        scope.launch(Dispatchers.IO) { dao.clearEpisode(item.episodeId) }
                    } catch (_: Throwable) { /* ignore */ }
                }
            } else onClearEpisode

            ResumeEpisodeRow(
                items = eps,
                onPlay = { item -> chooserEpisode = item },
                onClear = resolvedEpClear
            )
        }
    }
}

@Composable
fun ResumeVodRow(
    items: List<ResumeVodView>,
    onPlay: (ResumeVodView) -> Unit,
    onClear: (ResumeVodView) -> Unit
) {
    val scope = rememberCoroutineScope()
    // Ensure keys remain unique even if upstream accidentally duplicates
    val state = remember(items) { mutableStateOf(items.distinctBy { it.mediaId }) }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.value, key = { it.mediaId }) { v ->
            ResumeCard(
                title = v.name,
                subtitle = "Fortschritt: ${v.positionSecs} s",
                onPlay = { onPlay(v) },
                onClear = {
                    scope.launch {
                        onClear(v)
                        state.removeItem { it.mediaId == v.mediaId }
                    }
                }
            )
        }
    }
}

@Composable
fun ResumeEpisodeRow(
    items: List<ResumeEpisodeView>,
    onPlay: (ResumeEpisodeView) -> Unit,
    onClear: (ResumeEpisodeView) -> Unit
) {
    val scope = rememberCoroutineScope()
    // Ensure keys remain unique even if upstream accidentally duplicates
    val state = remember(items) { mutableStateOf(items.distinctBy { it.episodeId }) }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.value, key = { it.episodeId }) { e ->
            val title = "S${e.season}E${e.episodeNum} – ${e.title}"
            ResumeCard(
                title = title,
                subtitle = "Fortschritt: ${e.positionSecs} s",
                onPlay = { onPlay(e) },
                onClear = {
                    scope.launch {
                        onClear(e)
                        state.removeItem { it.episodeId == e.episodeId }
                    }
                }
            )
        }
    }
}

// resume-ui: Einfache Karte – Titel (2 Zeilen, ellipsize), Fortschritt, Play/Clear
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ResumeCard(
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onClear: () -> Unit
) {
    val ctx = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var hEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hEnabled = SettingsStore(ctx).hapticsEnabled.first() }
    Card(
        modifier = Modifier
            .width(200.dp)
            .height(140.dp)
            .combinedClickable(onClick = onPlay, onLongClick = {
                if (hEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClear()
            })
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Tipp: Klick = Abspielen, Long-Press = Entfernen
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, contentDescription = "Abspielen") }
                IconButton(onClick = onClear) { Icon(Icons.Filled.Clear, contentDescription = "Entfernen") }
            }
        }
    }
}

// resume-ui: Abschnittstitel
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

// resume-ui: Helper – Element aus einem MutableState<List<T>> entfernen
private fun <T> MutableState<List<T>>.removeItem(pred: (T) -> Boolean) {
    value = value.filterNot(pred)
}
