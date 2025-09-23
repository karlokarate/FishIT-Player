package com.chris.m3usuite.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.chris.m3usuite.model.Episode
import com.chris.m3usuite.data.obx.toEpisode
import com.chris.m3usuite.data.obx.buildPlayUrl
import com.chris.m3usuite.player.InternalPlayerScreen
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.core.xtream.ProviderLabelStore
import com.chris.m3usuite.ui.common.AppIconButton
import com.chris.m3usuite.ui.components.sheets.KidSelectSheet
import com.chris.m3usuite.ui.fx.FadeThrough
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.theme.DesignTokens
import com.chris.m3usuite.ui.util.AppAsyncImage
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.AssistChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.max

// ----------------------------
// Vorcompilierte Regex/Helper
// ----------------------------
private val COUNTRY_PARENS = Regex("""\s*\(([A-Z]{2,3})(/[A-Z]{2,3})*\)\s*""")
private val YEAR_PARENS = Regex("""\s*\(\d{4}\)\s*""")
private val SEASON_EP_MARK = Regex("""(?i)S\d{1,2}E\d{1,3}""")
private val EXTRA_SEPARATORS = Regex("""[-:–]+""")

private fun normalizeTrailerUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val lower = trimmed.lowercase()
    return when {
        Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed) -> trimmed
        lower.startsWith("//") -> "https:$trimmed"
        lower.startsWith("www.") -> "https://$trimmed"
        Regex("^[A-Za-z0-9_-]{6,}$").matches(trimmed) -> "https://www.youtube.com/watch?v=$trimmed"
        else -> trimmed
    }
}

private fun parseTags(raw: String?): List<String> = raw
    ?.split(',', ';', '|', '/')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.distinct()
    ?: emptyList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetaChip(text: String, onClick: (() -> Unit)? = null) {
    AssistChip(
        onClick = onClick ?: {},
        enabled = onClick != null,
        label = { Text(text) }
    )
}

private fun cleanSeriesName(raw: String, year: Int?): String {
    var base = raw.substringAfter(" - ", raw)
    if (year != null) base = base.replace(" ($year)", "")
    base = base.replace(COUNTRY_PARENS, " ")
    return base.trim()
}

private fun cleanEpisodeTitle(raw: String?, seriesName: String?): String {
    val lastSeg = raw.orEmpty().split(" - ").lastOrNull()?.trim().orEmpty()
    var t = if (lastSeg.isNotEmpty()) lastSeg else raw.orEmpty()
    if (!seriesName.isNullOrBlank()) t = t.removePrefix(seriesName).trim()
    t = t.replace(YEAR_PARENS, " ")
    t = t.replace(COUNTRY_PARENS, " ")
    t = t.replace(SEASON_EP_MARK, " ")
    t = t.replace(EXTRA_SEPARATORS, " ")
    return t.trim()
}

