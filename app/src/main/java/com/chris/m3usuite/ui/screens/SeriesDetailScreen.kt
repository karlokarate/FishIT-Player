package com.chris.m3usuite.ui.screens

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.data.db.AppDatabase
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.Episode
import com.chris.m3usuite.data.db.Profile
import com.chris.m3usuite.data.db.ResumeMark
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.data.repo.XtreamRepository
import com.chris.m3usuite.player.ExternalPlayer
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.fx.FadeThrough
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.theme.DesignTokens
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberAvatarModel
import com.chris.m3usuite.ui.util.rememberImageHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Text("Kinder auswählen") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { checked = kids.map { it.id }.toSet() }, enabled = kids.isNotEmpty()) { Text("Alle auswählen") }
                        TextButton(onClick = { checked = emptySet() }, enabled = checked.isNotEmpty()) { Text("Keine auswählen") }
                    }
                }
                items(kids, key = { it.id }) { k ->
                    val isC = k.id in checked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tvClickable {
                                val v = !isC
                                checked = if (v) checked + k.id else checked - k.id
                            },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val model = rememberAvatarModel(k.avatarPath)
                            if (model != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx)
                                        .data(model)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                                    error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                                    fallback = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image)
                                )
                            }
                            Text(k.name)
                        }
                        Switch(checked = isC, onCheckedChange = { v -> checked = if (v) checked + k.id else checked - k.id })
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        val Accent = com.chris.m3usuite.ui.theme.DesignTokens.Accent
                        TextButton(modifier = Modifier.weight(1f).focusScaleOnTv(), onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Accent)) { Text("Abbrechen") }
                        Button(modifier = Modifier.weight(1f).focusScaleOnTv(), onClick = { scope.launch { onConfirm(checked.toList()); onDismiss() } }, enabled = checked.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = androidx.compose.ui.graphics.Color.Black)) { Text("OK") }
                    }
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
                val headers = buildMap<String, String> {
                    val ua = store.userAgent.first(); if (ua.isNotBlank()) put("User-Agent", ua)
                    val ref = store.referer.first(); if (ref.isNotBlank()) put("Referer", ref)
                }

                PlayerChooser.start(
                    context = ctx,
                    store = store,
                    url = playUrl,
                    headers = headers,
                    startPositionMs = startMs
                ) { s -> openInternal?.invoke(playUrl, s, e.episodeId) ?: ExternalPlayer.open(context = ctx, url = playUrl, headers = headers, startPositionMs = s) }
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
    val listState = rememberLazyListState()
    HomeChromeScaffold(
        title = "Serie",
        onSettings = null,
        onSearch = null,
        onProfiles = null,
        onRefresh = null,
        listState = listState,
        bottomBar = {}
    ) { pads ->
    Box(Modifier.fillMaxSize().padding(pads)) {
        val profileIsAdult = isAdult
        val Accent = if (!profileIsAdult) DesignTokens.KidAccent else DesignTokens.Accent
        // Background
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to MaterialTheme.colorScheme.background, 1f to MaterialTheme.colorScheme.surface)))
        Box(Modifier.matchParentSize().background(Brush.radialGradient(colors = listOf(Accent.copy(alpha = if (!profileIsAdult) 0.20f else 0.12f), androidx.compose.ui.graphics.Color.Transparent), radius = with(LocalDensity.current) { 680.dp.toPx() })))
        Image(painter = painterResource(id = com.chris.m3usuite.R.drawable.fisch), contentDescription = null, modifier = Modifier.align(Alignment.Center).size(560.dp).graphicsLayer { alpha = 0.05f; try { if (Build.VERSION.SDK_INT >= 31) renderEffect = android.graphics.RenderEffect.createBlurEffect(36f, 36f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() } catch (_: Throwable) {} })
        com.chris.m3usuite.ui.common.AccentCard(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            accent = Accent
        ) {
        Column(Modifier.animateContentSize()) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Accent.copy(alpha = 0.35f))
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
        if (!plot.isNullOrBlank()) {
            var plotExpanded by remember { mutableStateOf(false) }
            val gradAlpha by animateFloatAsState(if (plotExpanded) 0f else 1f, animationSpec = tween(180), label = "plotGrad")
            Column(Modifier.animateContentSize()) {
                Box(Modifier.fillMaxWidth()) {
                    Text(plot!!, maxLines = if (plotExpanded) Int.MAX_VALUE else 8)
                    if (!plotExpanded) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(48.dp)
                                .graphicsLayer { alpha = gradAlpha }
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        1f to MaterialTheme.colorScheme.background
                                    )
                                )
                        )
                    }
                }
                TextButton(onClick = { plotExpanded = !plotExpanded }) {
                    Text(if (plotExpanded) "Weniger anzeigen" else "Mehr anzeigen")
                }
            }
        }

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
                    val Accent = if (!isAdult) com.chris.m3usuite.ui.theme.DesignTokens.KidAccent else com.chris.m3usuite.ui.theme.DesignTokens.Accent
                    FilterChip(
                        selected = seasonSel == s,
                        onClick = {
                            seasonSel = s
                            scope.launch {
                                val sid = seriesStreamId ?: return@launch
                                episodes = db.episodeDao().episodes(sid, s)
                            }
                        },
                        label = { Text("S$s") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.18f))
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Episodenliste – bekommt restliche Höhe
        val ftKey = remember(seasonSel, episodes.size) { (seasonSel ?: -1) to episodes.size }
        FadeThrough(key = ftKey) {
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
                                val Accent = if (!isAdult) com.chris.m3usuite.ui.theme.DesignTokens.KidAccent else com.chris.m3usuite.ui.theme.DesignTokens.Accent
                                if (resumeSecs != null) {
                                    AssistChip(
                                        modifier = Modifier.focusScaleOnTv(),
                                        onClick = { playEpisode(e, fromStart = false, resumeSecs = resumeSecs) },
                                        label = { Text("Fortsetzen ${fmt(resumeSecs!!)}") },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.22f))
                                    )
                                    AssistChip(
                                        modifier = Modifier.focusScaleOnTv(),
                                        onClick = {
                                            setEpisodeResume(episodeKey, (resumeSecs ?: 0) - 30) { resumeSecs = it }
                                        },
                                        label = { Text("-30s") },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.16f))
                                    )
                                    AssistChip(
                                        modifier = Modifier.focusScaleOnTv(),
                                        onClick = {
                                            setEpisodeResume(episodeKey, (resumeSecs ?: 0) + 30) { resumeSecs = it }
                                        },
                                        label = { Text("+30s") },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.16f))
                                    )
                                    AssistChip(
                                        modifier = Modifier.focusScaleOnTv(),
                                        onClick = {
                                            setEpisodeResume(episodeKey, (resumeSecs ?: 0) + 300) { resumeSecs = it }
                                        },
                                        label = { Text("+5m") },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.16f))
                                    )
                                    AssistChip(
                                        modifier = Modifier.focusScaleOnTv(),
                                        onClick = {
                                            clearEpisodeResume(episodeKey) { resumeSecs = null }
                                        },
                                        label = { Text("Zurücksetzen") },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.10f))
                                    )
                                } else {
                                    AssistChip(
                                        modifier = Modifier.focusScaleOnTv(),
                                        onClick = {
                                            setEpisodeResume(episodeKey, 0) { resumeSecs = it }
                                        },
                                        label = { Text("Resume setzen") },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.20f))
                                    )
                                }
                                AssistChip(
                                    modifier = Modifier.focusScaleOnTv(),
                                    onClick = { playEpisode(e, fromStart = true) },
                                    label = { Text("Von Anfang") },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = Accent.copy(alpha = 0.22f))
                                )
                                if (isAdult) {
                                    com.chris.m3usuite.ui.common.AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.AddKid, variant = com.chris.m3usuite.ui.common.IconVariant.Solid, contentDescription = "Für Kinder freigeben", onClick = { showGrantSheet = true })
                                    com.chris.m3usuite.ui.common.AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.RemoveKid, variant = com.chris.m3usuite.ui.common.IconVariant.Solid, contentDescription = "Aus Kinderprofil entfernen", onClick = { showRevokeSheet = true })
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
}

private fun fmt(totalSecs: Int): String {
    val s = max(0, totalSecs)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
