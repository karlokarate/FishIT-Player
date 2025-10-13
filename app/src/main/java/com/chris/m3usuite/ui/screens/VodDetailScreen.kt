package com.chris.m3usuite.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import com.chris.m3usuite.ui.util.rememberImageHeaders
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.detail.DetailMeta
import com.chris.m3usuite.ui.detail.DetailPage
import com.chris.m3usuite.ui.actions.MediaAction
import com.chris.m3usuite.ui.actions.MediaActionId
import com.chris.m3usuite.ui.actions.MediaActionBar
import androidx.compose.ui.res.stringResource
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.data.repo.ResumeRepository
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.ui.components.sheets.KidSelectSheet
import com.chris.m3usuite.ui.util.buildImageRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import com.chris.m3usuite.data.obx.toMediaItem

// --- Helpers ---
private fun fmt(totalSecs: Int): String {
    val s = max(0, totalSecs)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}


// OBX ID decode
private fun decodeObxVodId(itemId: Long): Int? =
    if (itemId in 2_000_000_000_000L until 3_000_000_000_000L) (itemId - 2_000_000_000_000L).toInt() else null

private data class LoadedVod(
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val plot: String?,
    val rating: Double?,
    val durationSecs: Int?,
    val year: Int?,
    val genre: String?,
    val director: String?,
    val cast: String?,
    val country: String?,
    val releaseDate: String?,
    val trailer: String?,
    val providerLabel: String?,
    val categoryLabel: String?,
    val containerExt: String?,
    val playUrl: String?,
    val mimeType: String?
)

