package com.chris.m3usuite.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavController
import com.chris.m3usuite.player.ExternalPlayer
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.ResumeEpisodeView
import com.chris.m3usuite.data.db.ResumeVodView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// resume-ui: Phase 3 – Resume-Karusselle (ohne Bilder), Material (nicht Material3)

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

    var vod by remember { mutableStateOf<List<ResumeVodView>>(emptyList()) }
    var eps by remember { mutableStateOf<List<ResumeEpisodeView>>(emptyList()) }
    // resume-ui: Chooser BottomSheet Intern vs Extern
    var chooserItem by remember { mutableStateOf<ResumeVodView?>(null) }

    // resume-ui: Laden beider Listen auf IO-Thread
    LaunchedEffect(Unit) {
        try {
            val dao = db.resumeDao()
            val v = withContext(Dispatchers.IO) { dao.recentVod(limit) }
            val e = withContext(Dispatchers.IO) { dao.recentEpisodes(limit) }
            vod = v
            eps = e
        } catch (_: Throwable) {
            // Ignorieren – bleibt leer
        }
    }

    val scope = rememberCoroutineScope()

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
                    Text("Wie abspielen?", style = MaterialTheme.typography.h6)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val encoded = Uri.encode(chooserItem!!.url!!)
                        val start = chooserItem!!.positionSecs.toLong() * 1000L
                        navController.navigate("player?url=$encoded&type=vod&mediaId=${chooserItem!!.mediaId}&startMs=$start")
                        chooserItem = null
                    }) { Text("Intern") }
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = {
                        ExternalPlayer.open(ctx, chooserItem!!.url!!, headers = emptyMap())
                        chooserItem = null
                    }) { Text("Extern") }
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
                // TODO: Episoden-Chooser (noch kein Chooser)
                onPlay = onPlayEpisode,
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
    val state = remember(items) { mutableStateOf(items) }

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
    val state = remember(items) { mutableStateOf(items) }

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
private fun ResumeCard(
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .height(140.dp)
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
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Abspielen")
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Clear, contentDescription = "Entfernen")
                }
            }
        }
    }
}

// resume-ui: Abschnittstitel
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.h6,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

// resume-ui: Helper – Element aus einem MutableState<List<T>> entfernen
private fun <T> MutableState<List<T>>.removeItem(pred: (T) -> Boolean) {
    value = value.filterNot(pred)
}
