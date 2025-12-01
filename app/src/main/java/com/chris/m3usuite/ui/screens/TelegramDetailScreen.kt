package com.chris.m3usuite.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.data.repo.ResumeRepository
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.core.TelegramFileLoader
import com.chris.m3usuite.telegram.domain.TelegramImageRef
import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.telegram.util.TelegramPlayUrl
import com.chris.m3usuite.ui.actions.MediaAction
import com.chris.m3usuite.ui.actions.MediaActionId
import com.chris.m3usuite.ui.detail.DetailMeta
import com.chris.m3usuite.ui.detail.DetailPage
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.util.rememberImageHeaders
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

// Helpers
private fun fmt(totalSecs: Int): String {
    val s = max(0, totalSecs)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

// Constants for Telegram ID encoding/decoding
// Use shared constant from TelegramPlayUrl for consistency
private val TELEGRAM_MEDIA_ID_OFFSET
    get() = com.chris.m3usuite.telegram.util.TelegramPlayUrl.TELEGRAM_MEDIA_ID_OFFSET
private const val TELEGRAM_MEDIA_ID_MAX = 5_000_000_000_000L

// Decode Telegram message ID from MediaItem ID (4e12 offset)
private fun decodeTelegramId(itemId: Long): Long? =
    if (itemId in TELEGRAM_MEDIA_ID_OFFSET until TELEGRAM_MEDIA_ID_MAX) {
        itemId - TELEGRAM_MEDIA_ID_OFFSET
    } else {
        null
    }

private data class LoadedTelegramItem(
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val plot: String?,
    val year: Int?,
    val genres: String?,
    val durationSecs: Int?,
    val playUrl: String,
    val chatId: Long,
    val messageId: Long,
    val fileId: Int?,
    val tmdbUrl: String?,
    val tmdbRating: Double?,
    val director: String?,
    val isAdultContent: Boolean,
    // Phase T2: Image refs for TelegramFileLoader
    val posterRef: TelegramImageRef? = null,
    val backdropRef: TelegramImageRef? = null,
)

/**
 * Load Telegram item details from legacy OBX (ObxTelegramMessage).
 *
 * @deprecated This legacy loading path uses fileId-only URL format without remoteId/uniqueId.
 * It remains for backward compatibility with old ObxTelegramMessage entries that don't have
 * remoteId/uniqueId populated. New code should use [loadTelegramItemByKey] which uses
 * the remoteId-first URL format per the SIP integration contract.
 *
 * @see loadTelegramItemByKey for the preferred Phase D loading path
 */
@Deprecated(
    message = "Use loadTelegramItemByKey for remoteId-first playback wiring (Phase D+)",
    level = DeprecationLevel.WARNING,
)
private suspend fun loadTelegramDetailLegacy(
    ctx: Context,
    itemId: Long,
): LoadedTelegramItem? =
    withContext(Dispatchers.IO) {
        val messageId = decodeTelegramId(itemId) ?: return@withContext null
        val obx = ObxStore.get(ctx)
        val msgBox = obx.boxFor<ObxTelegramMessage>()

        val row =
            msgBox
                .query()
                .equal(ObxTelegramMessage_.messageId, messageId)
                .build()
                .findFirst() ?: return@withContext null

        // Use posterLocalPath with fallback to thumbLocalPath (video thumbnail)
        val poster = row.posterLocalPath ?: row.thumbLocalPath

        LoadedTelegramItem(
            title = row.title ?: row.caption ?: row.fileName ?: "Untitled",
            poster = poster,
            backdrop = poster, // Legacy doesn't have separate backdrop
            plot = row.description,
            year = row.year,
            genres = row.genres,
            durationSecs = row.durationSecs,
            playUrl =
                TelegramPlayUrl
                    .buildFileUrl(row.fileId, row.chatId, row.messageId),
            chatId = row.chatId,
            messageId = row.messageId,
            fileId = row.fileId,
            tmdbUrl = null, // Legacy doesn't have TMDb URL
            tmdbRating = null,
            director = null,
            isAdultContent = false,
        )
    }

/**
 * Load TelegramItem by (chatId, anchorMessageId) from new Phase B repository.
 *
 * Phase D.3: Preferred loading path for TelegramItem-based content.
 * Phase D+: Uses remoteId-first URL building for playback wiring.
 * Phase T2: Includes posterRef and backdropRef for image loading.
 */
private suspend fun loadTelegramItemByKey(
    ctx: Context,
    chatId: Long,
    anchorMessageId: Long,
): LoadedTelegramItem? =
    withContext(Dispatchers.IO) {
        val store = SettingsStore(ctx)
        val repo = TelegramContentRepository(ctx, store)
        val item = repo.getItem(chatId, anchorMessageId) ?: return@withContext null

        // Build play URL from TelegramMediaRef using remoteId-first semantics (Phase D+)
        val playUrl =
            when (item.type) {
                TelegramItemType.MOVIE,
                TelegramItemType.SERIES_EPISODE,
                TelegramItemType.CLIP,
                -> {
                    item.videoRef?.let { ref ->
                        // Phase D+: Use remoteId-first URL building
                        TelegramPlayUrl.buildFileUrl(
                            fileId = ref.fileId,
                            chatId = item.chatId,
                            messageId = item.anchorMessageId,
                            remoteId = ref.remoteId,
                            uniqueId = ref.uniqueId,
                        )
                    }
                }
                else -> null
            }

        // Cannot play without a valid URL
        if (playUrl == null) return@withContext null

        // Convert duration from seconds to display
        val durationSecs =
            item.videoRef?.durationSeconds
                ?: (item.metadata.lengthMinutes?.times(60))

        LoadedTelegramItem(
            title = item.metadata.title ?: "Untitled",
            poster = null, // Will be loaded via TelegramFileLoader using posterRef
            backdrop = null, // Will be loaded via TelegramFileLoader using backdropRef
            plot = null, // TelegramMetadata doesn't have plot
            year = item.metadata.year,
            genres = item.metadata.genres.joinToString(", "),
            durationSecs = durationSecs,
            playUrl = playUrl,
            chatId = item.chatId,
            messageId = item.anchorMessageId,
            fileId = item.videoRef?.fileId,
            tmdbUrl = item.metadata.tmdbUrl,
            tmdbRating = item.metadata.tmdbRating,
            director = item.metadata.director,
            isAdultContent = item.metadata.isAdult,
            // Phase T2: Include image refs for TelegramFileLoader
            posterRef = item.posterRef,
            backdropRef = item.backdropRef,
        )
    }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TelegramDetailScreen(
    id: Long,
    openInternal: ((url: String, startMs: Long?, mimeType: String?) -> Unit)? = null,
    onLogo: (() -> Unit)? = null,
    onGlobalSearch: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    rememberImageHeaders()
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()
    val resumeRepo = remember { ResumeRepository(ctx) }

    var data by remember { mutableStateOf<LoadedTelegramItem?>(null) }
    var resumeSecs by rememberSaveable { mutableStateOf<Int?>(null) }

    // Load detail and resume
    LaunchedEffect(id) {
        data = loadTelegramDetailLegacy(ctx, id)
        resumeSecs =
            withContext(Dispatchers.IO) {
                resumeRepo
                    .recentVod(1)
                    .firstOrNull { it.mediaId == id }
                    ?.positionSecs
            }

        // Log detail screen opened
        data?.let { item ->
            TelegramLogRepository.info(
                source = "TelegramDetailScreen",
                message = "User opened Telegram detail",
                details =
                    mapOf(
                        "mediaId" to id.toString(),
                        "title" to item.title,
                        "playUrl" to item.playUrl,
                        "chatId" to item.chatId.toString(),
                        "messageId" to item.messageId.toString(),
                    ),
            )
        }
    }

    fun play(fromStart: Boolean = false) {
        val item = data ?: return
        val startMs: Long? = if (!fromStart) resumeSecs?.toLong()?.times(1000) else null

        // Log playback started from detail screen
        TelegramLogRepository.info(
            source = "TelegramDetailScreen",
            message = "User started Telegram playback from DetailScreen",
            details =
                mapOf(
                    "mediaId" to id.toString(),
                    "title" to item.title,
                    "playUrl" to item.playUrl,
                    "fromStart" to fromStart.toString(),
                    "startMs" to (startMs?.toString() ?: "0"),
                ),
        )

        scope.launch {
            PlayerChooser.start(
                context = ctx,
                store = store,
                url = item.playUrl,
                headers = emptyMap(),
                startPositionMs = startMs,
                mimeType = null,
            ) { s, resolvedMime ->
                openInternal?.invoke(item.playUrl, s, resolvedMime)
            }
        }
    }

    fun setResume(newSecs: Int) =
        scope.launch(Dispatchers.IO) {
            val pos = max(0, newSecs)
            resumeSecs = pos
            resumeRepo.setVodResume(id, pos)
        }

    fun clearResume() =
        scope.launch(Dispatchers.IO) {
            resumeSecs = null
            resumeRepo.clearVod(id)
        }

    val listState = rememberLazyListState()

    // Profile (adult/kid) — used for accent + backdrop parity with VOD/Series
    val profileId by store.currentProfileId.collectAsState(initial = -1L)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) {
        isAdult =
            if (profileId <= 0L) {
                true
            } else {
                withContext(Dispatchers.IO) {
                    ObxStore
                        .get(ctx)
                        .boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java)
                        .get(profileId)
                        ?.type != "kid"
                }
            }
    }

    HomeChromeScaffold(
        title = "Telegram Details",
        onSettings = onOpenSettings,
        onSearch = onGlobalSearch,
        onProfiles = null,
        listState = listState,
        onLogo = onLogo,
    ) { pads ->
        val hero = data?.backdrop ?: data?.poster
        val lblPlay = stringResource(com.chris.m3usuite.R.string.action_play)
        val lblResume = stringResource(com.chris.m3usuite.R.string.action_resume)

        val actions =
            remember(data, resumeSecs, lblPlay, lblResume) {
                buildList {
                    val canPlay = data != null
                    val resumeLabel = resumeSecs?.let { fmt(it) }

                    if (resumeLabel != null && canPlay) {
                        add(
                            MediaAction(
                                id = MediaActionId.Resume,
                                label = lblResume,
                                badge = resumeLabel,
                                onClick = { play(false) },
                            ),
                        )
                    }

                    add(
                        MediaAction(
                            id = MediaActionId.Play,
                            label = lblPlay,
                            primary = true,
                            enabled = canPlay,
                            onClick = { play(true) },
                        ),
                    )
                }
            }

        val genreList =
            data
                ?.genres
                ?.split(',', ';', '|', '/')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() } ?: emptyList()

        val meta =
            DetailMeta(
                year = data?.year,
                durationSecs = data?.durationSecs,
                videoQuality = null,
                genres = genreList,
                provider = "Telegram",
                category = "Telegram",
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
            containerExt = null,
            rating = data?.tmdbRating,
            mpaaRating = null,
            age = null,
            provider = "Telegram",
            category = "Telegram",
            genres = genreList,
            countries = emptyList(),
            director = data?.director,
            cast = null,
            releaseDate = null,
        )
    }
}