// Load VOD details from OBX; enrich via Xtream on-demand
private suspend fun loadVodDetail(
    ctx: Context,
    store: SettingsStore,
    itemId: Long
): LoadedVod? = withContext(Dispatchers.IO) {
    val obxVid = decodeObxVodId(itemId) ?: return@withContext null
    val obx = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
    val vodBox = obx.boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)

    fun read(): com.chris.m3usuite.data.obx.ObxVod? =
        vodBox.query(com.chris.m3usuite.data.obx.ObxVod_.vodId.equal(obxVid.toLong())).build().findFirst()

    var row = read()
    if (row == null || row.plot.isNullOrBlank()) {
        val repo = com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store)
        runCatching { repo.importVodDetailOnce(obxVid) }
        row = read()
    }
    row ?: return@withContext null

    val providerLabel = row.providerKey?.takeIf { it.isNotBlank() }?.let { key ->
        com.chris.m3usuite.core.xtream.ProviderLabelStore.get(ctx).labelFor(key) ?: key
    }
    val categoryLabel = row.categoryId?.takeIf { it.isNotBlank() }?.let { catId ->
        val box = obx.boxFor(com.chris.m3usuite.data.obx.ObxCategory::class.java)
        box.query(
            com.chris.m3usuite.data.obx.ObxCategory_.kind.equal("vod").and(
                com.chris.m3usuite.data.obx.ObxCategory_.categoryId.equal(catId)
            )
        ).build().findFirst()?.categoryName ?: catId
    }

    val images: List<String> = runCatching {
        row.imagesJson?.let { j ->
            Json.parseToJsonElement(j).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }
    }.getOrNull() ?: emptyList()
    val poster = row.poster?.takeUnless { it.isBlank() } ?: images.firstOrNull()
    val backdrop = images.firstOrNull { it != poster }

    val media = row.toMediaItem(ctx)
    val playReq = com.chris.m3usuite.core.playback.PlayUrlHelper.forVod(ctx, store, media)

    LoadedVod(
        title = row.name ?: "",
        poster = poster,
        backdrop = backdrop,
        plot = row.plot,
        rating = row.rating,
        durationSecs = row.durationSecs,
        year = row.year,
        genre = row.genre,
        director = row.director,
        cast = row.cast,
        country = row.country,
        releaseDate = row.releaseDate,
        trailer = row.trailer,
        providerLabel = providerLabel,
        categoryLabel = categoryLabel,
        containerExt = row.containerExt,
        playUrl = playReq?.url,
        mimeType = playReq?.mimeType
    )
}

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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VodDetailScreen(
    id: Long,
    openInternal: ((url: String, startMs: Long?, mimeType: String?) -> Unit)? = null,
    openVod: ((Long) -> Unit)? = null,
    onLogo: (() -> Unit)? = null,
    onGlobalSearch: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val headers = rememberImageHeaders()
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()
    val kidRepo = remember { KidContentRepository(ctx) }
    val resumeRepo = remember { ResumeRepository(ctx) }

    var data by remember { mutableStateOf<LoadedVod?>(null) }
    var url by remember { mutableStateOf<String?>(null) }
    var mimeType by remember { mutableStateOf<String?>(null) }
    var resumeSecs by rememberSaveable { mutableStateOf<Int?>(null) }

    // Load detail and resume
    LaunchedEffect(id) {
        data = loadVodDetail(ctx, store, id)
        resumeSecs = withContext(Dispatchers.IO) { resumeRepo.recentVod(1).firstOrNull { it.mediaId == id }?.positionSecs }
        url = data?.playUrl
        mimeType = data?.mimeType
    }

    fun play(fromStart: Boolean = false) {
        val startMs: Long? = if (!fromStart) resumeSecs?.toLong()?.times(1000) else null
        val u = url ?: return
        scope.launch {
            PlayerChooser.start(
                context = ctx,
                store = store,
                url = u,
                headers = com.chris.m3usuite.core.playback.PlayUrlHelper.defaultHeaders(store),
                startPositionMs = startMs,
                mimeType = mimeType
            ) { s, resolvedMime ->
                if (openInternal != null) openInternal(u, s, resolvedMime ?: mimeType)
            }
        }
    }

    fun setResume(newSecs: Int) = scope.launch(Dispatchers.IO) {
        val pos = max(0, newSecs)
        resumeSecs = pos
        resumeRepo.setVodResume(id, pos)
    }
    fun clearResume() = scope.launch(Dispatchers.IO) { resumeSecs = null; resumeRepo.clearVod(id) }

    val listState = rememberLazyListState()
    val snackHost = remember { SnackbarHostState() }
    // Kid whitelist sheets need to be declared before actions builder uses them
    var showGrantSheet by rememberSaveable { mutableStateOf(false) }
    var showRevokeSheet by rememberSaveable { mutableStateOf(false) }
    val permRepo = remember { com.chris.m3usuite.data.repo.PermissionRepository(ctx, store) }
    var canEditWhitelist by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { canEditWhitelist = permRepo.current().canEditWhitelist }
    // Profile (adult/kid) — used for accent + backdrop parity with Series
    val profileId by store.currentProfileId.collectAsState(initial = -1L)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) {
        isAdult = if (profileId <= 0L) true else withContext(Dispatchers.IO) {
            com.chris.m3usuite.data.obx.ObxStore
                .get(ctx)
                .boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java)
                .get(profileId)?.type != "kid"
        }
    }

    HomeChromeScaffold(
        title = "Details",
        onSettings = onOpenSettings,
        onSearch = onGlobalSearch,
        onProfiles = null,
        listState = listState,
        onLogo = onLogo,
        showBottomBar = false
    ) { pads ->
        val hero = data?.backdrop ?: data?.poster
        val uriHandler = LocalUriHandler.current
        val lblPlay = stringResource(com.chris.m3usuite.R.string.action_play)
        val lblResume = stringResource(com.chris.m3usuite.R.string.action_resume)
        val lblTrailer = stringResource(com.chris.m3usuite.R.string.action_trailer)
        val lblShare = stringResource(com.chris.m3usuite.R.string.action_share)
        val lblGrant = "Für Kinder freigeben"
        val lblRevoke = "Freigabe entfernen"
        val actions = remember(data, resumeSecs, url, lblPlay, lblResume, lblTrailer, lblShare, lblGrant, lblRevoke, uriHandler, canEditWhitelist) {
            buildList {
                val canPlay = url != null
                val resumeLabel = resumeSecs?.let { fmt(it) }
                if (resumeLabel != null && canPlay) add(
                    MediaAction(
                        id = MediaActionId.Resume,
                        label = lblResume,
                        badge = resumeLabel,
                        onClick = { play(false) }
                    )
                )
                add(
                    MediaAction(
                        id = MediaActionId.Play,
                        label = lblPlay,
                        primary = true,
                        enabled = canPlay,
                        onClick = { if (resumeSecs != null) play(false) else play(true) }
                    )
                )
                val tr = normalizeTrailerUrl(data?.trailer)
                if (!tr.isNullOrBlank()) add(
                    MediaAction(
                        id = MediaActionId.Trailer,
                        label = lblTrailer,
                        onClick = { runCatching { uriHandler.openUri(tr) } }
                    )
                )
                if (!url.isNullOrBlank()) add(
                    MediaAction(
                        id = MediaActionId.Share,
                        label = lblShare,
                        onClick = {
                            val link = url
                            if (link.isNullOrBlank()) {
                                android.widget.Toast.makeText(ctx, com.chris.m3usuite.R.string.no_link_available, android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, data?.title ?: "VOD-Link")
                                    putExtra(android.content.Intent.EXTRA_TEXT, link)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(android.content.Intent.createChooser(send, ctx.getString(com.chris.m3usuite.R.string.action_share)))
                            }
                        }
                    )
                )
                if (canEditWhitelist) {
                    add(
                        MediaAction(
                            id = MediaActionId.AddToList,
                            label = lblGrant,
                            onClick = { showGrantSheet = true }
                        )
                    )
                    add(
                        MediaAction(
                            id = MediaActionId.RemoveFromList,
                            label = lblRevoke,
                            onClick = { showRevokeSheet = true }
                        )
                    )
                }
            }
        }

        val meta = DetailMeta(
            year = data?.year,
            durationSecs = data?.durationSecs,
            videoQuality = data?.containerExt?.uppercase(),
            genres = data?.genre?.split(',', ';', '|', '/')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            provider = data?.providerLabel,
            category = data?.categoryLabel
        )

        DetailPage(
            isAdult = isAdult,
            pads = pads,
            listState = listState,
            title = data?.title ?: "",
            heroUrl = hero,
            posterUrl = data?.poster ?: hero,
            actions = actions,
            meta = meta,
            headerExtras = {},
            showHeaderMetaChips = false,
            resumeText = null,
            plot = data?.plot,
            year = data?.year,
            durationSecs = data?.durationSecs,
            containerExt = data?.containerExt,
            rating = data?.rating,
            mpaaRating = null,
            age = null,
            provider = data?.providerLabel,
            category = data?.categoryLabel,
            genres = data?.genre?.split(',', ';', '|', '/')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            countries = data?.country?.let { listOf(it) } ?: emptyList(),
            director = data?.director,
            cast = data?.cast,
            releaseDate = data?.releaseDate,
            imdbId = null,
            tmdbId = null,
            tmdbUrl = null,
            audio = null,
            video = null,
            bitrate = null,
            onOpenLink = { link -> runCatching { uriHandler.openUri(link) } },
            trailerUrl = normalizeTrailerUrl(data?.trailer),
            trailerHeaders = null,
            extraItems = null,
            modifier = Modifier.fillMaxSize()
        )
    }

    // Sheets
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
}
