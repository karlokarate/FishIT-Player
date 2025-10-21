package com.chris.m3usuite.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.chris.m3usuite.data.repo.EpgRepository
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.data.obx.toMediaItem
import com.chris.m3usuite.player.InternalPlayerScreen
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.components.sheets.KidSelectSheet
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.focus.focusScaleOnTv
import com.chris.m3usuite.ui.focus.tvClickable
import com.chris.m3usuite.ui.layout.FishRow
import com.chris.m3usuite.ui.layout.LiveFishTile
import io.objectbox.android.AndroidScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Zentralisierte ID-Dekodierung (OBX-Stream-ID aus Long extrahieren)
private fun decodeObxLiveId(v: Long): Int? =
    if (v in 1_000_000_000_000L..1_999_999_999_999L) (v - 1_000_000_000_000L).toInt() else null

@UnstableApi
@Composable
fun LiveDetailScreen(
    id: Long,
    onLogo: (() -> Unit)? = null,
    openLive: ((Long) -> Unit)? = null,
    onGlobalSearch: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    LaunchedEffect(id) {
        com.chris.m3usuite.metrics.RouteTag.set("live:$id")
        com.chris.m3usuite.core.debug.GlobalDebug.logTree("live:detail", "tile:$id")
    }
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()

    // --- UI / Haptics ---
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticsEnabled by store.hapticsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val hapticsEnabledState by rememberUpdatedState(newValue = hapticsEnabled)

    // --- Channel/Stream UI State ---
    var title by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf<String?>(null) }

    // --- EPG UI State ---
    var epgNow by remember { mutableStateOf("") }
    var epgNext by remember { mutableStateOf("") }
    var nowStartMs by remember { mutableStateOf<Long?>(null) }
    var nowEndMs by remember { mutableStateOf<Long?>(null) }
    var showEpg by rememberSaveable(id) { mutableStateOf(false) }
    var providerLabel by remember { mutableStateOf<String?>(null) }
    var categoryName by remember { mutableStateOf<String?>(null) }
    var hasArchive by remember { mutableStateOf<Boolean>(false) }
    var liveCategoryId by remember { mutableStateOf<String?>(null) }
    var moreInCategory by remember { mutableStateOf<List<com.chris.m3usuite.model.MediaItem>>(emptyList()) }
    var uiState by remember { mutableStateOf<com.chris.m3usuite.ui.state.UiState<Unit>>(com.chris.m3usuite.ui.state.UiState.Loading) }

    // --- Internal Player State ---
    var showInternal by rememberSaveable(id) { mutableStateOf(false) }
    var internalUrl by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var internalStartMs by rememberSaveable(id) { mutableStateOf<Long?>(null) }
    // Persist headers and mime for rotation
    var internalUa by rememberSaveable(id) { mutableStateOf("") }
    var internalRef by rememberSaveable(id) { mutableStateOf("") }
    var internalMime by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val liveLauncher = com.chris.m3usuite.playback.rememberPlaybackLauncher(onOpenInternal = { pr ->
        internalUrl = pr.url
        internalStartMs = pr.startPositionMs
        internalUa = pr.headers["User-Agent"].orEmpty()
        internalRef = pr.headers["Referer"].orEmpty()
        internalMime = pr.mimeType
        showInternal = true
    })

    // --- Profile / Adult Gate ---
    val profileId by store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) {
        if (profileId <= 0) {
            isAdult = true
            return@LaunchedEffect
        }
        isAdult = withContext(Dispatchers.IO) {
            val p = com.chris.m3usuite.data.obx.ObxStore
                .get(ctx)
                .boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java)
                .get(profileId)
            p?.type != "kid"
        }
    }

    // --- Kid Repo (Whitelist) ---
    val kidRepo = remember { KidContentRepository(ctx) }
    var showGrantSheet by rememberSaveable { mutableStateOf(false) }
    var showRevokeSheet by rememberSaveable { mutableStateOf(false) }

    // --- Helpers ---

    suspend fun buildStreamHeaders(): Map<String, String> =
        com.chris.m3usuite.core.http.RequestHeadersProvider.defaultHeaders(store)

    suspend fun chooseAndPlay() {
        val playUrl = url ?: return
        val allowed = withContext(Dispatchers.IO) {
            if (profileId <= 0) return@withContext true
            val prof = com.chris.m3usuite.data.obx.ObxStore
                .get(ctx)
                .boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java)
                .get(profileId)
            if (prof?.type == "adult" || prof == null) true
            else com.chris.m3usuite.data.repo.MediaQueryRepository(ctx, store).isAllowed("live", id)
        }
        if (!allowed) {
            Toast
                .makeText(ctx, "Nicht freigegeben", Toast.LENGTH_SHORT)
                .show()
            return
        }
        val hdrs = buildStreamHeaders()
        val mimeGuess = com.chris.m3usuite.core.playback.PlayUrlHelper.guessMimeType(playUrl, null)
        liveLauncher.launch(
            com.chris.m3usuite.playback.PlayRequest(
                type = "live",
                mediaId = id,
                url = playUrl,
                headers = hdrs,
                startPositionMs = null,
                mimeType = mimeGuess,
                title = title
            )
        )
    }

    // --- Initiales Laden: Title/Logo/URL + erstes EPG ---
    LaunchedEffect(id) {
        val sid = decodeObxLiveId(id)
        if (sid == null) {
            title = ""; logo = null; url = null
            epgNow = ""; epgNext = ""; nowStartMs = null; nowEndMs = null
            uiState = com.chris.m3usuite.ui.state.UiState.Empty
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            // OBX-Live-Row lesen
            val liveBox = com.chris.m3usuite.data.obx.ObxStore
                .get(ctx)
                .boxFor(com.chris.m3usuite.data.obx.ObxLive::class.java)
            val row = liveBox.query(
                com.chris.m3usuite.data.obx.ObxLive_.streamId.equal(sid.toLong())
            ).build().findFirst()
            // Resolve provider + category labels (best-effort)
            providerLabel = runCatching {
                com.chris.m3usuite.core.xtream.ProviderLabelStore.get(ctx).labelFor(row?.providerKey)
            }.getOrNull()
            categoryName = runCatching {
                row?.categoryId?.let { cid ->
                    com.chris.m3usuite.data.obx.ObxStore.get(ctx)
                        .boxFor(com.chris.m3usuite.data.obx.ObxCategory::class.java)
                        .query(com.chris.m3usuite.data.obx.ObxCategory_.kind.equal("live").and(com.chris.m3usuite.data.obx.ObxCategory_.categoryId.equal(cid)))
                        .build().findFirst()?.categoryName
                }
            }.getOrNull()
            hasArchive = (row?.tvArchive ?: 0) > 0
            liveCategoryId = row?.categoryId
            internalMime = null

            // Play-URL direkt aus XtreamUrlFactory/OBX ableiten (kein Client-Init)
            val playUrl: String? = runCatching {
                row?.let { it.toMediaItem(ctx).url }
            }.getOrNull()

            // Erstes Now/Next aus REST
            val nowNext = runCatching {
                EpgRepository(ctx, store).nowNext(sid, 2)
            }.getOrElse { emptyList() }

            // Auf UI-State mappen (Main Thread)
            withContext(Dispatchers.Main) {
                title = row?.name.orEmpty()
                logo = row?.logo
                url = playUrl

                val now = nowNext.getOrNull(0)
                val nxt = nowNext.getOrNull(1)
                epgNow = now?.title.orEmpty()
                epgNext = nxt?.title.orEmpty()
                nowStartMs = now?.start?.toLongOrNull()?.let { it * 1000 }
                nowEndMs = now?.end?.toLongOrNull()?.let { it * 1000 }
            }
            uiState = com.chris.m3usuite.ui.state.UiState.Success(Unit)
        }
    }

    // --- Direkter OBX-EPG-Read mit Live-Subscription ---
    DisposableEffect(id) {
        val sid = decodeObxLiveId(id)
        if (sid == null) return@DisposableEffect onDispose { }

        val box = com.chris.m3usuite.data.obx.ObxStore
            .get(ctx)
            .boxFor(com.chris.m3usuite.data.obx.ObxEpgNowNext::class.java)

        val query = box.query(
            com.chris.m3usuite.data.obx.ObxEpgNowNext_.streamId.equal(sid.toLong())
        ).build()

        // initial
        query.findFirst()?.let { row ->
            epgNow = row.nowTitle.orEmpty()
            epgNext = row.nextTitle.orEmpty()
            nowStartMs = row.nowStartMs
            nowEndMs = row.nowEndMs
        }

        val sub = query.subscribe()
            .on(AndroidScheduler.mainThread())
            .observer { results ->
                val row = results.firstOrNull() ?: return@observer
                epgNow = row.nowTitle.orEmpty()
                epgNext = row.nextTitle.orEmpty()
                nowStartMs = row.nowStartMs
                nowEndMs = row.nowEndMs
            }

        onDispose { sub.cancel() }
    }

    // Fetch more channels from same category (best-effort)
    val liveRepo = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }
    LaunchedEffect(liveCategoryId) {
        val cid = liveCategoryId ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                moreInCategory = liveRepo.liveByCategoryPaged(cid, 0, 48).map { it.toMediaItem(ctx) }.filter { it.id != id }
            }.onFailure { moreInCategory = emptyList() }
        }
    }

    // --- Interner Player Fullscreen ---
    if (showInternal) {
        val hdrs = buildMap<String, String> {
            if (internalUa.isNotBlank()) put("User-Agent", internalUa)
            if (internalRef.isNotBlank()) put("Referer", internalRef)
        }
        InternalPlayerScreen(
            url = internalUrl.orEmpty(),
            type = "live",
            mediaId = id,
            startPositionMs = internalStartMs,
            headers = hdrs,
            onExit = { showInternal = false },
            originLiveLibrary = true,
            liveCategoryHint = categoryName
        )
        return
    }

    run {
        when (val s = uiState) {
            is com.chris.m3usuite.ui.state.UiState.Loading -> { com.chris.m3usuite.ui.state.LoadingState(); return }
            is com.chris.m3usuite.ui.state.UiState.Empty -> { com.chris.m3usuite.ui.state.EmptyState(); return }
            is com.chris.m3usuite.ui.state.UiState.Error -> { com.chris.m3usuite.ui.state.ErrorState(s.message, s.retry); return }
            is com.chris.m3usuite.ui.state.UiState.Success -> { /* render content */ }
        }
    }

    // --- UI: Scaffold + Inhalt ---
    val listState = com.chris.m3usuite.ui.state.rememberRouteListState("liveDetail:${id}")
    HomeChromeScaffold(
        title = "Live",
        onSettings = onOpenSettings,
        onSearch = onGlobalSearch,
        onProfiles = null,
        listState = listState,
        onLogo = onLogo,
        enableDpadLeftChrome = false
    ) { pads ->

        Box(
            Modifier
                .fillMaxSize()
                .padding(pads)
        ) {
            val Accent = if (isAdult)
                com.chris.m3usuite.ui.theme.DesignTokens.Accent
            else
                com.chris.m3usuite.ui.theme.DesignTokens.KidAccent

            // Hintergrund: Verlauf + Fish
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
                                Accent.copy(alpha = if (isAdult) 0.12f else 0.20f),
                                Color.Transparent
                            ),
                            radius = with(LocalDensity.current) { 640.dp.toPx() }
                        )
                    )
            )
            com.chris.m3usuite.ui.fx.FishBackground(
                modifier = Modifier.align(Alignment.Center).size(520.dp),
                alpha = 0.05f,
                neutralizeUnderlay = true
            )

            // Karte mit Inhalt
            com.chris.m3usuite.ui.common.AccentCard(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                accent = Accent
            ) {
                Column(Modifier.animateContentSize()) {
                    if (com.chris.m3usuite.BuildConfig.DETAIL_SCAFFOLD_V1) {
                        val actions = buildList<com.chris.m3usuite.ui.actions.MediaAction> {
                            val playEnabled = url != null
                            add(
                                com.chris.m3usuite.ui.actions.MediaAction(
                                    id = com.chris.m3usuite.ui.actions.MediaActionId.Play,
                                    label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_play),
                                    primary = true,
                                    enabled = playEnabled,
                                    onClick = {
                                        com.chris.m3usuite.core.telemetry.Telemetry.event("ui_action_play", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current))
                                        scope.launch {
                                            if (hapticsEnabledState) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            chooseAndPlay()
                                        }
                                    }
                                )
                            )
                            add(
                                com.chris.m3usuite.ui.actions.MediaAction(
                                    id = com.chris.m3usuite.ui.actions.MediaActionId.OpenEpg,
                                    label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_open_epg),
                                    onClick = {
                                        com.chris.m3usuite.core.telemetry.Telemetry.event("ui_action_open_epg", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current))
                                        showEpg = true
                                    }
                                )
                            )
                            if (!url.isNullOrBlank()) add(
                                com.chris.m3usuite.ui.actions.MediaAction(
                                    id = com.chris.m3usuite.ui.actions.MediaActionId.Share,
                                    label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_share),
                                    onClick = {
                                        com.chris.m3usuite.core.telemetry.Telemetry.event("ui_action_share", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current))
                                        val link = url
                                        if (link.isNullOrBlank()) {
                                            Toast.makeText(ctx, com.chris.m3usuite.R.string.no_link_available, Toast.LENGTH_SHORT).show()
                                        } else {
                                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(android.content.Intent.EXTRA_SUBJECT, title.ifBlank { "Live-Link" })
                                                putExtra(android.content.Intent.EXTRA_TEXT, link)
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            ctx.startActivity(android.content.Intent.createChooser(send, ctx.getString(com.chris.m3usuite.R.string.action_share)))
                                        }
                                    }
                                )
                            )
                            // Favorites add/remove for adults
                            val liveId = decodeObxLiveId(id)
                            if (liveId != null && isAdult) {
                                val favCsv by store.favoriteLiveIdsCsv.collectAsStateWithLifecycle(initialValue = "")
                                val inFav = androidx.compose.runtime.remember(favCsv) {
                                    favCsv.split(',').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }.contains(liveId)
                                }
                                if (!inFav) add(
                                    com.chris.m3usuite.ui.actions.MediaAction(
                                        id = com.chris.m3usuite.ui.actions.MediaActionId.AddToList,
                                        label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_add_to_list),
                                        enabled = true,
                                        onClick = {
                                            scope.launch {
                                                val current = store.favoriteLiveIdsCsv.first()
                                                val set = current.split(',').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }.toMutableSet()
                                                if (set.add(liveId)) {
                                                    store.setFavoriteLiveIdsCsv(set.joinToString(","))
                                                    runCatching { com.chris.m3usuite.work.SchedulingGateway.refreshFavoritesEpgNow(ctx) }
                                                    com.chris.m3usuite.core.telemetry.Telemetry.event("ui_action_add_favorite", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current, "streamId" to liveId))
                                                }
                                            }
                                        }
                                    )
                                ) else add(
                                    com.chris.m3usuite.ui.actions.MediaAction(
                                        id = com.chris.m3usuite.ui.actions.MediaActionId.RemoveFromList,
                                        label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_remove_from_list),
                                        enabled = true,
                                        onClick = {
                                            scope.launch {
                                                val current = store.favoriteLiveIdsCsv.first()
                                                val set = current.split(',').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }.toMutableSet()
                                                if (set.remove(liveId)) {
                                                    store.setFavoriteLiveIdsCsv(set.joinToString(","))
                                                    runCatching { com.chris.m3usuite.work.SchedulingGateway.refreshFavoritesEpgNow(ctx) }
                                                    com.chris.m3usuite.core.telemetry.Telemetry.event("ui_action_remove_favorite", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current, "streamId" to liveId))
                                                }
                                            }
                                        }
                                    )
                                )
                            }
                        }
                        val genres = buildList {
                            if (hasArchive) add("Archiv")
                        }
                        val meta = com.chris.m3usuite.ui.detail.DetailMeta(
                            provider = providerLabel,
                            category = categoryName,
                            genres = genres
                        )
                        com.chris.m3usuite.ui.detail.DetailHeader(
                            title = title,
                            subtitle = null,
                            heroUrl = null,
                            posterUrl = logo,
                            actions = actions,
                            meta = meta
                        )
                    }
                    
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        providerLabel?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
                        categoryName?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
                        if (hasArchive) Text("Archiv", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Accent.copy(alpha = 0.35f)
                    )

                    // Legacy header (logo/title/epg/actions) only when scaffold is OFF
                    if (!com.chris.m3usuite.BuildConfig.DETAIL_SCAFFOLD_V1) {
                    // Logo → 1. Klick: EPG, 2. Klick: Play
                    com.chris.m3usuite.ui.util.AppAsyncImage(
                        url = logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        crossfade = true,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(140.dp)
                            .clip(CircleShape)
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                CircleShape
                            )
                            .semantics { role = Role.Button }
                            .then(
                                if (url != null)
                                    Modifier.tvClickable(
                                        role = Role.Button,
                                        shape = CircleShape,
                                        brightenContent = false,
                                        autoBringIntoView = false
                                    ) {
                                        if (showEpg) {
                                            showEpg = false
                                            scope.launch {
                                                if (hapticsEnabledState) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                chooseAndPlay()
                                            }
                                        } else {
                                            showEpg = true
                                        }
                                    }
                                else Modifier
                            )
                     )

                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 12.dp)
                    )

                    // EPG-Badge
                    Spacer(Modifier.size(8.dp))
                    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    if (epgNow.isNotBlank() || epgNext.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.70f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    CircleShape
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (epgNow.isNotBlank()) {
                                    val meta = if (
                                        nowStartMs != null &&
                                        nowEndMs != null &&
                                        nowEndMs!! > nowStartMs!!
                                    ) {
                                        val range = fmt.format(Date(nowStartMs!!)) +
                                                "–" + fmt.format(Date(nowEndMs!!))
                                        val rem = ((nowEndMs!! - System.currentTimeMillis())
                                            .coerceAtLeast(0L) / 60000L).toInt()
                                        " $range • noch ${rem}m"
                                    } else ""
                                    Text(
                                        text = "Jetzt: $epgNow$meta",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                                if (epgNext.isNotBlank()) {
                                    Text(
                                        text = "Danach: $epgNext",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Play + Actions
                    Spacer(Modifier.size(12.dp))
                    val playEnabled by remember(url) { derivedStateOf { url != null } }
                    if (com.chris.m3usuite.BuildConfig.MEDIA_ACTIONBAR_V1) {
                        val actions = buildList<com.chris.m3usuite.ui.actions.MediaAction> {
                            add(
                                com.chris.m3usuite.ui.actions.MediaAction(
                                    id = com.chris.m3usuite.ui.actions.MediaActionId.Play,
                                    label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_play),
                                    primary = true,
                                    enabled = playEnabled,
                                    onClick = {
                                        com.chris.m3usuite.core.telemetry.Telemetry.event("ui_action_play", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current))
                                        scope.launch {
                                            if (hapticsEnabledState) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            chooseAndPlay()
                                        }
                                    }
                                )
                            )
                            add(
                                com.chris.m3usuite.ui.actions.MediaAction(
                                    id = com.chris.m3usuite.ui.actions.MediaActionId.OpenEpg,
                                    label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_open_epg),
                                    onClick = {
                                        com.chris.m3usuite.core.telemetry.Telemetry.event("ui_action_open_epg", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current))
                                        showEpg = true
                                    }
                                )
                            )
                            if (!url.isNullOrBlank()) add(
                                com.chris.m3usuite.ui.actions.MediaAction(
                                    id = com.chris.m3usuite.ui.actions.MediaActionId.Share,
                                    label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_share),
                                    onClick = {
                                        com.chris.m3usuite.core.telemetry.Telemetry.event("ui_action_share", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current))
                                        val link = url
                                        if (link.isNullOrBlank()) {
                                            Toast.makeText(ctx, com.chris.m3usuite.R.string.no_link_available, Toast.LENGTH_SHORT).show()
                                        } else {
                                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(android.content.Intent.EXTRA_SUBJECT, title.ifBlank { "Live-Link" })
                                                putExtra(android.content.Intent.EXTRA_TEXT, link)
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            ctx.startActivity(android.content.Intent.createChooser(send, ctx.getString(com.chris.m3usuite.R.string.action_share)))
                                        }
                                    }
                                )
                            )
                            // Live favorites add/remove if permitted
                            val liveId = decodeObxLiveId(id)
                            if (liveId != null && isAdult) {
                                val favCsv by store.favoriteLiveIdsCsv.collectAsStateWithLifecycle(initialValue = "")
                                val inFav = remember(favCsv) {
                                    favCsv.split(',').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }.contains(liveId)
                                }
                                if (!inFav) add(
                                    com.chris.m3usuite.ui.actions.MediaAction(
                                        id = com.chris.m3usuite.ui.actions.MediaActionId.AddToList,
                                        label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_add_to_list),
                                        enabled = true,
                                        onClick = {
                                            scope.launch {
                                                val current = store.favoriteLiveIdsCsv.first()
                                                val set = current.split(',').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }.toMutableSet()
                                                if (set.add(liveId)) {
                                                    store.setFavoriteLiveIdsCsv(set.joinToString(","))
                                                    runCatching { com.chris.m3usuite.work.SchedulingGateway.refreshFavoritesEpgNow(ctx) }
                                                    com.chris.m3usuite.core.telemetry.Telemetry.event("ui_action_add_favorite", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current, "streamId" to liveId))
                                                }
                                            }
                                        }
                                    )
                                ) else add(
                                    com.chris.m3usuite.ui.actions.MediaAction(
                                        id = com.chris.m3usuite.ui.actions.MediaActionId.RemoveFromList,
                                        label = androidx.compose.ui.res.stringResource(com.chris.m3usuite.R.string.action_remove_from_list),
                                        enabled = true,
                                        onClick = {
                                            scope.launch {
                                                val current = store.favoriteLiveIdsCsv.first()
                                                val set = current.split(',').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }.toMutableSet()
                                                if (set.remove(liveId)) {
                                                    store.setFavoriteLiveIdsCsv(set.joinToString(","))
                                                    runCatching { com.chris.m3usuite.work.SchedulingGateway.refreshFavoritesEpgNow(ctx) }
                                                    com.chris.m3usuite.core.telemetry.Telemetry.event("ui_action_remove_favorite", mapOf("route" to com.chris.m3usuite.metrics.RouteTag.current, "streamId" to liveId))
                                                }
                                            }
                                        }
                                    )
                                )
                            }
                        }
                        com.chris.m3usuite.ui.actions.MediaActionBar(actions = actions)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.chris.m3usuite.ui.common.TvButton(
                                onClick = {
                                    scope.launch {
                                        if (hapticsEnabledState) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        chooseAndPlay()
                                    }
                                },
                                enabled = playEnabled,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Accent,
                                    contentColor = Color.White
                                )
                            ) { Text("Abspielen") }
                            TextButton(
                                modifier = Modifier.focusScaleOnTv(),
                                onClick = {
                                    val link = url
                                    if (link.isNullOrBlank()) {
                                        Toast.makeText(ctx, "Kein Link verfügbar", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, title.ifBlank { "Live-Link" })
                                            putExtra(android.content.Intent.EXTRA_TEXT, link)
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        ctx.startActivity(android.content.Intent.createChooser(send, "Direktlink teilen"))
                                    }
                                },
                                enabled = playEnabled,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Accent
                                )
                            ) { Text("Link teilen") }
                        }
                    }

                        if (isAdult) {
                            com.chris.m3usuite.ui.common.AppIconButton(
                                icon = com.chris.m3usuite.ui.common.AppIcon.AddKid,
                                variant = com.chris.m3usuite.ui.common.IconVariant.Solid,
                                contentDescription = "Für Kinder freigeben",
                                onClick = { showGrantSheet = true }
                            )
                            com.chris.m3usuite.ui.common.AppIconButton(
                                icon = com.chris.m3usuite.ui.common.AppIcon.RemoveKid,
                                variant = com.chris.m3usuite.ui.common.IconVariant.Solid,
                                contentDescription = "Aus Kinderprofil entfernen",
                                onClick = { showRevokeSheet = true }
                            )
                        }
                    }
                    } // end legacy header branch

                    // More channels in same category
                        if (moreInCategory.isNotEmpty()) {
                            Spacer(Modifier.size(12.dp))
                            Text("Mehr aus Kategorie", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.size(6.dp))
                            val categoryId = liveCategoryId
                            FishRow(
                                items = moreInCategory,
                                stateKey = "liveDetail:more:${categoryId}",
                                edgeLeftExpandChrome = false,
                                onPrefetchKeys = { keys ->
                                    val base = 1_000_000_000_000L
                                    val sids = keys
                                        .filter { it >= base && it < 2_000_000_000_000L }
                                        .map { (it - base).toInt() }
                                    if (sids.isNotEmpty()) {
                                        runCatching { liveRepo.prefetchEpgForVisible(sids, perStreamLimit = 2, parallelism = 4) }
                                    }
                                }
                            ) { media ->
                                LiveFishTile(
                                    media = media,
                                    onOpenDetails = { item -> openLive?.invoke(item.id) },
                                    onPlayDirect = { scope.launch { url = media.url; chooseAndPlay() } }
                                )
                            }
                        }

                    // --- EPG Overlay ---
                    if (showEpg) {
                        val Accent = if (isAdult)
                            com.chris.m3usuite.ui.theme.DesignTokens.Accent
                        else
                            com.chris.m3usuite.ui.theme.DesignTokens.KidAccent

                        AlertDialog(
                            onDismissRequest = { showEpg = false },
                            title = { Text("EPG") },
                            text = {
                                Column {
                                    if (epgNow.isNotBlank()) Text("Jetzt:  $epgNow")
                                    if (epgNext.isNotBlank()) {
                                        Spacer(Modifier.size(6.dp))
                                        Text("Danach: $epgNext")
                                    }
                                    if (epgNow.isBlank() && epgNext.isBlank()) {
                                        Text("Keine EPG-Daten verfügbar.")
                                    }
                                    Spacer(Modifier.size(10.dp))
                                    Text(
                                        "Tipp: Nochmal auf das Sender-Logo tippen startet den Stream.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    modifier = Modifier.focusScaleOnTv(),
                                    onClick = {
                                        showEpg = false
                                        scope.launch {
                                            if (hapticsEnabledState) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            chooseAndPlay()
                                        }
                                    },
                                    enabled = url != null,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Accent
                                    )
                                ) { Text("Jetzt abspielen") }
                            },
                            dismissButton = {
                                TextButton(
                                    modifier = Modifier.focusScaleOnTv(),
                                    onClick = { showEpg = false },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Accent
                                    )
                                ) { Text("Schließen") }
                            }
                        )
                    }

                    // --- Kid Whitelist Sheets (mit Toasts) ---
                    if (showGrantSheet) {
                        KidSelectSheet(
                            onConfirm = { kidIds ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        kidIds.forEach { kidRepo.allowBulk(it, "live", listOf(id)) }
                                    }
                                    Toast.makeText(
                                        ctx,
                                        "Sender freigegeben für ${kidIds.size} Kinder",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                showGrantSheet = false
                            },
                            onDismiss = { showGrantSheet = false }
                        )
                    }
                    if (showRevokeSheet) {
                        KidSelectSheet(
                            onConfirm = { kidIds ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        kidIds.forEach { kidRepo.disallowBulk(it, "live", listOf(id)) }
                                    }
                                    Toast.makeText(
                                        ctx,
                                        "Sender aus ${kidIds.size} Kinderprofil(en) entfernt",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                showRevokeSheet = false
                            },
                            onDismiss = { showRevokeSheet = false }
                        )
                    }

                }
            }
        }
    }
