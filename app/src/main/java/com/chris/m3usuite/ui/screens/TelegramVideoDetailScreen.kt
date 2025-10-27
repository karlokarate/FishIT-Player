package com.chris.m3usuite.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.chris.m3usuite.R
import com.chris.m3usuite.core.playback.PlayUrlHelper
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.model.isAdultCategory
import com.chris.m3usuite.telegram.live.TelegramLiveRepository
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.chris.m3usuite.ui.actions.MediaAction
import com.chris.m3usuite.ui.actions.MediaActionId
import com.chris.m3usuite.ui.detail.DetailMeta
import com.chris.m3usuite.ui.detail.DetailPage
import com.chris.m3usuite.ui.home.HomeChromeScaffold
import com.chris.m3usuite.ui.state.rememberRouteListState
import com.chris.m3usuite.ui.util.rememberImageHeaders
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramVideoDetailScreen(
    chatId: Long,
    messageId: Long,
    onLogo: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    openInternal: (url: String, startMs: Long?, mimeType: String?) -> Unit
) {
    val ctx = LocalContext.current
    rememberImageHeaders()
    val repo = remember { TelegramLiveRepository(ctx) }
    val service = remember { TelegramServiceClient(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val listState = rememberRouteListState("telegramDetail:$chatId:$messageId")
    val uriHandler = LocalUriHandler.current

    var media by remember { mutableStateOf<MediaItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chatId, messageId) {
        loading = true
        error = null
        val result = runCatching { repo.fetchMessageMediaItem(chatId, messageId) }
        media = result.getOrNull()
        error = result.exceptionOrNull()?.message
        loading = false
    }

    val playLabel = stringResource(R.string.action_play)
    val playAction = remember(media, playLabel) {
        media?.let { item ->
            MediaAction(
                id = MediaActionId.Play,
                label = playLabel,
                primary = true,
                onClick = {
                    scope.launch {
                        val resolvedChat = item.tgChatId ?: chatId
                        val resolvedMessage = item.tgMessageId ?: messageId
                        val playUrl = runCatching {
                            PlayUrlHelper.tgPlayUri(resolvedChat, resolvedMessage, service).toString()
                        }.getOrElse { err ->
                            Log.w(
                                "TelegramDetail",
                                "tgPlayUri failed chatId=$resolvedChat messageId=$resolvedMessage: ${err.message}"
                            )
                            return@launch
                        }
                        val mime = PlayUrlHelper.guessMimeType(playUrl, item.containerExt)
                        openInternal(playUrl, null, mime)
                    }
                }
            )
        }
    }

    HomeChromeScaffold(
        title = media?.name?.takeIf { it.isNotBlank() } ?: "Telegram",
        onSearch = onOpenSearch,
        onSettings = onOpenSettings,
        onLogo = onLogo,
        listState = listState,
        enableDpadLeftChrome = false
    ) { pads ->
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            media == null -> {
                val message = error ?: "Telegram-Inhalt konnte nicht geladen werden."
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = message, style = MaterialTheme.typography.bodyMedium)
                }
            }

            else -> {
                val item = media!!
                val hero = item.backdrop ?: item.poster ?: item.images.firstOrNull()
                val meta = DetailMeta(
                    year = item.year,
                    durationSecs = item.durationSecs,
                    genres = item.genre?.let { listOf(it) } ?: emptyList(),
                    provider = item.providerKey,
                    category = item.categoryName ?: item.categoryId
                )
                DetailPage(
                    isAdult = item.isAdultCategory(),
                    pads = pads,
                    listState = listState,
                    title = item.name.ifBlank { "Telegram" },
                    heroUrl = hero,
                    posterUrl = item.poster,
                    actions = listOfNotNull(playAction),
                    meta = meta,
                    plot = item.plot,
                    year = item.year,
                    durationSecs = item.durationSecs,
                    containerExt = item.containerExt,
                    provider = item.providerKey,
                    category = item.categoryName ?: item.categoryId,
                    genres = item.genre?.let { listOf(it) } ?: emptyList(),
                    onOpenLink = { link -> runCatching { uriHandler.openUri(link) } },
                    trailerUrl = null
                )
            }
        }
    }
}