private fun fmt(totalSecs: Int): String {
    val s = max(0, totalSecs)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@androidx.media3.common.util.UnstableApi
@Composable
fun SeriesDetailScreen(
    id: Long,
    // optionaler Callback für internen Player (url, startMs, seriesId, season, episodeNum, episodeId)
    openInternal: ((url: String, startMs: Long?, seriesId: Int, season: Int, episodeNum: Int, episodeId: Int?, mimeType: String?) -> Unit)? = null,
    onLogo: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val profileId by store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L)

    // Profiltyp (Adult/Kid)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) {
        if (profileId <= 0L) {
            isAdult = true
        } else {
            isAdult = withContext(Dispatchers.IO) {
                com.chris.m3usuite.data.obx.ObxStore
                    .get(ctx)
                    .boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java)
                    .get(profileId)?.type != "kid"
            }
        }
    }

    // Kid‑Gate für diese Serie
    val mediaRepo = remember { com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, store) }
    var contentAllowed by remember { mutableStateOf(true) }
    LaunchedEffect(id, profileId) {
        val adult = if (profileId <= 0L) true else withContext(Dispatchers.IO) {
            com.chris.m3usuite.data.obx.ObxStore
                .get(ctx)
                .boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java)
                .get(profileId)?.type == "adult"
        }
        contentAllowed = if (adult) true else mediaRepo.isAllowed("series", id)
    }

    // Kinder-Freigabe
    val kidRepo = remember { com.chris.m3usuite.data.repo.KidContentRepository(ctx) }
    var showGrantSheet by remember { mutableStateOf(false) }
    var showRevokeSheet by remember { mutableStateOf(false) }

    // --- UI/State ---
    var title by remember { mutableStateOf("") }
    var poster by remember { mutableStateOf<String?>(null) }
    var plot by remember { mutableStateOf<String?>(null) }
    var images by remember { mutableStateOf<List<String>>(emptyList()) }
    var seriesStreamId by remember { mutableStateOf<Int?>(null) }
    var year by remember { mutableStateOf<Int?>(null) }
    var rating by remember { mutableStateOf<Double?>(null) }
   var genre by remember { mutableStateOf<String?>(null) }
    var director by remember { mutableStateOf<String?>(null) }
    var cast by remember { mutableStateOf<String?>(null) }
    var trailer by remember { mutableStateOf<String?>(null) }
    var country by remember { mutableStateOf<String?>(null) }
    var releaseDate by remember { mutableStateOf<String?>(null) }
    var detailReady by remember { mutableStateOf(false) }
    var providerLabel by remember { mutableStateOf<String?>(null) }
    var categoryLabel by remember { mutableStateOf<String?>(null) }
    var imdbId by remember { mutableStateOf<String?>(null) }
    var tmdbId by remember { mutableStateOf<String?>(null) }

    var allEpisodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var seasons by remember { mutableStateOf<List<Int>>(emptyList()) }
    var seasonSel by remember { mutableStateOf<Int?>(null) }

    // --- Interner Player Zustand (Fullscreen) ---
    var showInternal by remember { mutableStateOf(false) }
    var internalUrl by remember { mutableStateOf<String?>(null) }
    var internalStartMs by remember { mutableStateOf<Long?>(null) }
    var internalEpisodeId by remember { mutableStateOf<Int?>(null) }
    var internalUa by remember { mutableStateOf("") }
    var internalRef by remember { mutableStateOf("") }
    var internalMime by remember { mutableStateOf<String?>(null) }
    var nextHintEpisodeId by remember { mutableStateOf<Int?>(null) }
    var nextHintText by remember { mutableStateOf<String?>(null) }
    var resumeRefreshKey by remember { mutableStateOf(0) }

    // Abgeleitete Episodenliste der aktuellen Season (lokales Filtern)
    val episodes by remember(seasonSel, allEpisodes) {
        derivedStateOf {
            val s = seasonSel
            if (s == null) emptyList() else allEpisodes.filter { it.season == s }
        }
    }

    // Daten laden (Serie + Episoden EINMAL)
    LaunchedEffect(id) {
        detailReady = false
        internalMime = null
        try {
        fun decodeObxSeriesId(v: Long): Int? =
            if (v >= 3_000_000_000_000L) (v - 3_000_000_000_000L).toInt() else null

        val obxSid = decodeObxSeriesId(id) ?: run {
            detailReady = true
            return@LaunchedEffect
        }
        seriesStreamId = obxSid

        // Serie (Titel, Poster, Plot, Jahr) + on-demand Enrichment
        withContext(Dispatchers.IO) {
            val box = com.chris.m3usuite.data.obx.ObxStore
                .get(ctx)
                .boxFor(com.chris.m3usuite.data.obx.ObxSeries::class.java)
            var row = box.query(
                com.chris.m3usuite.data.obx.ObxSeries_.seriesId.equal(obxSid.toLong())
            ).build().findFirst()
            val shouldImport = row == null || (
                row.plot.isNullOrBlank() &&
                row.imagesJson.isNullOrBlank()
            )
            if (shouldImport) {
                val repo = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
                repo.importSeriesDetailOnce(obxSid)
                row = box.query(
                    com.chris.m3usuite.data.obx.ObxSeries_.seriesId.equal(obxSid.toLong())
                ).build().findFirst()
                // Vorauslad: Nachbar‑Serien nach ProviderKey (bis 50)
                runCatching {
                    val key = row?.providerKey
                    val ids: List<Int> = if (!key.isNullOrBlank()) {
                        repo.seriesByProviderKeyNewest(key, 0, 60).map { it.seriesId }
                    } else {
                        repo.seriesPagedNewest(0, 60).map { it.seriesId }
                    }
                    repo.importSeriesDetailsForIds(ids, max = 50)
                }
            }

            title = row?.name.orEmpty()
            images = row?.imagesJson?.takeIf { it.isNotBlank() }?.let {
                runCatching {
                    val arr = JSONArray(it)
                    (0 until arr.length()).mapNotNull { idx -> arr.optString(idx, null) }
                }.getOrNull()
            } ?: emptyList()
            poster = images.firstOrNull()
            plot = row?.plot?.takeIf { it.isNotBlank() }
            year = row?.year
            rating = row?.rating
            genre = row?.genre
            director = row?.director
            cast = row?.cast
            country = row?.country
            releaseDate = row?.releaseDate
            trailer = normalizeTrailerUrl(row?.trailer)
            providerLabel = row?.providerKey?.takeIf { it.isNotBlank() }?.let { key ->
                ProviderLabelStore.get(ctx).labelFor(key) ?: key
            }
            categoryLabel = row?.categoryId?.takeIf { it.isNotBlank() }?.let { catId ->
                val catBox = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
                    .boxFor(com.chris.m3usuite.data.obx.ObxCategory::class.java)
                catBox.query(
                    com.chris.m3usuite.data.obx.ObxCategory_.kind.equal("series").and(
                        com.chris.m3usuite.data.obx.ObxCategory_.categoryId.equal(catId)
                    )
                ).build().findFirst()?.categoryName ?: catId
            }
            imdbId = row?.imdbId?.takeIf { it.isNotBlank() }
            tmdbId = row?.tmdbId?.takeIf { it.isNotBlank() }
        }

        // Episoden holen; bei Leerstand einmal importieren und erneut lesen
        suspend fun fetchAll(): List<Episode> {
            val repo = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
            var raw = withContext(Dispatchers.IO) { repo.episodesForSeries(obxSid) }
            if (raw.isEmpty()) {
                withContext(Dispatchers.IO) { repo.importSeriesDetailOnce(obxSid) }
                raw = withContext(Dispatchers.IO) { repo.episodesForSeries(obxSid) }
            }
            return raw.map { it.toEpisode() }
                .map { ep -> ep.copy(seriesStreamId = obxSid, title = ep.title ?: "Episode ${ep.episodeNum}") }
                .sortedWith(compareBy({ it.season }, { it.episodeNum }))
        }

        val mapped = fetchAll()
        allEpisodes = mapped
        seasons = mapped.asSequence().map { it.season }.distinct().sorted().toList()
        seasonSel = seasons.firstOrNull()
        } finally {
            detailReady = true
        }
    }

    if (showInternal) {
        val hdrs = mutableMapOf<String, String>().apply {
            if (internalUa.isNotBlank()) this["User-Agent"] = internalUa
            if (internalRef.isNotBlank()) this["Referer"] = internalRef
        }
        InternalPlayerScreen(
            url = internalUrl.orEmpty(),
            type = "series",
            episodeId = internalEpisodeId,
            startPositionMs = internalStartMs,
            headers = hdrs,
            mimeType = internalMime,
            onExit = { showInternal = false }
        )
        return
    }

    if (!detailReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Nach Player-Exit: kurzer „Next“-Hinweis (derzeit ohne EpisodeId‑Pfad)
    LaunchedEffect(showInternal) {
        if (!showInternal && internalEpisodeId != null) {
            nextHintEpisodeId = null
            nextHintText = null
            resumeRefreshKey++
        }
    }

    fun playEpisode(e: Episode, fromStart: Boolean = false, resumeSecs: Int? = null) {
        scope.launch {
            if (!contentAllowed) {
                android.widget.Toast
                    .makeText(ctx, "Nicht freigegeben", android.widget.Toast.LENGTH_SHORT)
                    .show()
                return@launch
            }
            val startMs: Long? = if (!fromStart) resumeSecs?.toLong()?.times(1000) else null

            // Telegram bevorzugen, wenn Referenzen existieren
            val tgUrl = if (e.tgChatId != null && e.tgMessageId != null)
                "tg://message?chatId=${e.tgChatId}&messageId=${e.tgMessageId}" else null

            val headers = com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeadersBlocking(store)
            // Bevorzugt: direkte Episode-URL über XtreamUrlFactory/OBX (kein Client-Init, keine Redirect-Probe)
            val urlToPlay = tgUrl ?: com.chris.m3usuite.data.obx.buildEpisodePlayUrl(
                ctx = ctx,
                seriesStreamId = seriesStreamId ?: 0,
                season = e.season,
                episodeNum = e.episodeNum,
                episodeExt = e.containerExt,
                episodeId = e.episodeId.takeIf { it > 0 }
            ) ?: return@launch

            val playableUrl = urlToPlay
            val resolvedMime = com.chris.m3usuite.core.playback.PlayUrlHelper.guessMimeType(playableUrl, e.containerExt)

            PlayerChooser.start(
                context = ctx,
                store = store,
                url = playableUrl,
                headers = headers,
                startPositionMs = startMs,
                mimeType = resolvedMime
            ) { s, mime ->
                if (openInternal != null) {
                    openInternal(playableUrl, s, seriesStreamId ?: 0, e.season, e.episodeNum, e.episodeId.takeIf { it > 0 }, mime)
                } else {
                    internalUrl = playableUrl
                    internalEpisodeId = e.episodeId.takeIf { it > 0 }
                    internalStartMs = s
                    internalUa = headers["User-Agent"].orEmpty()
                    internalRef = headers["Referer"].orEmpty()
                    internalMime = mime
                    showInternal = true
                }
            }
        }
    }

    val listState = com.chris.m3usuite.ui.state.rememberRouteListState("seriesDetail:$id")

    HomeChromeScaffold(
        title = "Serie",
        onSettings = null,
        onSearch = onLogo, // will be overridden by MainActivity navigation
        onProfiles = null,
        listState = listState,
        onLogo = onLogo,
        bottomBar = {}
    ) { pads ->
        Box(Modifier.fillMaxSize().padding(pads)) {
            val accent = if (!isAdult) DesignTokens.KidAccent else DesignTokens.Accent
            val badgeColor = if (!isAdult) accent.copy(alpha = 0.26f) else accent.copy(alpha = 0.20f)
            val badgeColorDarker = if (!isAdult) accent.copy(alpha = 0.32f) else accent.copy(alpha = 0.26f)

            // Hintergrund
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.background,
                            1f to MaterialTheme.colorScheme.surface
                        )
                    )
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (!isAdult) 0.20f else 0.12f),
                                Color.Transparent
                            ),
                            radius = with(LocalDensity.current) { 680.dp.toPx() }
                        )
                    )
            )
            com.chris.m3usuite.ui.fx.FishBackground(
                modifier = Modifier.align(Alignment.Center).size(560.dp),
                alpha = 0.05f,
                neutralizeUnderlay = true
            )
            com.chris.m3usuite.ui.common.AccentCard(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                accent = accent
            ) {
                val ftKey = remember(seasonSel, episodes.size) { (seasonSel ?: -1) to episodes.size }
                FadeThrough(key = ftKey) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // Header (Titel, Poster, Kid‑Freigabe)
                        item {
                            Column(Modifier.fillMaxWidth()) {
                                val cleanTitle = remember(title, year) { cleanSeriesName(title, year) }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = badgeColor,
                                    contentColor = Color.White,
                                    modifier = Modifier.graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                                ) {
                                    Text(
                                        if (year != null) "$cleanTitle ($year)" else cleanTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = accent.copy(alpha = 0.35f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Box(modifier = Modifier.height(220.dp).fillMaxWidth()) {
                                    com.chris.m3usuite.ui.util.AppHeroImage(
                                        url = poster,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize(),
                                        crossfade = true
                                    )
                                    if (!trailer.isNullOrBlank()) {
                                        androidx.compose.material3.Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.Black.copy(alpha = 0.6f),
                                            contentColor = Color.White,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                        ) { Text("Trailer", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                                // Trailer embedded + expandable
                                if (!trailer.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    val expand = remember { mutableStateOf(false) }
                                    androidx.compose.material3.Text("Trailer", style = MaterialTheme.typography.titleSmall)
                                    com.chris.m3usuite.ui.common.TrailerBox(
                                        url = trailer!!,
                                        headers = com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeadersBlocking(store),
                                        expanded = expand
                                    )
                                    if (expand.value) {
                                        androidx.compose.ui.window.Dialog(onDismissRequest = { expand.value = false }) {
                                            Surface(color = Color.Black) {
                                                Box(Modifier.fillMaxSize()) {
                                                    com.chris.m3usuite.ui.common.TrailerBox(
                                                        url = trailer!!,
                                                        headers = com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeadersBlocking(store),
                                                        expanded = expand,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                // Optional: Bilder-Galerie
                                if (images.size > 1) {
                                    Spacer(Modifier.height(8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                        items(images.size, key = { i -> images[i] }) { i ->
                                            val img = images[i]
                                            AppAsyncImage(
                                                url = img,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .height(110.dp)
                                                    .aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                        }
                                    }
                                }
                                // Optional: Metadata-Zeilen
                                Spacer(Modifier.height(8.dp))
                                val genreTags = remember(genre) { parseTags(genre) }
                                val countryTags = remember(country) { parseTags(country) }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    year?.let { MetaChip("$it") }
                                    rating?.let { MetaChip("★ ${"%.1f".format(it)}") }
                                    providerLabel?.let { MetaChip(it) }
                                    categoryLabel?.let { MetaChip(it) }
                                    genreTags.forEach { tag -> MetaChip(tag) }
                                    if (genreTags.isEmpty()) {
                                        genre?.takeIf { !it.isNullOrBlank() }?.let { MetaChip(it!!) }
                                    }
                                    countryTags.forEach { tag -> MetaChip(tag) }
                                    if (countryTags.isEmpty()) {
                                        country?.takeIf { !it.isNullOrBlank() }?.let { MetaChip(it!!) }
                                    }
                                }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    imdbId?.let { id ->
                                        val imdbUrl = if (id.startsWith("tt", ignoreCase = true))
                                            "https://www.imdb.com/title/$id" else "https://www.imdb.com/find?q=$id"
                                        MetaChip("IMDB: $id") { uriHandler.openUri(imdbUrl) }
                                    }
                                    tmdbId?.let { id ->
                                        val tmdbUrl = "https://www.themoviedb.org/tv/$id"
                                        MetaChip("TMDB: $id") { uriHandler.openUri(tmdbUrl) }
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    director?.takeIf { it.isNullOrBlank().not() }?.let { Text("Regie: $it", style = MaterialTheme.typography.bodySmall) }
                                    cast?.takeIf { it.isNullOrBlank().not() }?.let { Text("Cast: $it", style = MaterialTheme.typography.bodySmall) }
                                    releaseDate?.takeIf { it.isNullOrBlank().not() }?.let { Text("Release: $it", style = MaterialTheme.typography.bodySmall) }
                                }
                                if (isAdult) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        AppIconButton(
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

                        // Plot (expand/collapse)
                        if (!plot.isNullOrBlank()) {
                            item {
                                var plotExpanded by remember { mutableStateOf(false) }
                                val plotAnim by animateFloatAsState(
                                    if (plotExpanded) 0f else 1f,
                                    animationSpec = tween(180),
                                    label = "plotGrad"
                                )
                                Column(Modifier.animateContentSize()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = badgeColorDarker,
                                        contentColor = Color.White,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                                    ) {
                                        Text(
                                            plot!!,
                                            maxLines = if (plotExpanded) Int.MAX_VALUE else 8,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                    androidx.compose.material3.TextButton(onClick = { plotExpanded = !plotExpanded }) {
                                        Text(if (plotExpanded) "Weniger anzeigen" else "Mehr anzeigen")
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        // Staffeln – reines Umschalten (lokales Filtern)
                        if (seasons.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = badgeColor,
                                    contentColor = Color.White,
                                    modifier = Modifier.graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                                ) {
                                    Text(
                                        "Staffeln",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(end = 8.dp)
                                ) {
                                    items(seasons, key = { it }) { s ->
                                        FilterChip(
                                            modifier = Modifier.graphicsLayer(alpha = DesignTokens.BadgeAlpha),
                                            selected = seasonSel == s,
                                            onClick = { seasonSel = s },
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

                        // Episodenliste der aktuellen Season
                        items(
                            items = episodes,
                            key = { "${it.seriesStreamId}-${it.season}-${it.episodeNum}" }
                        ) { e ->
                            val compositeKey = "${e.seriesStreamId}-${e.season}-${e.episodeNum}"
                            var resumeSecs by remember(compositeKey, resumeRefreshKey) { mutableStateOf<Int?>(null) }

                            // Resume (OBX)
                            LaunchedEffect(compositeKey, resumeRefreshKey) {
                                resumeSecs = withContext(Dispatchers.IO) {
                                    val sid = seriesStreamId
                                    if (sid != null) com.chris.m3usuite.data.repo.ResumeRepository(ctx)
                                        .getSeriesResume(sid, e.season, e.episodeNum)
                                    else null
                                }
                            }

                            BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                val seriesClean = remember(title, year) { cleanSeriesName(title, year) }
                                val epName = remember(e.title, seriesClean) { cleanEpisodeTitle(e.title, seriesClean) }
                                val thumbSize = 48.dp
                                val shape = RoundedCornerShape(28.dp)
                                var epFocused by remember { mutableStateOf(false) }

                                Box(Modifier.fillMaxWidth()) {
                                    // Chip Container
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (epFocused)
                                                    Modifier.border(
                                                        width = 2.dp,
                                                        brush = Brush.linearGradient(listOf(accent, Color.Transparent)),
                                                        shape = shape
                                                    )
                                                else Modifier
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
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    // Poster Thumbnail
                                                    Box(
                                                        Modifier
                                                            .size(thumbSize)
                                                            .clip(RoundedCornerShape(12.dp))
                                                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                                ) {
                                                    var loaded by remember(e.poster) { mutableStateOf(false) }
                                                    val alpha by animateFloatAsState(
                                                        if (loaded) 1f else 0f,
                                                        animationSpec = tween(260),
                                                        label = "thumbFade"
                                                    )
                                                    if (!loaded)
                                                        com.chris.m3usuite.ui.fx.ShimmerBox(
                                                            modifier = Modifier.fillMaxSize(),
                                                            cornerRadius = 12.dp
                                                        )
                                                    AppAsyncImage(
                                                        url = e.poster,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                                                        crossfade = false,
                                                        onLoading = { loaded = false },
                                                        onSuccess = { loaded = true },
                                                        onError = { loaded = true }
                                                    )
                                                    // Duration overlay (bottom-right) if available
                                                    if ((e.durationSecs ?: 0) > 0) {
                                                        val secs = e.durationSecs ?: 0
                                                        val h = secs / 3600
                                                        val m = (secs % 3600) / 60
                                                        val text = if (h > 0) String.format("%dh %02dm", h, m) else String.format("%dm", m)
                                                        androidx.compose.material3.Surface(
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = Color.Black.copy(alpha = 0.55f),
                                                            contentColor = Color.White,
                                                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                                        ) { Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                                    }
                                                    // Play-Overlay bei Fokus
                                                    val vis by remember { derivedStateOf { epFocused } }
                                                    val a by animateFloatAsState(
                                                        if (vis) 1f else 0f,
                                                        animationSpec = tween(150),
                                                        label = "epPlayFade"
                                                    )
                                                    if (a > 0f) {
                                                        Box(Modifier.matchParentSize()) {
                                                            AppIconButton(
                                                                icon = com.chris.m3usuite.ui.common.AppIcon.PlayCircle,
                                                                contentDescription = "Abspielen",
                                                                onClick = {
                                                                    if (resumeSecs != null) playEpisode(e, fromStart = false, resumeSecs = resumeSecs)
                                                                    else playEpisode(e, fromStart = true)
                                                                },
                                                                modifier = Modifier.align(Alignment.Center).graphicsLayer { this.alpha = a },
                                                                size = 28.dp
                                                            )
                                                        }
                                                    }
                                                }

                                                // Zentrum: SxxEyy + Titel
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        "S%02dE%02d".format(e.season, e.episodeNum),
                                                        color = accent,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(Modifier.size(8.dp))
                                                    Text(
                                                        epName,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                // Aktion: Direktlink teilen (Xtream)
                                                androidx.compose.material3.TextButton(
                                                    onClick = {
                                                        val link = e.buildPlayUrl(ctx)
                                                        if (link.isNullOrBlank()) {
                                                            android.widget.Toast.makeText(ctx, "Kein Link verfügbar", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            val subj = run {
                                                                val base = if (title.isBlank()) "Serie" else title
                                                                val sStr = java.lang.String.format(java.util.Locale.US, "%02d", e.season)
                                                                val eStr = java.lang.String.format(java.util.Locale.US, "%02d", e.episodeNum)
                                                                "$base – S${sStr}E${eStr}"
                                                            }
                                                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                                type = "text/plain"
                                                                putExtra(android.content.Intent.EXTRA_SUBJECT, subj)
                                                                putExtra(android.content.Intent.EXTRA_TEXT, link)
                                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            ctx.startActivity(android.content.Intent.createChooser(send, "Direktlink teilen"))
                                                        }
                                                    },
                                                    enabled = true,
                                                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = accent)
                                                ) { androidx.compose.material3.Text("Link teilen") }
                                            }

                                            val episodeMeta = buildList {
                                                e.durationSecs?.let { secs ->
                                                    val hrs = secs / 3600
                                                    val mins = (secs % 3600) / 60
                                                    val label = when {
                                                        hrs > 0 -> "${hrs}h ${mins}m"
                                                        mins > 0 -> "${mins}m"
                                                        else -> "${secs}s"
                                                    }
                                                    add(label)
                                                }
                                                e.rating?.let { add("★ ${"%.1f".format(it)}") }
                                                e.airDate?.takeIf { !it.isNullOrBlank() }?.let { add(it) }
                                            }
                                            if (episodeMeta.isNotEmpty()) {
                                                Text(
                                                    episodeMeta.joinToString(" • "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                                )
                                            }
                                        }
                                    }

                                    // Progress-Balken
                                    val duration = e.durationSecs ?: 0
                                    val prog = (resumeSecs ?: 0).toFloat() / (if (duration > 0) duration.toFloat() else 1f)
                                    val clamped = prog.coerceIn(0f, 1f)
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
                                                color = accent,
                                                start = start,
                                                end = fillEnd,
                                                strokeWidth = 3.5f,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                                            )
                                        }
                                    }

                                    // Progress-Tooltip bei Fokus
                                    if (epFocused) {
                                        val secs = (resumeSecs ?: 0).coerceAtLeast(0)
                                        val pct = if (duration > 0) ((secs * 100) / duration) else 0
                                        Box(Modifier.matchParentSize()) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = Color.Black.copy(alpha = 0.65f),
                                                contentColor = Color.White,
                                                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 10.dp)
                                            ) {
                                                Text(
                                                    if (secs > 0) "Fortgesetzt bei ${fmt(secs)} (${pct}%)" else "0%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Next-Hinweis (aktuell inaktiv)
                                    if (nextHintEpisodeId == e.episodeId && !nextHintText.isNullOrBlank()) {
                                        Box(Modifier.matchParentSize()) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = accent.copy(alpha = 0.85f),
                                                contentColor = Color.Black,
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 6.dp)
                                            ) {
                                                Text(
                                                    nextHintText!!,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                                )
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

            // Overlay: „Sticky“-Serienbadge beim Scrollen
            val showPinned by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 }
            }
            if (showPinned) {
                val cleanTitlePinned = remember(title, year) { cleanSeriesName(title, year) }
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 24.dp, top = 20.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = badgeColor,
                            contentColor = Color.White,
                            modifier = Modifier.graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                        ) {
                            Text(
                                if (year != null) "$cleanTitlePinned ($year)" else cleanTitlePinned,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Kid-Freigabe-Dialoge
            if (showGrantSheet) KidSelectSheet(
                onConfirm = { kidIds ->
                    scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.allowBulk(it, "series", listOf(id)) } }
                    showGrantSheet = false
                },
                onDismiss = { showGrantSheet = false }
            )
            if (showRevokeSheet) KidSelectSheet(
                onConfirm = { kidIds ->
                    scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.disallowBulk(it, "series", listOf(id)) } }
                    showRevokeSheet = false
                },
                onDismiss = { showRevokeSheet = false }
            )
        }
    }
}}