/**
 * Phase D.3: TelegramItem-based detail screen.
 *
 * Loads TelegramItem by (chatId, anchorMessageId) and displays full metadata.
 * Wires playback via PlayerChooser → InternalPlayerEntry.
 *
 * Enforces Telegram ↔ PlayerLifecycle Contract:
 * - MUST build PlaybackContext(type=VOD) from TelegramItem
 * - MUST convert TelegramMediaRef → MediaItem via PlayerChooser
 * - MUST navigate into InternalPlayerEntry for playback
 * - MUST NOT create/release ExoPlayer
 * - MUST NOT modify PlayerView
 * - MUST NOT override activity lifecycle or orientation
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TelegramItemDetailScreen(
    chatId: Long,
    anchorMessageId: Long,
    openInternal: ((url: String, startMs: Long?, mimeType: String?) -> Unit)? = null,
    onLogo: (() -> Unit)? = null,
    onGlobalSearch: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    rememberImageHeaders()
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()
    val resumeRepo = remember { ResumeRepository(ctx) }

    // Phase T2: TelegramFileLoader for image loading
    val fileLoader =
        remember {
            val serviceClient = T_TelegramServiceClient.getInstance(ctx)
            TelegramFileLoader(serviceClient)
        }

    var data by remember { mutableStateOf<LoadedTelegramItem?>(null) }
    // Encode mediaId for resume tracking using TELEGRAM_MEDIA_ID_OFFSET
    val mediaId = TELEGRAM_MEDIA_ID_OFFSET + anchorMessageId
    var resumeSecs by rememberSaveable { mutableStateOf<Int?>(null) }

    // Phase T2: Image paths loaded via TelegramFileLoader
    var posterPath by remember { mutableStateOf<String?>(null) }
    var backdropPath by remember { mutableStateOf<String?>(null) }

    // Load TelegramItem by key (Phase D.3)
    LaunchedEffect(chatId, anchorMessageId) {
        data = loadTelegramItemByKey(ctx, chatId, anchorMessageId)
        resumeSecs =
            withContext(Dispatchers.IO) {
                resumeRepo
                    .recentVod(1)
                    .firstOrNull { it.mediaId == mediaId }
                    ?.positionSecs
            }

        // Log detail screen opened
        data?.let { item ->
            TelegramLogRepository.info(
                source = "TelegramItemDetailScreen",
                message = "User opened TelegramItem detail (Phase D)",
                details =
                    mapOf(
                        "chatId" to chatId.toString(),
                        "anchorMessageId" to anchorMessageId.toString(),
                        "title" to item.title,
                        "playUrl" to item.playUrl,
                        "tmdbUrl" to (item.tmdbUrl ?: "none"),
                        "hasPosterRef" to (item.posterRef != null).toString(),
                        "hasBackdropRef" to (item.backdropRef != null).toString(),
                    ),
            )

            // Phase T2: Prefetch images when detail screen opens
            fileLoader.prefetchImages(item.posterRef, item.backdropRef)
        }
    }

    // Phase T2: Load poster image via TelegramFileLoader
    LaunchedEffect(data?.posterRef) {
        val ref = data?.posterRef ?: return@LaunchedEffect
        posterPath = fileLoader.ensureImageDownloaded(ref)
    }

    // Phase T2: Load backdrop image via TelegramFileLoader
    // Use backdropRef if available, else fall back to posterRef for backdrop
    LaunchedEffect(data?.backdropRef, data?.posterRef) {
        val ref = data?.backdropRef ?: data?.posterRef ?: return@LaunchedEffect
        backdropPath = fileLoader.ensureImageDownloaded(ref)
    }

    fun play(fromStart: Boolean = false) {
        val item = data ?: return
        val startMs: Long? = if (!fromStart) resumeSecs?.toLong()?.times(1000) else null

        // Log playback started (Phase D.4: Playback wiring)
        TelegramLogRepository.info(
            source = "TelegramItemDetailScreen",
            message = "User started Telegram playback (Phase D)",
            details =
                mapOf(
                    "chatId" to chatId.toString(),
                    "anchorMessageId" to anchorMessageId.toString(),
                    "title" to item.title,
                    "playUrl" to item.playUrl,
                    "fromStart" to fromStart.toString(),
                    "startMs" to (startMs?.toString() ?: "0"),
                ),
        )

        // Phase D.4: Wire playback via PlayerChooser → InternalPlayerEntry
        // This respects the Telegram ↔ PlayerLifecycle Contract:
        // - PlayerChooser.start() handles player resolution
        // - openInternal callback triggers navigation to InternalPlayerEntry
        // - We do NOT create/release ExoPlayer here
        scope.launch {
            PlayerChooser.start(
                context = ctx,
                store = store,
                url = item.playUrl,
                headers = emptyMap(),
                startPositionMs = startMs,
                mimeType = null,
            ) { s, resolvedMime ->
                openInternal?.invoke(item.playUrl, s, resolvedMime)
            }
        }
    }

    fun setResume(newSecs: Int) =
        scope.launch(Dispatchers.IO) {
            val pos = max(0, newSecs)
            resumeSecs = pos
            resumeRepo.setVodResume(mediaId, pos)
        }

    fun clearResume() =
        scope.launch(Dispatchers.IO) {
            resumeSecs = null
            resumeRepo.clearVod(mediaId)
        }

    val listState = rememberLazyListState()

    // Profile (adult/kid) — check for adult content filtering
    val profileId by store.currentProfileId.collectAsState(initial = -1L)
    var isAdult by remember { mutableStateOf(true) }
    LaunchedEffect(profileId) {
        isAdult =
            if (profileId <= 0L) {
                true
            } else {
                withContext(Dispatchers.IO) {
                    ObxStore
                        .get(ctx)
                        .boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java)
                        .get(profileId)
                        ?.type != "kid"
                }
            }
    }

    HomeChromeScaffold(
        title = "Telegram Details",
        onSettings = onOpenSettings,
        onSearch = onGlobalSearch,
        onProfiles = null,
        listState = listState,
        onLogo = onLogo,
    ) { pads ->
        // Phase T2: Use loaded image paths from TelegramFileLoader
        // Priority: backdropPath > posterPath > default placeholder
        val hero = backdropPath ?: posterPath
        val poster = posterPath ?: backdropPath
        val lblPlay = stringResource(com.chris.m3usuite.R.string.action_play)
        val lblResume = stringResource(com.chris.m3usuite.R.string.action_resume)

        val actions =
            remember(data, resumeSecs, lblPlay, lblResume) {
                buildList {
                    val canPlay = data != null
                    val resumeLabel = resumeSecs?.let { fmt(it) }

                    if (resumeLabel != null && canPlay) {
                        add(
                            MediaAction(
                                id = MediaActionId.Resume,
                                label = lblResume,
                                badge = resumeLabel,
                                onClick = { play(false) },
                            ),
                        )
                    }

                    add(
                        MediaAction(
                            id = MediaActionId.Play,
                            label = lblPlay,
                            primary = true,
                            enabled = canPlay,
                            onClick = { play(true) },
                        ),
                    )
                }
            }

        val genreList =
            data
                ?.genres
                ?.split(',', ';', '|', '/')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() } ?: emptyList()

        val meta =
            DetailMeta(
                year = data?.year,
                durationSecs = data?.durationSecs,
                videoQuality = null,
                genres = genreList,
                provider = "Telegram",
                category = "Telegram",
            )

        DetailPage(
            isAdult = isAdult,
            pads = pads,
            listState = listState,
            title = data?.title ?: "",
            heroUrl = hero,
            posterUrl = poster ?: hero,
            actions = actions,
            meta = meta,
            headerExtras = {},
            showHeaderMetaChips = true,
            resumeText = null,
            plot = data?.plot,
            year = data?.year,
            durationSecs = data?.durationSecs,
            containerExt = null,
            rating = data?.tmdbRating,
            mpaaRating = null,
            age = null,
            provider = "Telegram",
            category = "Telegram",
            genres = genreList,
            countries = emptyList(),
            director = data?.director,
            cast = null,
            releaseDate = null,
        )
    }
}
