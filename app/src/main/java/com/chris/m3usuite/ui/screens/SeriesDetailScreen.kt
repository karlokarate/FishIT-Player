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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.onFocusEvent
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
import com.chris.m3usuite.ui.actions.MediaAction
import com.chris.m3usuite.ui.actions.MediaActionBar
import com.chris.m3usuite.ui.actions.MediaActionId
import com.chris.m3usuite.core.telemetry.Telemetry
import androidx.compose.ui.res.stringResource
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
    onLogo: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    LaunchedEffect(id) {
        com.chris.m3usuite.metrics.RouteTag.set("series:$id")
        com.chris.m3usuite.core.debug.GlobalDebug.logTree("series:detail", "tile:$id")
    }
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val resumeRepo = remember { com.chris.m3usuite.data.repo.ResumeRepository(ctx) }
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
    var backdrop by remember { mutableStateOf<String?>(null) }
    var cover by remember { mutableStateOf<String?>(null) }
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
    var uiState by remember { mutableStateOf<com.chris.m3usuite.ui.state.UiState<Unit>>(com.chris.m3usuite.ui.state.UiState.Loading) }
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
        uiState = com.chris.m3usuite.ui.state.UiState.Loading
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
                // Kein globaler Prefetch: Metadaten werden ausschließlich on-demand für die aktuelle Kachel geladen
            }

            title = row?.name.orEmpty()
            images = row?.imagesJson?.takeIf { it.isNotBlank() }?.let {
                runCatching {
                    val arr = JSONArray(it)
                    (0 until arr.length()).mapNotNull { idx -> arr.optString(idx, null) }
                }.getOrNull()
            } ?: emptyList()
            run {
                val posterCand = images.getOrNull(0)
                val coverCand = images.getOrNull(1)?.takeUnless { it == posterCand }
                val backdropCand = images.drop(if (coverCand != null) 2 else 1).firstOrNull { it != posterCand && it != coverCand }
                poster = posterCand
                cover = coverCand
                backdrop = backdropCand
            }
            plot = row?.plot?.takeIf { it.isNotBlank() }
            year = row?.year
            rating = row?.rating
            genre = row?.genre
            director = row?.director
            cast = row?.cast
            country = row?.country
            releaseDate = row?.releaseDate
            trailer = normalizeTrailerUrl(row?.trailer)
            imdbId = row?.imdbId
            tmdbId = row?.tmdbId
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
            // If we end up here without exceptions, consider it success; empty episodes still render below
            uiState = com.chris.m3usuite.ui.state.UiState.Success(Unit)
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

    // Back button: allow exit from details
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(backDispatcher) {
        if (backDispatcher == null) return@DisposableEffect onDispose { }
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                remove()
                backDispatcher.onBackPressed()
            }
        }
        backDispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }

    if (com.chris.m3usuite.BuildConfig.UI_STATE_V1) {
        when (val s = uiState) {
            is com.chris.m3usuite.ui.state.UiState.Loading -> { com.chris.m3usuite.ui.state.LoadingState(); return }
            is com.chris.m3usuite.ui.state.UiState.Empty -> { com.chris.m3usuite.ui.state.EmptyState(); return }
            is com.chris.m3usuite.ui.state.UiState.Error -> { com.chris.m3usuite.ui.state.ErrorState(s.message, s.retry); return }
            is com.chris.m3usuite.ui.state.UiState.Success -> { /* render content */ }
        }
    } else if (!detailReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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

    val seriesLauncher = if (com.chris.m3usuite.BuildConfig.PLAYBACK_LAUNCHER_V1)
        com.chris.m3usuite.playback.rememberPlaybackLauncher(
            onOpenInternal = { pr ->
                if (openInternal != null) {
                    openInternal(pr.url, pr.startPositionMs, seriesStreamId ?: 0, pr.season ?: 0, pr.episodeNum ?: 0, pr.episodeId, pr.mimeType)
                } else {
                    internalUrl = pr.url
                    internalEpisodeId = pr.episodeId
                    internalStartMs = pr.startPositionMs
                    internalUa = pr.headers["User-Agent"].orEmpty()
                    internalRef = pr.headers["Referer"].orEmpty()
                    internalMime = pr.mimeType
                    showInternal = true
                }
            },
            onResult = { req, res ->
                scope.launch(Dispatchers.IO) {
                    val sid = req.seriesId
                    val s = req.season
                    val eNum = req.episodeNum
                    when (res) {
                        is com.chris.m3usuite.playback.PlayerResult.Completed -> if (sid != null && s != null && eNum != null) {
                            com.chris.m3usuite.core.telemetry.Telemetry.event(
                                "resume.clear",
                                mapOf("type" to "series", "seriesId" to sid, "season" to s, "episode" to eNum)
                            )
                            runCatching { resumeRepo.clearSeriesResume(sid, s, eNum) }
                        }
                        is com.chris.m3usuite.playback.PlayerResult.Stopped -> if (sid != null && s != null && eNum != null) {
                            val pos = ((res.positionMs / 1000).toInt()).coerceAtLeast(0)
                            com.chris.m3usuite.core.telemetry.Telemetry.event(
                                "resume.set",
                                mapOf("type" to "series", "seriesId" to sid, "season" to s, "episode" to eNum, "positionSecs" to pos)
                            )
                            runCatching { resumeRepo.setSeriesResume(sid, s, eNum, pos) }
                        }
                        else -> Unit
                    }
                }
            }
        ) else null

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

            if (com.chris.m3usuite.BuildConfig.PLAYBACK_LAUNCHER_V1 && seriesLauncher != null) {
                seriesLauncher.launch(
                    com.chris.m3usuite.playback.PlayRequest(
                        type = "series",
                        mediaId = id,
                        url = playableUrl,
                        headers = headers,
                        startPositionMs = startMs,
                        mimeType = resolvedMime,
                        title = title,
                        seriesId = seriesStreamId,
                        season = e.season,
                        episodeNum = e.episodeNum,
                        episodeId = e.episodeId.takeIf { it > 0 }
                    )
                )
            } else {
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
    }

    val listState = com.chris.m3usuite.ui.state.rememberRouteListState("seriesDetail:$id")

    HomeChromeScaffold(
        title = "Serie",
        onSettings = onOpenSettings,
        onSearch = onLogo, // will be overridden by MainActivity navigation
        onProfiles = null,
        listState = listState,
        onLogo = onLogo,
        bottomBar = {},
        enableDpadLeftChrome = false
    ) { pads ->
        // New unified detail mask (behind flag). Fallback below keeps legacy path.
        if (com.chris.m3usuite.BuildConfig.DETAIL_SCAFFOLD_V1) {
            com.chris.m3usuite.ui.detail.SeriesDetailMask(
                isAdult = isAdult,
                pads = pads,
                listState = listState,
                store = store,
                backdrop = backdrop,
                poster = poster,
                cover = cover,
                title = title,
                plot = plot,
                year = year,
                rating = rating,
                genre = genre,
                providerLabel = providerLabel,
                categoryLabel = categoryLabel,
                country = country,
                releaseDate = releaseDate,
                imdbId = imdbId,
                tmdbId = tmdbId,
                trailer = trailer,
                seriesStreamId = seriesStreamId,
                seasons = seasons,
                seasonSel = seasonSel,
                onSelectSeason = { s -> seasonSel = s },
                episodes = episodes,
                resumeLookup = { e -> com.chris.m3usuite.data.repo.ResumeRepository(ctx).getSeriesResume(seriesStreamId ?: -1, e.season, e.episodeNum) },
                onPlayEpisode = { e, fromStart, resumeSecs -> playEpisode(e, fromStart, resumeSecs) },
                onOpenLink = { link -> runCatching { uriHandler.openUri(link) } }
            )
            return@HomeChromeScaffold
        }
        if (false && com.chris.m3usuite.BuildConfig.DETAIL_SCAFFOLD_V1) {
            val heroUrl = remember(backdrop, poster) { backdrop ?: poster }
            val firstEp = episodes.firstOrNull()
            val lblPlay = stringResource(com.chris.m3usuite.R.string.action_play)
            val lblTrailer = stringResource(com.chris.m3usuite.R.string.action_trailer)
            val actions = remember(firstEp, trailer, lblPlay, lblTrailer, uriHandler) {
                buildList<com.chris.m3usuite.ui.actions.MediaAction> {
                    if (firstEp != null) add(
                        com.chris.m3usuite.ui.actions.MediaAction(
                            id = com.chris.m3usuite.ui.actions.MediaActionId.Play,
                            label = lblPlay,
                            primary = true,
                            onClick = { playEpisode(firstEp, fromStart = true) }
                        )
                    )
                    val tr = normalizeTrailerUrl(trailer)
                    if (!tr.isNullOrBlank()) add(
                        com.chris.m3usuite.ui.actions.MediaAction(
                            id = com.chris.m3usuite.ui.actions.MediaActionId.Trailer,
                            label = lblTrailer,
                            onClick = { runCatching { uriHandler.openUri(tr) } }
                        )
                    )
                }
            }
            val meta = com.chris.m3usuite.ui.detail.DetailMeta(
                year = year,
                genres = parseTags(genre),
                provider = providerLabel,
                category = categoryLabel
            )
            com.chris.m3usuite.ui.detail.DetailPage(
                isAdult = isAdult,
                pads = pads,
                listState = listState,
                title = title,
                heroUrl = heroUrl,
                posterUrl = cover ?: poster ?: heroUrl,
                actions = actions,
                meta = meta,
                headerExtras = { com.chris.m3usuite.ui.detail.DetailHeaderExtras() },
                showHeaderMetaChips = true,
                resumeText = null,
                plot = plot,
                year = year,
                durationSecs = null,
                containerExt = null,
                rating = rating,
                mpaaRating = null,
                age = null,
                provider = providerLabel,
                category = categoryLabel,
                genres = parseTags(genre),
                countries = parseTags(country).ifEmpty { country?.let { listOf(it) } ?: emptyList() },
                director = director,
                cast = cast,
                releaseDate = releaseDate,
                imdbId = imdbId,
                tmdbId = tmdbId,
                tmdbUrl = null,
                audio = null,
                video = null,
                bitrate = null,
                onOpenLink = { link -> runCatching { uriHandler.openUri(link) } },
                trailerUrl = null,
                trailerHeaders = null,
                extraItems = {
                    // Staffeln – reines Umschalten (lokales Filtern)
                    if (seasons.isNotEmpty()) {
                        item {
                            val accent = if (!isAdult) DesignTokens.KidAccent else DesignTokens.Accent
                            val badgeColor = if (!isAdult) accent.copy(alpha = 0.26f) else accent.copy(alpha = 0.20f)
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
                            com.chris.m3usuite.ui.tv.TvFocusRow(
                                stateKey = "series:seasons:${seriesStreamId ?: -1}",
                                itemSpacing = 8.dp,
                                contentPadding = PaddingValues(end = 8.dp),
                                itemCount = seasons.size,
                                itemKey = { idx -> seasons[idx] }
                            ) { idx ->
                                val s = seasons[idx]
                                FilterChip(
                                    modifier = Modifier.graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                                        .then(com.chris.m3usuite.ui.skin.run { Modifier.tvClickable { seasonSel = s } }),
                                    selected = seasonSel == s,
                                    onClick = { seasonSel = s },
                                    label = { Text("S$s") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = badgeColor,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    // Episodenliste der aktuellen Season
                    itemsIndexed(
                        items = episodes,
                        key = { index, item -> "${item.seriesStreamId}-${item.season}-${item.episodeNum}" }
                    ) { index, e ->
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
                                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                                            else Modifier
                                        )
                                        .clip(shape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                                        .then(com.chris.m3usuite.ui.skin.run {
                                            Modifier.tvClickable {
                                                if (resumeSecs != null) playEpisode(e, fromStart = false, resumeSecs = resumeSecs)
                                                else playEpisode(e, fromStart = true)
                                            }
                                        })
                                        .onFocusEvent { ev -> epFocused = ev.isFocused }
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        // Thumb
                                        Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.06f)) {
                                            Box(Modifier.size(thumbSize)) {
                                                var loaded by remember { mutableStateOf(false) }
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
                                                // Duration overlay
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
                                            }
                                        }
                                        // Title + info
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            val titleLine = buildString {
                                                append("S"); append(e.season); append("E"); append(e.episodeNum)
                                                if (!epName.isNullOrBlank()) { append(" · "); append(epName) }
                                            }
                                            Text(titleLine, style = MaterialTheme.typography.titleSmall)
                                            val sub = e.plot?.takeIf { it.isNotBlank() } ?: e.title ?: ""
                                            if (sub.isNotBlank()) Text(sub, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                        }
                                        // Actions entfernt: Gesamtes Feld ist fokussierbar und klickbar (spielt ab)
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            return@HomeChromeScaffold
        }

        // Header auto-expand on TV removed (details must remain visible)
        // Root without padding so hero covers entire screen
        Box(Modifier.fillMaxSize()) {
            val accent = if (!isAdult) DesignTokens.KidAccent else DesignTokens.Accent
            val heroUrl = remember(backdrop, poster) { backdrop ?: poster }
            // Layer 1: full-screen hero image (crop to fill; center-aligned)
            heroUrl?.let { url ->
                com.chris.m3usuite.ui.util.AppHeroImage(
                    url = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer(alpha = com.chris.m3usuite.ui.detail.HERO_SCRIM_IMAGE_ALPHA),
                    crossfade = true
                )
            }
            val badgeColor = if (!isAdult) accent.copy(alpha = 0.26f) else accent.copy(alpha = 0.20f)
            val badgeColorDarker = if (!isAdult) accent.copy(alpha = 0.32f) else accent.copy(alpha = 0.26f)

            // Foreground content area with scaffold paddings
            Box(Modifier.fillMaxSize().padding(pads)) {
                // Hintergrundlesbarkeit innerhalb des gepaddeten Inhalts
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                                1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
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
                // Removed FishBackground per request
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
                        if (true) {
                            item {
                                val firstEp = episodes.firstOrNull()
                                val actions = buildList<com.chris.m3usuite.ui.actions.MediaAction> {
                                    if (firstEp != null) add(
                                        com.chris.m3usuite.ui.actions.MediaAction(
                                            id = com.chris.m3usuite.ui.actions.MediaActionId.Play,
                                            label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_play),
                                            primary = true,
                                            onClick = { playEpisode(firstEp, fromStart = true) }
                                        )
                                    )
                                    val tr = normalizeTrailerUrl(trailer)
                                    if (!tr.isNullOrBlank()) add(
                                        com.chris.m3usuite.ui.actions.MediaAction(
                                            id = com.chris.m3usuite.ui.actions.MediaActionId.Trailer,
                                            label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_trailer),
                                            onClick = { runCatching { uriHandler.openUri(tr) } }
                                        )
                                    )
                                }
                                val meta = com.chris.m3usuite.ui.detail.DetailMeta(
                                    year = year,
                                    genres = parseTags(genre),
                                    provider = providerLabel,
                                    category = categoryLabel
                                )
                                com.chris.m3usuite.ui.detail.DetailHeader(
                                    title = title,
                                    subtitle = null,
                                    heroUrl = backdrop ?: poster,
                                    posterUrl = cover ?: poster ?: backdrop,
                                    actions = actions,
                                    meta = meta,
                                    showHeroScrim = false,
                                    headerExtras = { com.chris.m3usuite.ui.detail.DetailHeaderExtras() }
                                )
                            }
                        }
                        if (false && !com.chris.m3usuite.BuildConfig.DETAIL_SCAFFOLD_V1) item {
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
                                    com.chris.m3usuite.ui.util.AppPosterImage(
                                        url = poster ?: heroUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
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
                                // Unified action bar (Play / Trailer)
                                if (com.chris.m3usuite.BuildConfig.MEDIA_ACTIONBAR_V1) {
                                    val actions = buildList<MediaAction> {
                                        val firstEp = episodes.firstOrNull()
                                        if (firstEp != null) add(
                                            MediaAction(
                                                id = MediaActionId.Play,
                                                label = stringResource(com.chris.m3usuite.R.string.action_play),
                                                primary = true,
                                                onClick = {
                                                    Telemetry.event("ui_action_play", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current))
                                                    playEpisode(firstEp, fromStart = true)
                                                }
                                            )
                                        )
                                        val tr = normalizeTrailerUrl(trailer)
                                        if (!tr.isNullOrBlank()) add(
                                            MediaAction(
                                                id = MediaActionId.Trailer,
                                                label = stringResource(com.chris.m3usuite.R.string.action_trailer),
                                                onClick = {
                                                    Telemetry.event("ui_action_trailer", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current))
                                                    runCatching { uriHandler.openUri(tr) }
                                                }
                                            )
                                        )
                                    }
                                    MediaActionBar(actions = actions, modifier = Modifier.padding(vertical = 6.dp))
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

                        // Plot (always expanded)
                        if (!plot.isNullOrBlank()) {
                            item {
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
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        // Global Facts (zentral)
                        item {
                            com.chris.m3usuite.ui.detail.DetailFacts(
                                modifier = Modifier.fillMaxWidth(),
                                year = year,
                                durationSecs = null, // Serie: Episode-Laufzeit separat
                                containerExt = null,
                                rating = rating,
                                mpaaRating = null,
                                age = null,
                                provider = providerLabel,
                                category = categoryLabel,
                                genres = parseTags(genre),
                                countries = parseTags(country).ifEmpty { country?.let { listOf(it) } ?: emptyList() },
                                director = director,
                                cast = cast,
                                releaseDate = releaseDate,
                                imdbId = imdbId,
                                tmdbId = tmdbId,
                                tmdbUrl = null,
                                audio = null,
                                video = null,
                                bitrate = null,
                                onOpenLink = { link -> runCatching { uriHandler.openUri(link) } }
                            )
                            Spacer(Modifier.height(8.dp))
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
                                run {
                                    com.chris.m3usuite.ui.tv.TvFocusRow(
                                        stateKey = "series:seasons:${seriesStreamId ?: -1}",
                                        itemSpacing = 8.dp,
                                        contentPadding = PaddingValues(end = 8.dp),
                                        itemCount = seasons.size,
                                        itemKey = { idx -> seasons[idx] }
                                    ) { idx ->
                                        val s = seasons[idx]
                                        FilterChip(
                                            modifier = Modifier.graphicsLayer(alpha = DesignTokens.BadgeAlpha)
                                                .then(com.chris.m3usuite.ui.skin.run { Modifier.tvClickable { seasonSel = s } }),
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
                                                .focusable()
                                                .onFocusEvent { epFocused = it.isFocused || it.hasFocus }
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
                                                if (com.chris.m3usuite.BuildConfig.CARDS_V1) {
                                                    com.chris.m3usuite.ui.cards.EpisodeRow(
                                                        title = "S%02dE%02d – %s".format(e.season, e.episodeNum, epName),
                                                        subtitle = e.airDate,
                                                        imageUrl = e.poster,
                                                        onClick = {
                                                            if (resumeSecs != null) playEpisode(e, fromStart = false, resumeSecs = resumeSecs)
                                                            else playEpisode(e, fromStart = true)
                                                        }
                                                    )
                                                } else {
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
                                                }

                                                // Actions (per episode): Resume? → Play → Share
                                                if (com.chris.m3usuite.BuildConfig.MEDIA_ACTIONBAR_V1) {
                                                    val actions = buildList<com.chris.m3usuite.ui.actions.MediaAction> {
                                                        val canPlay = true
                                                        val r = resumeSecs
                                                        if (r != null && r > 0) add(
                                                            com.chris.m3usuite.ui.actions.MediaAction(
                                                                id = com.chris.m3usuite.ui.actions.MediaActionId.Resume,
                                                                label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_resume),
                                                                badge = fmt(r),
                                                                onClick = {
                                                                    com.chris.m3usuite.core.telemetry.Telemetry.event(
                                                                        "ui_action_resume",
                                                                        mapOf(
                                                                            "route" to com.chris.m3usuite.metrics.RouteTag.current,
                                                                            "season" to e.season,
                                                                            "episode" to e.episodeNum
                                                                        )
                                                                    )
                                                                    playEpisode(e, fromStart = false, resumeSecs = r)
                                                                }
                                                            )
                                                        )
                                                        add(
                                                            com.chris.m3usuite.ui.actions.MediaAction(
                                                                id = com.chris.m3usuite.ui.actions.MediaActionId.Play,
                                                                label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_play),
                                                                primary = true,
                                                                enabled = canPlay,
                                                                onClick = {
                                                                    com.chris.m3usuite.core.telemetry.Telemetry.event(
                                                                        "ui_action_play",
                                                                        mapOf(
                                                                            "route" to com.chris.m3usuite.metrics.RouteTag.current,
                                                                            "season" to e.season,
                                                                            "episode" to e.episodeNum
                                                                        )
                                                                    )
                                                                    if (resumeSecs != null) playEpisode(e, fromStart = false, resumeSecs = resumeSecs)
                                                                    else playEpisode(e, fromStart = true)
                                                                }
                                                            )
                                                        )
                                                        add(
                                                            com.chris.m3usuite.ui.actions.MediaAction(
                                                                id = com.chris.m3usuite.ui.actions.MediaActionId.Share,
                                                                label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_share),
                                                                onClick = {
                                                                    val link = e.buildPlayUrl(ctx)
                                                                    com.chris.m3usuite.core.telemetry.Telemetry.event(
                                                                        "ui_action_share",
                                                                        mapOf(
                                                                            "route" to com.chris.m3usuite.metrics.RouteTag.current,
                                                                            "season" to e.season,
                                                                            "episode" to e.episodeNum,
                                                                            "hasLink" to (link?.isNotBlank() == true)
                                                                        )
                                                                    )
                                                                    if (link.isNullOrBlank()) {
                                                                        android.widget.Toast.makeText(ctx, com.chris.m3usuite.R.string.no_link_available, android.widget.Toast.LENGTH_SHORT).show()
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
                                                                        ctx.startActivity(android.content.Intent.createChooser(send, ctx.getString(com.chris.m3usuite.R.string.action_share)))
                                                                    }
                                                                }
                                                            )
                                                        )
                                                    }
                                                    com.chris.m3usuite.ui.actions.MediaActionBar(actions = actions)
                                                } else {
                                                    androidx.compose.material3.TextButton(
                                                        modifier = Modifier.focusScaleOnTv(),
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
        }
    }
    }
    }

    if (showGrantSheet) {
        KidSelectSheet(
            onConfirm = { kidIds ->
                scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.allowBulk(it, "series", listOf(id)) } }
                showGrantSheet = false
            },
            onDismiss = { showGrantSheet = false }
        )
    }
    if (showRevokeSheet) {
        KidSelectSheet(
            onConfirm = { kidIds ->
                scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.disallowBulk(it, "series", listOf(id)) } }
                showRevokeSheet = false
            },
            onDismiss = { showRevokeSheet = false }
        )
    }
}
