package com.chris.m3usuite.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.m3usuite.ui.fx.FadeThrough
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.ui.res.painterResource
import android.os.Build
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import coil3.compose.AsyncImage
import com.chris.m3usuite.data.repo.ResumeRepository
// XtreamRepository not needed for OBX-only flow
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.player.InternalPlayerScreen
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.launch
import kotlin.math.max
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.util.rememberImageHeaders
import com.chris.m3usuite.ui.util.AppPosterImage
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.core.xtream.ProviderLabelStore
// Room removed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import coil3.request.ImageRequest
import com.chris.m3usuite.ui.util.rememberAvatarModel
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.aspectRatio
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.derivedStateOf
import androidx.media3.common.util.UnstableApi
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import com.chris.m3usuite.ui.components.sheets.KidSelectSheet
import com.chris.m3usuite.data.obx.toMediaItem

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

private data class VodDetailPayload(
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val images: List<String>,
    val plot: String?,
    val rating: Double?,
    val year: Int?,
    val genre: String?,
    val director: String?,
    val cast: String?,
    val country: String?,
    val releaseDate: String?,
    val imdbId: String?,
    val tmdbId: String?,
    val trailer: String?,
    val providerLabel: String?,
    val categoryLabel: String?,
    val containerExt: String?,
    val durationSecs: Int?,
    val playUrl: String?,
    val resumeSecs: Int?,
    val isAdultProfile: Boolean,
    val contentAllowed: Boolean,
    val mimeType: String?
)

private fun decodeObxVodId(itemId: Long): Int? =
    if (itemId in 2_000_000_000_000L until 3_000_000_000_000L) (itemId - 2_000_000_000_000L).toInt() else null

