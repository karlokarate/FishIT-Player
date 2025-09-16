package com.chris.m3usuite.ui.screens

import androidx.compose.animation.animateContentSize
// AnimatedVisibility not used to keep compatibility with older compose
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
// stickyHeader not available on current Compose; emulate with overlay instead
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chris.m3usuite.core.xtream.XtreamConfig
// Room access moved behind feature gate for OBX-first
import com.chris.m3usuite.model.Episode
// Room removed
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.player.InternalPlayerScreen
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.fx.FadeThrough
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.theme.DesignTokens
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberAvatarModel
import com.chris.m3usuite.ui.util.rememberImageHeaders
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.derivedStateOf
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SeriesDetailScreen(
    id: Long,
    // optionaler Callback für internen Player (url, startMs, seriesId, season, episodeNum)
    openInternal: ((url: String, startMs: Long?, seriesId: Int, season: Int, episodeNum: Int) -> Unit)? = null,
    onLogo: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val roomEnabled by store.roomEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val headers = rememberImageHeaders()
    val kidRepo = remember { KidContentRepository(ctx) }
    val profileId by store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) { isAdult = withContext(Dispatchers.IO) { com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java).get(profileId)?.type != "kid" } }
    val mediaRepo = remember { com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, store) }
    var contentAllowed by remember { mutableStateOf(true) }
    LaunchedEffect(id, profileId) {
        val prof = withContext(Dispatchers.IO) { com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java).get(profileId) }
        val adult = prof?.type == "adult"
        contentAllowed = if (adult) true else mediaRepo.isAllowed("series", id)
    }
    var showGrantSheet by rememberSaveable { mutableStateOf(false) }
    var showRevokeSheet by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun KidSelectSheet(onConfirm: suspend (kidIds: List<Long>) -> Unit, onDismiss: () -> Unit) {
    var kids by remember { mutableStateOf<List<com.chris.m3usuite.data.obx.ObxProfile>>(emptyList()) }
        LaunchedEffect(profileId) { kids = withContext(Dispatchers.IO) { com.chris.m3usuite.data.repo.ProfileObxRepository(ctx).all().filter { it.type == "kid" } } }
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
    var year by remember { mutableStateOf<Int?>(null) }

    fun cleanSeriesName(raw: String, year: Int?): String {
        // Many providers prefix the category before a hyphen, e.g. "4K-A+ - Berlin ER (2025) (DE)".
        // Use the portion AFTER the first hyphen as the actual title when present.
        var base = raw.substringAfter(" - ", raw)
        // Remove year parentheses if supplied
        if (year != null) base = base.replace(" (" + year + ")", "")
        // Remove country codes like (DE), (EN), (US/UK)
        base = base.replace(Regex("\\s*\\(([A-Z]{2,3})(/[A-Z]{2,3})*\\)\\s*"), " ")
        return base.trim()
    }

    fun cleanEpisodeTitle(raw: String?, seriesName: String?): String {
        // Prefer last segment after ' - ' which usually is the true episode title
        val lastSeg = raw.orEmpty().split(" - ").lastOrNull()?.trim().orEmpty()
        var t = if (lastSeg.isNotEmpty()) lastSeg else raw.orEmpty()
        // Remove series name prefix if present
        if (!seriesName.isNullOrBlank()) {
            t = t.removePrefix(seriesName).trim()
        }
        // Remove year (yyyy) and country codes in parentheses
        t = t.replace(Regex("\\s*\\(\\d{4}\\)\\s*"), " ")
        t = t.replace(Regex("\\s*\\(([A-Z]{2,3})(/[A-Z]{2,3})*\\)\\s*"), " ")
        // Remove embedded season/episode markers like S01E01
        t = t.replace(Regex("(?i)S\\d{1,2}E\\d{1,3}"), " ")
        // Collapse extra separators
        t = t.replace(Regex("[-:–]+"), " ")
        return t.trim()
    }

    // Daten laden
    LaunchedEffect(id) {
        fun decodeObxSeriesId(v: Long): Int? = if (v >= 3_000_000_000_000L) (v - 3_000_000_000_000L).toInt() else null
        val obxSid = decodeObxSeriesId(id)
        if (obxSid != null) {
            seriesStreamId = obxSid
            val box = com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxSeries::class.java)
            val row = box.query(com.chris.m3usuite.data.obx.ObxSeries_.seriesId.equal(obxSid.toLong())).build().findFirst()
            title = row?.name.orEmpty()
            poster = row?.imagesJson?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonArray.firstOrNull()?.jsonPrimitive?.content }.getOrNull() }
            plot = row?.plot
            year = row?.year
        } else {
            // No Room fallback available for legacy ID
            return@LaunchedEffect
        }
        val sid = seriesStreamId ?: return@LaunchedEffect
        // Prefer ObjectBox episodes if present; fallback to Room/import path
        val obx = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
        val obxEps = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.episodesForSeries(sid) }
        if (obxEps.isNotEmpty()) {
            val allSeasons = obxEps.map { it.season }.distinct().sorted()
            seasons = allSeasons
            seasonSel = allSeasons.firstOrNull()
            val season = seasonSel
            if (season != null) {
                episodes = obxEps.filter { it.season == season }.map { e ->
                        com.chris.m3usuite.model.Episode(
                            seriesStreamId = sid,
                            episodeId = 0, // 0 → use series/season/episode for play URL
                            season = e.season,
                            episodeNum = e.episodeNum,
                            title = e.title ?: "Episode ${e.episodeNum}",
                            plot = e.plot,
                            durationSecs = e.durationSecs,
                            rating = e.rating,
                            airDate = e.airDate,
                            containerExt = e.playExt,
                            poster = null
                        )
                }
            }
        } else {
            // Import to OBX and try again
            withContext(kotlinx.coroutines.Dispatchers.IO) { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store).importSeriesDetailOnce(sid) }
            val obxEps2 = withContext(kotlinx.coroutines.Dispatchers.IO) { obx.episodesForSeries(sid) }
            if (obxEps2.isNotEmpty()) {
                val allSeasons = obxEps2.map { it.season }.distinct().sorted()
                seasons = allSeasons
                seasonSel = allSeasons.firstOrNull()
                val season = seasonSel
                if (season != null) {
                        episodes = obxEps2.filter { it.season == season }.map { e ->
                            com.chris.m3usuite.model.Episode(
                                seriesStreamId = sid,
                                episodeId = 0,
                                season = e.season,
                                episodeNum = e.episodeNum,
                                title = e.title ?: "Episode ${e.episodeNum}",
                                plot = e.plot,
                                durationSecs = e.durationSecs,
                                rating = e.rating,
                                airDate = e.airDate,
                                containerExt = e.playExt,
                                poster = null
                            )
                        }
                }
            }
        }
    }

    // --- Interner Player Zustand (Fullscreen) ---
    var showInternal by rememberSaveable { mutableStateOf(false) }
    var internalUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var internalStartMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var internalEpisodeId by rememberSaveable { mutableStateOf<Int?>(null) }
    var internalUa by rememberSaveable { mutableStateOf("") }
    var internalRef by rememberSaveable { mutableStateOf("") }
    var nextHintEpisodeId by rememberSaveable { mutableStateOf<Int?>(null) }
    var nextHintText by rememberSaveable { mutableStateOf<String?>(null) }
    var resumeRefreshKey by rememberSaveable { mutableStateOf(0) }

    if (showInternal) {
        val hdrs = buildMap<String, String> {
            if (internalUa.isNotBlank()) put("User-Agent", internalUa)
            if (internalRef.isNotBlank()) put("Referer", internalRef)
        }
        InternalPlayerScreen(
            url = internalUrl.orEmpty(),
            type = "series",
            episodeId = internalEpisodeId,
            startPositionMs = internalStartMs,
            headers = hdrs,
            onExit = { showInternal = false }
        )
        return
    }

    // After player exit: suggest next episode briefly
    LaunchedEffect(showInternal) {
        if (!showInternal && internalEpisodeId != null) {
            // Room removed: cannot compute next by episodeId; skip hint
            nextHintEpisodeId = null
            nextHintText = null
            resumeRefreshKey++
        }
    }

    fun playEpisode(e: Episode, fromStart: Boolean = false, resumeSecs: Int? = null) {
        scope.launch {
            if (!contentAllowed) {
                android.widget.Toast.makeText(ctx, "Nicht freigegeben", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val startMs: Long? = if (!fromStart) resumeSecs?.toLong()?.times(1000) else null
            // Prefer Telegram if episode has TG refs
            val tgUrl = if (e.tgChatId != null && e.tgMessageId != null) "tg://message?chatId=${e.tgChatId}&messageId=${e.tgMessageId}" else null
            val headers = com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeaders(store)
            val urlToPlay = tgUrl ?: run {
                val snap = store.snapshot()
                if (snap.xtHost.isNotBlank() && snap.xtUser.isNotBlank() && snap.xtPass.isNotBlank()) {
                    val scheme = if (snap.xtPort == 443) "https" else "http"
                    val http = com.chris.m3usuite.core.http.HttpClientFactory.create(ctx, store)
                    val client = com.chris.m3usuite.core.xtream.XtreamClient(http)
                    val caps = com.chris.m3usuite.core.xtream.ProviderCapabilityStore(ctx)
                    val ports = com.chris.m3usuite.core.xtream.EndpointPortStore(ctx)
                    client.initialize(
                        scheme = scheme,
                        host = snap.xtHost,
                        username = snap.xtUser,
                        password = snap.xtPass,
                        basePath = null,
                        store = caps,
                        portStore = ports,
                        portOverride = snap.xtPort
                    )
                    client.buildSeriesEpisodePlayUrl(seriesStreamId ?: 0, e.season, e.episodeNum, e.containerExt)
                } else null
            } ?: return@launch

            PlayerChooser.start(
                context = ctx,
                store = store,
                url = urlToPlay,
                headers = headers,
                startPositionMs = startMs
            ) { s ->
                    if (openInternal != null) {
                    openInternal(urlToPlay, s, seriesStreamId ?: 0, e.season, e.episodeNum)
                } else {
                    internalUrl = urlToPlay
                    internalEpisodeId = null
                    internalStartMs = s
                    internalUa = headers["User-Agent"].orEmpty()
                    internalRef = headers["Referer"].orEmpty()
                    showInternal = true
                }
            }
        }
    }

    // Resume lesen/setzen/löschen (OBX-backed)
    suspend fun getEpisodeResume(episodeKey: Int): Int? = null

    fun setEpisodeResume(episodeKey: Int, newSecs: Int, onUpdated: (Int) -> Unit) {
        scope.launch {
            val pos = max(0, newSecs)
            // Room removed: episode resume by episodeId not supported in this path
            onUpdated(pos)
        }
    }

    fun clearEpisodeResume(episodeKey: Int, onCleared: () -> Unit) {
        scope.launch {
            // Room removed: episode resume by episodeId not supported in this path
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
        onLogo = onLogo,
        bottomBar = {}
    ) { pads ->
    Box(Modifier.fillMaxSize().padding(pads)) {
        val profileIsAdult = isAdult
        val Accent = if (!profileIsAdult) DesignTokens.KidAccent else DesignTokens.Accent
        // Unified badge container color; plot will be ~15% darker
        val badgeColor = if (!profileIsAdult) Accent.copy(alpha = 0.26f) else Accent.copy(alpha = 0.20f)
        val badgeColorDarker = if (!profileIsAdult) Accent.copy(alpha = 0.32f) else Accent.copy(alpha = 0.26f)
        // Background
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to MaterialTheme.colorScheme.background, 1f to MaterialTheme.colorScheme.surface)))
        Box(Modifier.matchParentSize().background(Brush.radialGradient(colors = listOf(Accent.copy(alpha = if (!profileIsAdult) 0.20f else 0.12f), androidx.compose.ui.graphics.Color.Transparent), radius = with(LocalDensity.current) { 680.dp.toPx() })))
        com.chris.m3usuite.ui.fx.FishBackground(
            modifier = Modifier.align(Alignment.Center).size(560.dp),
            alpha = 0.05f
        )
        com.chris.m3usuite.ui.common.AccentCard(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            accent = Accent
        ) {
        val ftKey = remember(seasonSel, episodes.size) { (seasonSel ?: -1) to episodes.size }
        FadeThrough(key = ftKey) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    val cleanTitle = remember(title, year) { cleanSeriesName(title, year) }
                    Surface(shape = RoundedCornerShape(50), color = badgeColor, contentColor = Color.White, modifier = Modifier.graphicsLayer(alpha = DesignTokens.BadgeAlpha)) {
                        Text(
                            if (year != null) "$cleanTitle ($year)" else cleanTitle,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Accent.copy(alpha = 0.35f))
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = buildImageRequest(ctx, poster, headers),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.height(220.dp).fillMaxWidth()
                    )
                    if (isAdult) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            com.chris.m3usuite.ui.common.AppIconButton(
                                icon = com.chris.m3usuite.ui.common.AppIcon.AddKid,
                                variant = com.chris.m3usuite.ui.common.IconVariant.Solid,
                                contentDescription = "Für Kinder freigeben",
                                onClick = { showGrantSheet = true }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (!plot.isNullOrBlank()) {
                item {
                    var plotExpanded by remember { mutableStateOf(false) }
                    val gradAlpha by animateFloatAsState(if (plotExpanded) 0f else 1f, animationSpec = tween(180), label = "plotGrad")
                    Column(Modifier.animateContentSize()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeColorDarker,
                            contentColor = Color.White,
                            modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                        ) {
                            Text(
                                plot!!,
                                maxLines = if (plotExpanded) Int.MAX_VALUE else 8,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        TextButton(onClick = { plotExpanded = !plotExpanded }) {
                            Text(if (plotExpanded) "Weniger anzeigen" else "Mehr anzeigen")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (seasons.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(50), color = badgeColor, contentColor = Color.White, modifier = Modifier.graphicsLayer(alpha = DesignTokens.BadgeAlpha)) {
                        Text("Staffeln", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(seasons, key = { it }) { s ->
                            val Accent = if (!isAdult) com.chris.m3usuite.ui.theme.DesignTokens.KidAccent else com.chris.m3usuite.ui.theme.DesignTokens.Accent
                            FilterChip(
                                modifier = Modifier.graphicsLayer(alpha = DesignTokens.BadgeAlpha),
                                selected = seasonSel == s,
                                onClick = {
                                    seasonSel = s
                                    scope.launch {
                                        val sid = seriesStreamId ?: return@launch
                                        val obxRepo = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
                                        val eps = withContext(kotlinx.coroutines.Dispatchers.IO) { obxRepo.episodesForSeries(sid) }
                                        episodes = eps.filter { it.season == s }.map { e ->
                                            com.chris.m3usuite.model.Episode(
                                                seriesStreamId = sid,
                                                episodeId = 0,
                                                season = e.season,
                                                episodeNum = e.episodeNum,
                                                title = e.title ?: "Episode ${e.episodeNum}",
                                                plot = e.plot,
                                                durationSecs = e.durationSecs,
                                                containerExt = e.playExt,
                                                poster = null
                                            )
                                        }
                                    }
                                },
                                label = { Text("S$s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = badgeColor,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            // Floating series title badge while scrolling (emulated overlay added below outside the list)
            items(episodes, key = { "${it.seriesStreamId}-${it.season}-${it.episodeNum}" }) { e ->
                val compositeKey = "${e.seriesStreamId}-${e.season}-${e.episodeNum}"
                var resumeSecs by remember(compositeKey) { mutableStateOf<Int?>(null) }

                // Resume für diese Episode laden (auch nach Player-Rückkehr)
                LaunchedEffect(compositeKey, resumeRefreshKey) {
                    resumeSecs = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val sid = seriesStreamId
                        if (sid != null) com.chris.m3usuite.data.repo.ResumeRepository(ctx).getSeriesResume(sid, e.season, e.episodeNum) else null
                    }
                }

                BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    val Accent = if (!isAdult) com.chris.m3usuite.ui.theme.DesignTokens.KidAccent else com.chris.m3usuite.ui.theme.DesignTokens.Accent
                    val seriesClean = remember(title, year) { cleanSeriesName(title, year) }
                    val epName = remember(e.title, seriesClean) { cleanEpisodeTitle(e.title, seriesClean) }
                    val thumbSize = 48.dp
                    val shape = RoundedCornerShape(28.dp)
                    var epFocused by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxWidth()) {
                        // Chip container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (epFocused)
                                        Modifier.border(
                                            width = 2.dp,
                                            brush = Brush.linearGradient(listOf(Accent, Color.Transparent)),
                                            shape = shape
                                        ) else Modifier
                                )
                        ) {
                            Surface(
                                shape = shape,
                                color = badgeColor,
                                contentColor = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                                    .focusScaleOnTv()
                                    .onFocusChanged { epFocused = it.isFocused || it.hasFocus }
                                    .tvClickable {
                                        if (resumeSecs != null) playEpisode(e, fromStart = false, resumeSecs = resumeSecs)
                                        else playEpisode(e, fromStart = true)
                                    }
                            ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Poster thumbnail, rounded + subtle border
                                val model = remember(e.poster) { e.poster }
                                Box(
                                    Modifier
                                        .size(thumbSize)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                ) {
                                    var loaded by remember(model) { mutableStateOf(false) }
                                    val alpha by animateFloatAsState(if (loaded) 1f else 0f, animationSpec = tween(260), label = "thumbFade")
                                    if (!loaded) com.chris.m3usuite.ui.fx.ShimmerBox(modifier = Modifier.fillMaxSize(), cornerRadius = 12.dp)
                                    AsyncImage(
                                        model = buildImageRequest(ctx, model, headers),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                                        onLoading = { loaded = false },
                                        onSuccess = { loaded = true },
                                        onError = { loaded = true }
                                    )
                                    // Play overlay when focused
                                    val vis by remember { derivedStateOf { epFocused } }
                                    val a by animateFloatAsState(if (vis) 1f else 0f, animationSpec = tween(150), label = "epPlayFade")
                                    if (a > 0f) {
                                        Box(Modifier.matchParentSize()) {
                                            com.chris.m3usuite.ui.common.AppIconButton(
                                                icon = com.chris.m3usuite.ui.common.AppIcon.PlayCircle,
                                                contentDescription = "Abspielen",
                                                onClick = { if (resumeSecs != null) playEpisode(e, fromStart = false, resumeSecs = resumeSecs) else playEpisode(e, fromStart = true) },
                                                modifier = Modifier.align(Alignment.Center).graphicsLayer { this.alpha = a },
                                                size = 28.dp
                                            )
                                        }
                                    }
                                }
                                // Centered label area
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "S%02dE%02d".format(e.season, e.episodeNum),
                                        color = Accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        epName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            }
                        }

                        // Progress pill overlay (thin bar near bottom), full width minus 5% margins
                        val duration = e.durationSecs ?: 0
                        val prog = (resumeSecs ?: 0).toFloat() / (if (duration > 0) duration.toFloat() else 1f)
                        val clamped = prog.coerceIn(0f, 1f)
                        val errorColor = MaterialTheme.colorScheme.error
                        Canvas(Modifier.matchParentSize()) {
                            val wPx = size.width
                            val hPx = size.height
                            val margin = wPx * 0.05f
                            val start = Offset(margin, hPx - 6f)
                            val end = Offset(wPx - margin, hPx - 6f)
                            val fillEnd = Offset(start.x + (end.x - start.x) * clamped, start.y)
                            // Track
                            drawLine(
                                color = Color.White.copy(alpha = 0.35f),
                                start = start,
                                end = end,
                                strokeWidth = 3f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            // Progress
                            if (duration > 0 && (resumeSecs ?: 0) > 0) {
                                drawLine(
                                    color = errorColor,
                                    start = start,
                                    end = fillEnd,
                                    strokeWidth = 3.5f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                        }

                        // Progress tooltip on focus
                        if (epFocused) {
                            val secs = (resumeSecs ?: 0).coerceAtLeast(0)
                            val pct = if (duration > 0) ((secs * 100) / duration) else 0
                            Box(Modifier.matchParentSize()) {
                                Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.65f), contentColor = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 10.dp)) {
                                    Text(
                                        if (secs > 0) "Fortgesetzt bei ${fmt(secs)} (${pct}%)" else "0%",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Next hint overlay (after player exit)
                        if (nextHintEpisodeId == e.episodeId && !nextHintText.isNullOrBlank()) {
                            Box(Modifier.matchParentSize()) {
                                Surface(shape = RoundedCornerShape(50), color = Accent.copy(alpha = 0.85f), contentColor = Color.Black, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)) {
                                    Text(nextHintText!!, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
        }
        }

        // Overlay sticky-like floating series badge when scrolled
        val showPinned by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }
        if (showPinned) {
            val cleanTitlePinned = remember(title, year) { cleanSeriesName(title, year) }
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 20.dp)
            ) {
                Surface(shape = RoundedCornerShape(50), color = badgeColor, contentColor = Color.White, modifier = Modifier.graphicsLayer(alpha = DesignTokens.BadgeAlpha)) {
                    Text(
                        if (year != null) "$cleanTitlePinned ($year)" else cleanTitlePinned,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
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

private fun fmt(totalSecs: Int): String {
    val s = max(0, totalSecs)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
