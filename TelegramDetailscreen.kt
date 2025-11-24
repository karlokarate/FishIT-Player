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
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.core.TelegramFileLoader
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
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, sec)
    } else {
        "%d:%02d".format(m, sec)
    }
}

// Telegram ID encoding helpers (shared with MediaItem)
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

        // Resolve poster dynamically via TDLib (posterFileId -> TelegramFileLoader)
        val poster = run {
            val posterId = row.posterFileId ?: row.thumbFileId
            if (posterId != null) {
                val serviceClient = T_TelegramServiceClient.getInstance(ctx)
                val loader = TelegramFileLoader(serviceClient)
                try {
                    loader.ensureThumbDownloaded(posterId)
                } catch (e: Exception) {
                    TelegramLogRepository.error(
                        source = "TelegramDetailScreen",
                        message = "Failed to load poster for messageId=${row.messageId}",
                        exception = e,
                    )
                    null
                }
            } else {
                null
            }
        }

        LoadedTelegramItem(
            title = row.title ?: row.caption ?: row.fileName ?: "Untitled",
            poster = poster,
            plot = row.description,
            year = row.year,
            genres = row.genres,
            durationSecs = row.durationSecs,
            playUrl =
                com.chris.m3usuite.telegram.util.TelegramPlayUrl
                    .buildFileUrl(row.fileId, row.chatId, row.messageId),
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
            data?.let {
                resumeRepo.getResumePosition(
                    url = it.playUrl,
                    fallbackSecs = null,
                )
            }
    }

    val listState = rememberLazyListState()

    HomeChromeScaffold(
        showLogo = true,
        onLogo = onLogo,
        onGlobalSearch = onGlobalSearch,
        onOpenSettings = onOpenSettings,
    ) { pads ->
        val hero = data?.poster
        val lblPlay = stringResource(com.chris.m3usuite.R.string.action_play)
        val lblResume = stringResource(com.chris.m3usuite.R.string.action_resume)

        val actions =
            remember(data, resumeSecs, lblPlay, lblResume) {
                buildList {
                    val canPlay = data != null

                    if (canPlay) {
                        add(
                            MediaAction(
                                id = MediaActionId.Play,
                                label = lblPlay,
                                isPrimary = true,
                                onClick = {
                                    val d = data ?: return@MediaAction
                                    val chooser =
                                        PlayerChooser(
                                            context = ctx,
                                            settings = store,
                                        )
                                    chooser.open(
                                        url = d.playUrl,
                                        mimeType = null,
                                        startMs = null,
                                        preferInternal = openInternal != null,
                                        openInternal = openInternal,
                                    )
                                },
                            ),
                        )
                    }

                    if (canPlay && resumeSecs != null && resumeSecs!! > 0) {
                        add(
                            MediaAction(
                                id = MediaActionId.Resume,
                                label = "$lblResume (${fmt(resumeSecs!!)})",
                                isPrimary = false,
                                onClick = {
                                    val d = data ?: return@MediaAction
                                    val chooser =
                                        PlayerChooser(
                                            context = ctx,
                                            settings = store,
                                        )
                                    chooser.open(
                                        url = d.playUrl,
                                        mimeType = null,
                                        startMs = resumeSecs!! * 1000L,
                                        preferInternal = openInternal != null,
                                        openInternal = openInternal,
                                    )
                                },
                            ),
                        )
                    }
                }
            }

        val isAdult = false // Telegram-Kanäle sind bisher nicht als Adult markiert

        val resumeText =
            resumeSecs?.takeIf { it > 0 }?.let {
                "${stringResource(com.chris.m3usuite.R.string.action_resume)} (${fmt(it)})"
            }

        val genreList =
            data?.genres
                ?.split(",")
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
            resumeText = resumeText,
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