private suspend fun loadVodDetail(
    ctx: Context,
    store: SettingsStore,
    mediaRepo: com.chris.m3usuite.data.repo.MediaQueryRepository,
    resumeRepo: ResumeRepository,
    itemId: Long
): VodDetailPayload? = withContext(Dispatchers.IO) {
    val obxVid = decodeObxVodId(itemId) ?: return@withContext null

    suspend fun readRow(): com.chris.m3usuite.data.obx.ObxVod? {
        val box = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
            .boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)
        return box.query(com.chris.m3usuite.data.obx.ObxVod_.vodId.equal(obxVid.toLong())).build().findFirst()
    }

    var row = readRow()
    val needsImport = row == null || row.plot.isNullOrBlank()
    if (needsImport) {
        val repo = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
        runCatching { repo.importVodDetailOnce(obxVid) }
        row = readRow()
        // Vorauslad für bessere UX: Nachbar‑Details (bis 50) nach ProviderKey
        runCatching {
            val key = row?.providerKey
            val ids: List<Int> = if (!key.isNullOrBlank()) {
                repo.vodByProviderKeyNewest(key, 0, 60).map { it.vodId }
            } else {
                repo.vodPagedNewest(0, 60).map { it.vodId }
            }
            // Reduce neighbor prefetch to keep network quiet on low-end panels
            repo.importVodDetailsForIds(ids, max = 16)
        }
    }

    val profileId = store.currentProfileId.first()
    val profile = if (profileId > 0) com.chris.m3usuite.data.obx.ObxStore
        .get(ctx)
        .boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java)
        .get(profileId) else null
    val isAdult = profile?.type != "kid"
    val allowed = if (isAdult) true else mediaRepo.isAllowed("vod", itemId)

    val providerLabel = row?.providerKey?.takeIf { !it.isNullOrBlank() }?.let { key ->
        com.chris.m3usuite.core.xtream.ProviderLabelStore.get(ctx).labelFor(key) ?: key
    }

    val categoryLabel = row?.categoryId?.takeIf { !it.isNullOrBlank() }?.let { catId ->
        val box = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
            .boxFor(com.chris.m3usuite.data.obx.ObxCategory::class.java)
        box.query(
            com.chris.m3usuite.data.obx.ObxCategory_.kind.equal("vod").and(
                com.chris.m3usuite.data.obx.ObxCategory_.categoryId.equal(catId)
            )
        ).build().findFirst()?.categoryName ?: catId
    }

    val images: List<String> = runCatching {
        row?.imagesJson?.let { j ->
            Json.parseToJsonElement(j).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }
    }.getOrNull() ?: emptyList()

    val poster = row?.poster?.takeUnless { it.isBlank() } ?: images.firstOrNull()
    val backdrop = images.firstOrNull { it != poster }

    val durationSecs = row?.durationSecs
    val resumeSecs = resumeRepo.recentVod(1)
        .firstOrNull { it.mediaId == itemId }
        ?.positionSecs

    val port = store.xtPort.first()
    val scheme = if (port == 443) "https" else "http"
    val host = store.xtHost.first()
    val user = store.xtUser.first()
    val pass = store.xtPass.first()
    val playUrl = if (host.isBlank() || user.isBlank() || pass.isBlank()) null else runCatching {
        val http = com.chris.m3usuite.core.http.HttpClientFactory.create(ctx, store)
        val caps = com.chris.m3usuite.core.xtream.ProviderCapabilityStore(ctx)
        val ports = com.chris.m3usuite.core.xtream.EndpointPortStore(ctx)
        val client = com.chris.m3usuite.core.xtream.XtreamClient(http)
        client.initialize(
            scheme = scheme,
            host = host,
            username = user,
            password = pass,
            store = caps,
            portStore = ports,
            portOverride = port
        )
        val raw = client.buildVodPlayUrl(obxVid, row?.containerExt)
        raw
    }.getOrNull()

    val mimeType = com.chris.m3usuite.core.playback.PlayUrlHelper.guessMimeType(playUrl, row?.containerExt)

    val displayTitle = row?.name?.substringAfter(" - ", row?.name.orEmpty()).orEmpty()

    return@withContext VodDetailPayload(
        title = displayTitle,
        poster = poster,
        backdrop = backdrop,
        images = images,
        plot = row?.plot,
        rating = row?.rating,
        year = row?.year,
        genre = row?.genre,
        director = row?.director,
        cast = row?.cast,
        country = row?.country,
        releaseDate = row?.releaseDate,
        imdbId = row?.imdbId,
        tmdbId = row?.tmdbId,
        trailer = row?.trailer,
        providerLabel = providerLabel,
        categoryLabel = categoryLabel,
        containerExt = row?.containerExt,
        durationSecs = durationSecs,
        playUrl = playUrl,
        resumeSecs = resumeSecs,
        isAdultProfile = isAdult,
        contentAllowed = allowed,
        mimeType = mimeType
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VodDetailScreen(
    id: Long,
    // optional: interner Player (url, startMs, mime)
    openInternal: ((url: String, startMs: Long?, mimeType: String?) -> Unit)? = null,
    onLogo: (() -> Unit)? = null,
    onGlobalSearch: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    LaunchedEffect(id) {
        com.chris.m3usuite.metrics.RouteTag.set("vod:$id")
        com.chris.m3usuite.core.debug.GlobalDebug.logTree("vod:detail", "tile:$id")
    }
    val ctx = LocalContext.current
    val headers = rememberImageHeaders()
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()
    val kidRepo = remember { KidContentRepository(ctx) }
    val mediaRepo = remember { com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, store) }
    val obxRepo = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled by store.hapticsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val uriHandler = LocalUriHandler.current

    var title by remember { mutableStateOf("") }
    var poster by remember { mutableStateOf<String?>(null) }
    var backdrop by remember { mutableStateOf<String?>(null) }
    var plot by remember { mutableStateOf<String?>(null) }
    var rating by remember { mutableStateOf<Double?>(null) }
    var year by remember { mutableStateOf<Int?>(null) }
    var genre by remember { mutableStateOf<String?>(null) }
    var director by remember { mutableStateOf<String?>(null) }
    var cast by remember { mutableStateOf<String?>(null) }
    var providerLabel by remember { mutableStateOf<String?>(null) }
    var categoryLabel by remember { mutableStateOf<String?>(null) }
    var containerExtLabel by remember { mutableStateOf<String?>(null) }
    var country by remember { mutableStateOf<String?>(null) }
    var releaseDate by remember { mutableStateOf<String?>(null) }
    var imdbId by remember { mutableStateOf<String?>(null) }
    var tmdbId by remember { mutableStateOf<String?>(null) }
    var trailer by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableStateOf<Int?>(null) }
    var mimeType by remember { mutableStateOf<String?>(null) }

    var resumeSecs by rememberSaveable { mutableStateOf<Int?>(null) }
    val resumeRepo = remember { ResumeRepository(ctx) }

    val profileId by store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    var isAdult by remember { mutableStateOf(true) }
    var contentAllowed by remember { mutableStateOf(true) }
    var detailReady by remember { mutableStateOf(false) }

    // --- Interner Player Zustand (Fullscreen) ---
    var showInternal by rememberSaveable { mutableStateOf(false) }
    var internalUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var internalStartMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var internalUa by rememberSaveable { mutableStateOf("") }
    var internalRef by rememberSaveable { mutableStateOf("") }
    var internalMime by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(id, profileId) {
        detailReady = false
        val payload = loadVodDetail(ctx, store, mediaRepo, resumeRepo, id)
        if (payload == null) {
            title = ""
            poster = null
            backdrop = null
            plot = null
            rating = null
            year = null
            genre = null
            director = null
            cast = null
            providerLabel = null
            categoryLabel = null
            containerExtLabel = null
            country = null
            releaseDate = null
            imdbId = null
            tmdbId = null
            trailer = null
            url = null
            duration = null
            resumeSecs = null
            mimeType = null
            isAdult = true
            contentAllowed = true
            internalMime = null
            detailReady = true
            return@LaunchedEffect
        }

        title = payload.title
        poster = payload.poster
        backdrop = payload.backdrop ?: payload.poster
        plot = payload.plot
        rating = payload.rating
        year = payload.year
        genre = payload.genre
        director = payload.director
        cast = payload.cast
        providerLabel = payload.providerLabel
        categoryLabel = payload.categoryLabel
        containerExtLabel = payload.containerExt?.uppercase()
        country = payload.country
        releaseDate = payload.releaseDate
        imdbId = payload.imdbId
        tmdbId = payload.tmdbId
        trailer = payload.trailer
        url = payload.playUrl
        duration = payload.durationSecs
        resumeSecs = payload.resumeSecs
        isAdult = payload.isAdultProfile
        contentAllowed = payload.contentAllowed
        mimeType = payload.mimeType
        detailReady = true
    }

    LaunchedEffect(id) {
        val obxId = decodeObxVodId(id) ?: return@LaunchedEffect
        obxRepo.vodChanges().collect {
        val row = withContext(Dispatchers.IO) {
            val query = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
                .boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)
                .query(com.chris.m3usuite.data.obx.ObxVod_.vodId.equal(obxId.toLong()))
                .build()
            try {
                query.findFirst()
            } finally {
                query.close()
            }
        } ?: return@collect

            val updatedPlot = row.plot?.takeUnless { it.isNullOrBlank() }
            if (!updatedPlot.isNullOrBlank() && updatedPlot != plot) {
                plot = updatedPlot
            }

            val updatedPoster = row.poster?.takeUnless { it.isBlank() }
            if (!updatedPoster.isNullOrBlank() && updatedPoster != poster) {
                poster = updatedPoster
            }

            val updatedDuration = row.durationSecs
            if (updatedDuration != null && updatedDuration != duration) {
                duration = updatedDuration
            }
        }
    }

    fun setResume(newSecs: Int) = scope.launch {
        val pos = max(0, newSecs)
        resumeSecs = pos
        withContext(Dispatchers.IO) { resumeRepo.setVodResume(id, pos) }
    }

    fun clearResume() = scope.launch {
        resumeSecs = null
        withContext(Dispatchers.IO) { resumeRepo.clearVod(id) }
    }

    // Wenn interner Player aktiv ist, fullscreen anzeigen
    if (showInternal) {
        val hdrs = buildMap<String, String> {
            if (internalUa.isNotBlank()) put("User-Agent", internalUa)
            if (internalRef.isNotBlank()) put("Referer", internalRef)
        }
        InternalPlayerScreen(
            url = internalUrl.orEmpty(),
            type = "vod",
            mediaId = id,
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

    fun play(fromStart: Boolean = false) {
        val startMs: Long? = if (!fromStart) resumeSecs?.toLong()?.times(1000) else null
        scope.launch {
            if (!contentAllowed) {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                android.widget.Toast.makeText(ctx, "Nicht freigegeben", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            // Build request via PlayUrlHelper for consistent routing (matches tile behavior)
            val req = withContext(Dispatchers.IO) {
                // Try OBX→MediaItem; fall back to current URL if needed
                val obx = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
                val vodId = ((id - 2_000_000_000_000L).toInt()).coerceAtLeast(0)
                val row = obx.boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)
                    .query(com.chris.m3usuite.data.obx.ObxVod_.vodId.equal(vodId.toLong()))
                    .build().findFirst()
                val media = row?.toMediaItem(ctx)
                if (media != null) com.chris.m3usuite.core.playback.PlayUrlHelper.forVod(ctx, store, media)
                else url?.let { u -> com.chris.m3usuite.core.playback.PlayUrlHelper.PlayRequest(u, com.chris.m3usuite.core.playback.PlayUrlHelper.defaultHeaders(store), mimeType) }
            }
            if (req == null) return@launch
            PlayerChooser.start(
                context = ctx,
                store = store,
                url = req.url,
                headers = req.headers,
                startPositionMs = startMs,
                mimeType = req.mimeType
            ) { s, resolvedMime ->
                if (openInternal != null) {
                    openInternal(req.url, s, resolvedMime ?: req.mimeType)
                } else {
                    internalUrl = req.url
                    internalStartMs = s
                    internalUa = req.headers["User-Agent"].orEmpty()
                    internalRef = req.headers["Referer"].orEmpty()
                    internalMime = resolvedMime ?: req.mimeType
                    showInternal = true
                }
            }
        }
    }

    // Adult
    LaunchedEffect(profileId) {
        if (profileId <= 0) {
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

    var showGrantSheet by rememberSaveable { mutableStateOf(false) }
    var showRevokeSheet by rememberSaveable { mutableStateOf(false) }

    // Local KidSelectSheet removed; using shared component via import

    val snackHost = remember { SnackbarHostState() }
    val listState = com.chris.m3usuite.ui.state.rememberRouteListState("vodDetail:$id")
    HomeChromeScaffold(
        title = "Details",
        onSettings = onOpenSettings,
        onSearch = onGlobalSearch,
        onProfiles = null,
        listState = listState,
        onLogo = onLogo,
        bottomBar = {}
    ) { pads ->
    Box(modifier = Modifier.fillMaxSize().padding(pads)) {
        val Accent = com.chris.m3usuite.ui.theme.DesignTokens.Accent
        val heroUrl = remember(backdrop, poster) { backdrop ?: poster }
        heroUrl?.let { url ->
            com.chris.m3usuite.ui.util.AppHeroImage(
                url = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer(alpha = 0.5f),
                crossfade = true
            )
        }
        // Background
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
        // Radial accent glow + blurred icon
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.12f), Color.Transparent),
                        radius = with(LocalDensity.current) { 660.dp.toPx() }
                    )
                )
        )
        com.chris.m3usuite.ui.fx.FishBackground(
            modifier = Modifier.align(Alignment.Center).size(540.dp),
            alpha = 0.05f,
            neutralizeUnderlay = true
        )
        com.chris.m3usuite.ui.common.AccentCard(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            accent = Accent
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
        item {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (url != null)
                        Modifier.tvClickable(
                            brightenContent = false,
                            autoBringIntoView = false
                        ) { play(fromStart = false) }
                    else Modifier
                )
        ) {
            com.chris.m3usuite.ui.util.AppPosterImage(
                url = poster ?: heroUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                crossfade = true
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to Color(0x66000000),
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
            )
            // Trailer badge (top-right)
            if (!trailer.isNullOrBlank()) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) { Text("Trailer", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall) }
            }
            // Duration (bottom-right)
            duration?.let { secs ->
                val h = secs / 3600
                val m = (secs % 3600) / 60
                val text = if (h > 0) String.format("%dh %02dm", h, m) else String.format("%dm", m)
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) { Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall) }
            }
        }
        }
        item {
        Column(Modifier.padding(top = 12.dp)) {
            val AccentDyn = if (isAdult) com.chris.m3usuite.ui.theme.DesignTokens.Accent else com.chris.m3usuite.ui.theme.DesignTokens.KidAccent
            val badgeColor = if (!isAdult) AccentDyn.copy(alpha = 0.26f) else AccentDyn.copy(alpha = 0.20f)
            val badgeColorDarker = if (!isAdult) AccentDyn.copy(alpha = 0.32f) else AccentDyn.copy(alpha = 0.26f)
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50), color = badgeColor, contentColor = Color.White, modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .then(if (url != null) Modifier.focusable(true) else Modifier)
                        .clickable(enabled = url != null) { play(fromStart = false) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = AccentDyn.copy(alpha = 0.35f))

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rating?.let { Text("★ ${"%.1f".format(it)}  ") }
                Spacer(Modifier.weight(1f))
                if (isAdult) {
                    com.chris.m3usuite.ui.common.AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.AddKid, variant = com.chris.m3usuite.ui.common.IconVariant.Solid, contentDescription = "Für Kinder freigeben", onClick = { showGrantSheet = true })
                    com.chris.m3usuite.ui.common.AppIconButton(icon = com.chris.m3usuite.ui.common.AppIcon.RemoveKid, variant = com.chris.m3usuite.ui.common.IconVariant.Solid, contentDescription = "Aus Kinderprofil entfernen", onClick = { showRevokeSheet = true })
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = if (resumeSecs != null) "Fortsetzen ab ${fmt(resumeSecs!!)}" else "Abspielen"
                AssistChip(
                    modifier = Modifier.focusScaleOnTv().graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha),
                    onClick = { if (resumeSecs != null) play(false) else play(true) },
                    label = { Text(label) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = AccentDyn.copy(alpha = 0.22f))
                )

                // Direktlink teilen (Xtream)
                AssistChip(
                    modifier = Modifier.focusScaleOnTv().graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha),
                    onClick = {
                        val link = url
                        if (link.isNullOrBlank()) {
                            android.widget.Toast.makeText(ctx, "Kein Link verfügbar", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, title.ifBlank { "VOD-Link" })
                                putExtra(android.content.Intent.EXTRA_TEXT, link)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(android.content.Intent.createChooser(send, "Direktlink teilen"))
                        }
                    },
                    label = { Text("Link teilen") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = AccentDyn.copy(alpha = 0.14f))
                )
            }

            // Thin progress pill across full width (minus 5% margins)
            if ((duration ?: 0) > 0 && (resumeSecs ?: 0) > 0) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    val total = duration ?: 0
                    val prog = (resumeSecs ?: 0).toFloat() / total.toFloat()
                    val clamped = prog.coerceIn(0f, 1f)
                    val errorColor = MaterialTheme.colorScheme.error
                    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                        val w = size.width
                        val h = size.height
                        val y = h / 2f
                        val margin = w * 0.05f
                        val start = Offset(margin, y)
                        val end = Offset(w - margin, y)
                        val fillEnd = Offset(start.x + (end.x - start.x) * clamped, y)
                        drawLine(color = Color.White.copy(alpha = 0.35f), start = start, end = end, strokeWidth = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        drawLine(color = errorColor, start = start, end = fillEnd, strokeWidth = 3.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!plot.isNullOrBlank()) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = badgeColorDarker,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha)
                ) {
                    Text(
                        plot!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .then(if (url != null) Modifier.focusable(true) else Modifier)
                            .clickable(enabled = url != null) { play(fromStart = false) }
                    )
                }
            } else {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = badgeColorDarker.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha)
                ) {
                    Text(
                        "Keine Beschreibung verfügbar",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }

            val genreTags = remember(genre) { parseTags(genre) }
            val countryTags = remember(country) { parseTags(country) }
            val runtimeLabel = remember(duration) {
                duration?.let { secs ->
                    val hours = secs / 3600
                    val mins = (secs % 3600) / 60
                    when {
                        hours > 0 -> "${hours}h ${mins}m"
                        mins > 0 -> "${mins}m"
                        else -> "${secs}s"
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                year?.let { MetaChip("$it") }
                rating?.let { MetaChip("★ ${"%.1f".format(it)}") }
                runtimeLabel?.let { MetaChip(it) }
                containerExtLabel?.let { MetaChip(it) }
                providerLabel?.let { MetaChip(it) }
                categoryLabel?.let { MetaChip(it) }
                genreTags.forEach { tag -> MetaChip(tag) }
                if (genreTags.isEmpty()) {
                    genre?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
                }
                countryTags.forEach { tag -> MetaChip(tag) }
                if (countryTags.isEmpty()) {
                    country?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
                }
            }

            Spacer(Modifier.height(8.dp))
            // People (director/cast) compact lines
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                director?.takeIf { it.isNotBlank() }?.let { Text("Regie: $it", style = MaterialTheme.typography.bodySmall) }
                cast?.takeIf { it.isNotBlank() }?.let { Text("Cast: $it", style = MaterialTheme.typography.bodySmall) }
                releaseDate?.takeIf { it.isNotBlank() }?.let { Text("Release: $it", style = MaterialTheme.typography.bodySmall) }
            }

            Spacer(Modifier.height(10.dp))
            Spacer(Modifier.height(8.dp))
            // Trailer embedded + expandable
            if (!trailer.isNullOrBlank()) {
                val expand = remember { mutableStateOf(false) }
                Text("Trailer", style = MaterialTheme.typography.titleSmall)
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

            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                imdbId?.takeIf { it.isNotBlank() }?.let { id ->
                    val imdbUrl = if (id.startsWith("tt", ignoreCase = true))
                        "https://www.imdb.com/title/$id" else "https://www.imdb.com/find?q=$id"
                    MetaChip("IMDB: $id") { uriHandler.openUri(imdbUrl) }
                }
                tmdbId?.takeIf { it.isNotBlank() }?.let { id ->
                    val tmdbUrl = "https://www.themoviedb.org/movie/$id"
                    MetaChip("TMDB: $id") { uriHandler.openUri(tmdbUrl) }
                }
                releaseDate?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Tippe auf Poster oder Titel, um abzuspielen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        }
        }
        // Overlay sticky-like floating title badge when scrolled
        val showPinned by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }
        if (showPinned) {
            val AccentDyn2 = if (isAdult) com.chris.m3usuite.ui.theme.DesignTokens.Accent else com.chris.m3usuite.ui.theme.DesignTokens.KidAccent
            val badgeColorSticky = if (!isAdult) AccentDyn2.copy(alpha = 0.26f) else AccentDyn2.copy(alpha = 0.20f)
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 24.dp, top = 20.dp)
                ) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50), color = badgeColorSticky, contentColor = Color.White, modifier = Modifier.graphicsLayer(alpha = com.chris.m3usuite.ui.theme.DesignTokens.BadgeAlpha)) {
                        Text(title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                    }
                }
            }
        }
    }

    if (showGrantSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.allowBulk(it, "vod", listOf(id)) } }
        scope.launch { snackHost.showSnackbar("VOD freigegeben für ${kidIds.size} Kinder") }
        showGrantSheet = false
    }, onDismiss = { showGrantSheet = false })
    if (showRevokeSheet) KidSelectSheet(onConfirm = { kidIds ->
        scope.launch(Dispatchers.IO) { kidIds.forEach { kidRepo.disallowBulk(it, "vod", listOf(id)) } }
        scope.launch { snackHost.showSnackbar("VOD aus ${kidIds.size} Kinderprofil(en) entfernt") }
        showRevokeSheet = false
    }, onDismiss = { showRevokeSheet = false })
}}
}

private fun fmt(totalSecs: Int): String {
    val s = max(0, totalSecs)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
