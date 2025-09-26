package com.chris.m3usuite.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.foundation.focusable
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.fx.tvFocusGlow
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusEvent
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
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import com.chris.m3usuite.player.ExternalPlayer
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.repo.ResumeRepository
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.core.playback.PlayUrlHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

// Resume-UI: Material3

// resume-ui: Default sentinels to detect empty callbacks for clear actions
data class VodResume(val mediaId: Long, val name: String, val url: String?, val positionSecs: Int, val containerExt: String? = null)
data class SeriesResume(
    val seriesId: Int,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val url: String?,
    val positionSecs: Int,
    val episodeId: Int? = null,
    val containerExt: String? = null
)
private val DefaultOnClearVod: (VodResume) -> Unit = {}
private val DefaultOnClearEpisode: (SeriesResume) -> Unit = {}

// resume-ui: chooser Intern vs Extern (NavController + ExternalPlayer)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeSectionAuto(
    navController: NavController,
    limit: Int = 20,
    onPlayVod: (VodResume) -> Unit = {},
    onPlayEpisode: (SeriesResume) -> Unit = {},
    onClearVod: (VodResume) -> Unit = DefaultOnClearVod,
    onClearEpisode: (SeriesResume) -> Unit = DefaultOnClearEpisode
) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val mediaRepo = remember { com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, store) }
    val obx = remember { ObxStore.get(ctx) }
    val resumeRepo = remember { ResumeRepository(ctx) }
    val http = remember(ctx) { com.chris.m3usuite.core.http.HttpClientFactory.create(ctx, store) }
    suspend fun buildClient(): XtreamClient? {
        val host = store.xtHost.first(); val user = store.xtUser.first(); val pass = store.xtPass.first(); val port = store.xtPort.first()
        if (host.isBlank() || user.isBlank() || pass.isBlank()) return null
        val scheme = if (port == 443) "https" else "http"
        val caps = com.chris.m3usuite.core.xtream.ProviderCapabilityStore(ctx)
        val portStore = com.chris.m3usuite.core.xtream.EndpointPortStore(ctx)
        return XtreamClient(http).also { it.initialize(scheme, host, user, pass, basePath = null, store = caps, portStore = portStore, portOverride = port) }
    }

    var vod by remember { mutableStateOf<List<VodResume>>(emptyList()) }
    var eps by remember { mutableStateOf<List<SeriesResume>>(emptyList()) }
    // resume-ui: Chooser BottomSheet Intern vs Extern
    var chooserItem by remember { mutableStateOf<VodResume?>(null) }
    var chooserEpisode by remember { mutableStateOf<SeriesResume?>(null) }
    // store already defined above

    // resume-ui: Laden beider Listen auf IO-Thread
    LaunchedEffect(Unit) {
        try {
            val client = withContext(Dispatchers.IO) { buildClient() }
            val v = withContext(Dispatchers.IO) {
                resumeRepo.recentVod(limit).mapNotNull { mark ->
                    val enc = mark.mediaId
                    val vid = (enc - 2_000_000_000_000L).toInt()
                    val vodBox = obx.boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)
                    val row = vodBox.query(com.chris.m3usuite.data.obx.ObxVod_.vodId.equal(vid.toLong())).build().findFirst()
                    val name = row?.name ?: "VOD $vid"
                    val url = client?.buildVodPlayUrl(vid, row?.containerExt)
                    VodResume(mediaId = enc, name = name, url = url, positionSecs = mark.positionSecs, containerExt = row?.containerExt)
                }
            }
            val e = withContext(Dispatchers.IO) {
                resumeRepo.recentEpisodes(limit).mapNotNull { mk ->
                    val epBox = obx.boxFor(com.chris.m3usuite.data.obx.ObxEpisode::class.java)
                    val ep = epBox.query(
                        com.chris.m3usuite.data.obx.ObxEpisode_.seriesId.equal(mk.seriesId.toLong())
                            .and(com.chris.m3usuite.data.obx.ObxEpisode_.season.equal(mk.season.toLong()))
                            .and(com.chris.m3usuite.data.obx.ObxEpisode_.episodeNum.equal(mk.episodeNum.toLong()))
                    ).build().findFirst()
                    val title = ep?.title ?: "S${mk.season}E${mk.episodeNum}"
                    val episodeId = ep?.episodeId?.takeIf { it > 0 }
                    val rawUrl = client?.buildSeriesEpisodePlayUrl(
                        seriesId = mk.seriesId,
                        season = mk.season,
                        episode = mk.episodeNum,
                        episodeExt = ep?.playExt,
                        episodeId = episodeId
                    )
                    val url = rawUrl
                    SeriesResume(
                        seriesId = mk.seriesId,
                        season = mk.season,
                        episodeNum = mk.episodeNum,
                        title = title,
                        url = url,
                        positionSecs = mk.positionSecs,
                        episodeId = episodeId,
                        containerExt = ep?.playExt
                    )
                }
            }
            // Filter by effective allow-set for non-adult profiles
            val prof = withContext(Dispatchers.IO) { obx.boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java).get(store.currentProfileId.first()) }
            if (prof?.type == "adult") {
                vod = v; eps = e
            } else {
                val allowedVodIds = withContext(Dispatchers.IO) { mediaRepo.listByTypeFiltered("vod", 100000, 0).map { it.id }.toSet() }
                val seriesAllowedStreamIds = withContext(Dispatchers.IO) { mediaRepo.listByTypeFiltered("series", 100000, 0).mapNotNull { it.streamId }.toSet() }
                vod = v.filter { it.mediaId in allowedVodIds }
                eps = e.filter { it.seriesId in seriesAllowedStreamIds }
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
                        val mime = PlayUrlHelper.guessMimeType(chooserItem!!.url, chooserItem!!.containerExt)
                        val mimeArg = mime?.let { Uri.encode(it) } ?: ""
                        navController.navigate("player?url=$encoded&type=vod&mediaId=${chooserItem!!.mediaId}&startMs=$start&mime=$mimeArg")
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
                    val start = ep.positionSecs.toLong() * 1000L
                    val playUrl = ep.url
                    val mime = PlayUrlHelper.guessMimeType(playUrl, ep.containerExt)
                    val mimeArg = mime?.let { Uri.encode(it) } ?: ""
                    Button(onClick = {
                        if (playUrl != null) {
                            val encoded = Uri.encode(playUrl)
                            val epId = ep.episodeId?.takeIf { it > 0 } ?: -1
                            navController.navigate(
                                "player?url=$encoded&type=series&mediaId=-1&seriesId=${ep.seriesId}&season=${ep.season}&episodeNum=${ep.episodeNum}&episodeId=$epId&startMs=$start&mime=$mimeArg"
                            )
                        }
                        chooserEpisode = null
                    }, enabled = playUrl != null) { Text("Intern") }
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = {
                        scope.launch {
                            if (playUrl != null && !playUrl.startsWith("tg://")) {
                                val headers = com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeaders(store)
                                ExternalPlayer.open(ctx, playUrl, headers = headers)
                            }
                            chooserEpisode = null
                        }
                    }, enabled = playUrl != null && !playUrl.startsWith("tg://")) { Text("Extern") }
                }
            }
        }
        if (vod.isNotEmpty()) {
            SectionTitle(text = "Weiter schauen – Filme")
            // resume-ui: Fallback – wenn onClearVod leer ist, DB aufräumen, sonst Callback verwenden
            val resolvedVodClear: (VodResume) -> Unit = if (onClearVod === DefaultOnClearVod) {
                { item ->
                    try {
                        scope.launch(Dispatchers.IO) { resumeRepo.clearVod(item.mediaId) }
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
            val resolvedEpClear: (SeriesResume) -> Unit = if (onClearEpisode === DefaultOnClearEpisode) {
                { item ->
                    // OBX series resume not persisted per-episode yet; handled via set during playback
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
    items: List<VodResume>,
    onPlay: (VodResume) -> Unit,
    onClear: (VodResume) -> Unit
) {
    val scope = rememberCoroutineScope()
    // Ensure keys remain unique even if upstream accidentally duplicates
    val state = remember(items) { mutableStateOf(items.distinctBy { it.mediaId }) }

    run {
        val listState = com.chris.m3usuite.ui.state.rememberRouteListState("resume:vod")
        com.chris.m3usuite.ui.tv.TvFocusRow(
            items = state.value,
            key = { it.mediaId },
            listState = listState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) { _, v, itemMod ->
            ResumeCard(
                modifier = itemMod,
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
    items: List<SeriesResume>,
    onPlay: (SeriesResume) -> Unit,
    onClear: (SeriesResume) -> Unit
) {
    val scope = rememberCoroutineScope()
    // Ensure keys remain unique even if upstream accidentally duplicates
    val state = remember(items) { mutableStateOf(items.distinctBy { Triple(it.seriesId, it.season, it.episodeNum) }) }

    run {
        val listState = com.chris.m3usuite.ui.state.rememberRouteListState("resume:series")
        com.chris.m3usuite.ui.tv.TvFocusRow(
            items = state.value,
            key = { "${it.seriesId}:${it.season}:${it.episodeNum}" },
            listState = listState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) { _, e, itemMod ->
            val title = "S${e.season}E${e.episodeNum} – ${e.title}"
            ResumeCard(
                modifier = itemMod,
                title = title,
                subtitle = "Fortschritt: ${e.positionSecs} s",
                onPlay = { onPlay(e) },
                onClear = {
                    scope.launch {
                        onClear(e)
                        state.removeItem { it.seriesId == e.seriesId && it.season == e.season && it.episodeNum == e.episodeNum }
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
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onClear: () -> Unit
) {
    val ctx = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var hEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hEnabled = SettingsStore(ctx).hapticsEnabled.first() }
    var focused by remember { mutableStateOf(false) }
    val shape: Shape = RoundedCornerShape(14.dp)
    Card(
        modifier = Modifier
            .then(modifier)
            .focusable()
            .onFocusEvent { focused = it.isFocused || it.hasFocus }
            .focusScaleOnTv(
                focusedScale = 1.40f,
                pressedScale = 1.40f,
                focusColors = com.chris.m3usuite.ui.skin.TvFocusColors(
                    focusFill = Color.White.copy(alpha = 0.28f),
                    focusBorder = Color.White.copy(alpha = 0.92f),
                    pressedFill = Color.White.copy(alpha = 0.32f),
                    pressedBorder = Color.White.copy(alpha = 1.0f)
                ),
                focusBorderWidth = 2.5.dp
            )
            .tvFocusGlow(focused = focused, shape = shape, ringWidth = 5.dp)
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
