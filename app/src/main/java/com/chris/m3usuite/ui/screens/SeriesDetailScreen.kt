package com.chris.m3usuite.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.data.db.AppDatabase
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.Episode
import com.chris.m3usuite.data.db.ResumeMark
import com.chris.m3usuite.data.repo.XtreamRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.player.ExternalPlayer
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.data.db.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil3.request.ImageRequest
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberImageHeaders

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    id: Long,
    // optionaler Callback für internen Player (url, startMs, episodeId)
    openInternal: ((url: String, startMs: Long?, episodeId: Int) -> Unit)? = null
) {
    val ctx = LocalContext.current
    val db: AppDatabase = remember { DbProvider.get(ctx) }
    val store = remember { SettingsStore(ctx) }
    val repo = remember { XtreamRepository(ctx, store) }
    val scope = rememberCoroutineScope()
    val headers = rememberImageHeaders()
    val kidRepo = remember { KidContentRepository(ctx) }
    val profileId by store.currentProfileId.collectAsState(initial = -1L)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) { isAdult = withContext(Dispatchers.IO) { DbProvider.get(ctx).profileDao().byId(profileId)?.type != "kid" } }
    var showGrantSheet by rememberSaveable { mutableStateOf(false) }
    var showRevokeSheet by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun KidSelectSheet(onConfirm: suspend (kidIds: List<Long>) -> Unit, onDismiss: () -> Unit) {
        var kids by remember { mutableStateOf<List<Profile>>(emptyList()) }
        LaunchedEffect(profileId) { kids = withContext(Dispatchers.IO) { DbProvider.get(ctx).profileDao().all().filter { it.type == "kid" } } }
        var checked by remember { mutableStateOf(setOf<Long>()) }
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.padding(16.dp)) {
                Text("Kinder auswählen")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { checked = kids.map { it.id }.toSet() }, enabled = kids.isNotEmpty()) { Text("Alle auswählen") }
                    TextButton(onClick = { checked = emptySet() }, enabled = checked.isNotEmpty()) { Text("Keine auswählen") }
                }
                Spacer(Modifier.height(4.dp))
                kids.forEach { k ->
                    val isC = k.id in checked
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!k.avatarPath.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx).data(k.avatarPath).build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(k.name)
                        }
                        Switch(checked = isC, onCheckedChange = { v -> checked = if (v) checked + k.id else checked - k.id })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Button(onClick = { scope.launch { onConfirm(checked.toList()); onDismiss() } }, enabled = checked.isNotEmpty()) { Text("OK") }
                }
            }
        }
    }

    var title by remember { mutableStateOf("") }
    var poster by remember { mutableStateOf<String?>(null) }
    var plot by remember { mutableStateOf<String?>(null) }
    var seriesStreamId by remember { mutableStateOf<Int?>(null) }
    var seasons by remember { mutableStateOf<List<Int>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var seasonSel by remember { mutableStateOf<Int?>(null) }

    // Daten laden
    LaunchedEffect(id) {
        val item = db.mediaDao().byId(id) ?: return@LaunchedEffect
        title = item.name
        poster = item.poster
        plot = item.plot
        seriesStreamId = item.streamId

        val sid = seriesStreamId ?: return@LaunchedEffect
        repo.loadSeriesInfo(sid).getOrElse { 0 }
        seasons = db.episodeDao().seasons(sid)
        seasonSel = seasons.firstOrNull()
        seasonSel?.let { episodes = db.episodeDao().episodes(sid, it) }
    }

    fun playEpisode(e: Episode, fromStart: Boolean = false, resumeSecs: Int? = null) {
        scope.launch {
            val host = store.xtHost.first()
            val user = store.xtUser.first()
            val pass = store.xtPass.first()
            val out  = store.xtOutput.first()
            val port = store.xtPort.first()
            if (host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                val cfg = XtreamConfig(host, port, user, pass, out)
                val startMs: Long? = if (!fromStart) resumeSecs?.toLong()?.times(1000) else null
                val playUrl = cfg.seriesEpisodeUrl(e.episodeId, e.containerExt)

                PlayerChooser.start(
                    context = ctx,
                    store = store,
                    url = playUrl,
                    headers = emptyMap(),
                    startPositionMs = startMs
                ) { s -> openInternal?.invoke(playUrl, s, e.episodeId) ?: ExternalPlayer.open(context = ctx, url = playUrl, startPositionMs = s) }
            }
        }
    }

    // Resume lesen/setzen/löschen
    suspend fun getEpisodeResume(episodeKey: Int): Int? =
        db.resumeDao().getEpisode(episodeKey)?.positionSecs

    fun setEpisodeResume(episodeKey: Int, newSecs: Int, onUpdated: (Int) -> Unit) {
        scope.launch {
            val pos = max(0, newSecs)
            db.resumeDao().upsert(
                ResumeMark(
                    type = "series",
                    mediaId = null,
                    episodeId = episodeKey,
                    positionSecs = pos,
                    updatedAt = System.currentTimeMillis()
                )
            )
            onUpdated(pos)
        }
    }

    fun clearEpisodeResume(episodeKey: Int, onCleared: () -> Unit) {
        scope.launch {
            db.resumeDao().clearEpisode(episodeKey)
            onCleared()
        }
    }

    val snackHost = remember { SnackbarHostState() }
    Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { pad ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(pad)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        AsyncImage(
            model = buildImageRequest(ctx, poster, headers),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(220.dp)
                .fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        if (!plot.isNullOrBlank()) Text(plot!!)

        Spacer(Modifier.height(12.dp))

        // Staffel-Auswahl (scrollbar)
        if (seasons.isNotEmpty()) {
            Text("Staffeln:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(seasons, key = { it }) { s ->
                    FilterChip(
                        selected = seasonSel == s,
                        onClick = {
                            seasonSel = s
                            scope.launch {
                                val sid = seriesStreamId ?: return@launch
                                episodes = db.episodeDao().episodes(sid, s)
                            }
                        },
                        label = { Text("S$s") }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Episodenliste – bekommt restliche Höhe
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(episodes, key = { it.episodeId }) { e ->
                val episodeKey = e.episodeId
                var resumeSecs by remember(episodeKey) { mutableStateOf<Int?>(null) }

                // Resume für diese Episode laden
                LaunchedEffect(episodeKey) {
                    resumeSecs = getEpisodeResume(episodeKey)
                }

                ListItem(
                    headlineContent = { Text("S${e.season}E${e.episodeNum}  ${e.title}") },
                    supportingContent = {
                        Column {
                            if (!e.plot.isNullOrBlank()) {
                                Text(e.plot)
                                Spacer(Modifier.height(6.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (resumeSecs != null) {
                                    AssistChip(
                                        onClick = { playEpisode(e, fromStart = false, resumeSecs = resumeSecs) },
                                        label = { Text("Fortsetzen ${fmt(resumeSecs!!)}") }
                                    )
                                    AssistChip(
                                        onClick = {
                                            setEpisodeResume(episodeKey, (resumeSecs ?: 0) - 30) { resumeSecs = it }
                                        },
                                        label = { Text("-30s") }
                                    )
                                    AssistChip(
                                        onClick = {
                                            setEpisodeResume(episodeKey, (resumeSecs ?: 0) + 30) { resumeSecs = it }
                                        },
                                        label = { Text("+30s") }
                                    )
                                    AssistChip(
                                        onClick = {
                                            setEpisodeResume(episodeKey, (resumeSecs ?: 0) + 300) { resumeSecs = it }
                                        },
                                        label = { Text("+5m") }
                                    )
                                    AssistChip(
                                        onClick = {
                                            clearEpisodeResume(episodeKey) { resumeSecs = null }
                                        },
                                        label = { Text("Zurücksetzen") }
                                    )
                                } else {
                                    AssistChip(
                                        onClick = {
                                            setEpisodeResume(episodeKey, 0) { resumeSecs = it }
                                        },
                                        label = { Text("Resume setzen") }
                                    )
                                }
                                AssistChip(
                                    onClick = { playEpisode(e, fromStart = true) },
                                    label = { Text("Von Anfang") }
                                )
                                if (isAdult) {
                                    TextButton(onClick = { showGrantSheet = true }) { Text("Für Kind(er) freigeben…") }
                                    TextButton(onClick = { showRevokeSheet = true }) { Text("Entfernen…") }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (resumeSecs != null) playEpisode(e, fromStart = false, resumeSecs = resumeSecs)
                            else playEpisode(e, fromStart = true)
                        }
                )
                HorizontalDivider()
            }
        }
    if (showGrantSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.allowBulk(it, "series", listOf(id)) } }
        scope.launch { snackHost.showSnackbar("Serie freigegeben für ${kidIds.size} Kinder") }
        showGrantSheet = false
    }, onDismiss = { showGrantSheet = false })
    if (showRevokeSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.disallowBulk(it, "series", listOf(id)) } }
        scope.launch { snackHost.showSnackbar("Serie aus ${kidIds.size} Kinderprofil(en) entfernt") }
        showRevokeSheet = false
    }, onDismiss = { showRevokeSheet = false })
}
}

}

private fun fmt(totalSecs: Int): String {
    val s = max(0, totalSecs)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
