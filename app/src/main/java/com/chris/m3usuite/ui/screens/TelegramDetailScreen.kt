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
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
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

// Decode Telegram message ID from MediaItem ID (4e12 offset)
private fun decodeTelegramId(itemId: Long): Long? =
    if (itemId in 4_000_000_000_000L until 5_000_000_000_000L) {
        itemId - 4_000_000_000_000L
    } else {
        null
    }

private data class LoadedTelegramItem(
    val title: String,
    val poster: String?,
    val plot: String?,
    val year: Int?,
    val genres: String?,
    val durationSecs: Int?,
    val playUrl: String,
    val chatId: Long,
    val messageId: Long,
    val fileId: Int?,
)

// Load Telegram item details from OBX
private suspend fun loadTelegramDetail(
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
            plot = row.description,
            year = row.year,
            genres = row.genres,
            durationSecs = row.durationSecs,
            playUrl = com.chris.m3usuite.telegram.util.TelegramPlayUrl.buildFileUrl(row.fileId, row.chatId, row.messageId),
            chatId = row.chatId,
            messageId = row.messageId,
            fileId = row.fileId,
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
        data = loadTelegramDetail(ctx, id)
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
                if (openInternal != null) openInternal(item.playUrl, s, resolvedMime)
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
        val hero = data?.poster
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
            rating = null,
            mpaaRating = null,
            age = null,
            provider = "Telegram",
            category = "Telegram",
            genres = genreList,
            countries = emptyList(),
            director = null,
            cast = null,
            releaseDate = null,
        )
    }
}
