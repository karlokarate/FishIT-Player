package com.chris.m3usuite.telegram.live

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.telegram.TelegramHeuristics
import com.chris.m3usuite.telegram.TdLibReflection
import com.chris.m3usuite.tg.TgGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

/**
 * Mirror-only Telegram repository that pages media straight from TDLib.
 *
 * The legacy ObjectBox-backed repositories remain disabled while the gate stays in mirror mode.
 * This repository never touches ObjectBox and converts TDLib results directly into [MediaItem]s.
 */
class TelegramLiveRepository(context: Context) {

    private val appContext = context.applicationContext
    private val authState = MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)

    private data class SeriesNumberPattern(
        val regex: Regex,
        val seasonIndex: Int,
        val episodeIndex: Int
    )

    fun pagerForChat(chatId: Long): Pager<Long, MediaItem> {
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = 1, enablePlaceholders = false),
            pagingSourceFactory = { TelegramChatPagingSource(chatId) }
        )
    }

    suspend fun fetchMessageMediaItem(chatId: Long, messageId: Long): MediaItem? = withContext(Dispatchers.IO) {
        if (!TgGate.mirrorOnly()) return@withContext null
        val client = ensureClientReady() ?: return@withContext null
        val fn = TdLibReflection.buildGetMessage(chatId, messageId) ?: return@withContext null
        val result = TdLibReflection.sendForResultDetailed(
            client,
            fn,
            timeoutMs = 2_000,
            retries = 1,
            traceTag = "TelegramLiveRepo:GetMessage[$chatId/$messageId]"
        )
        if (result.error != null) {
            Log.w(
                TAG,
                "GetMessage failed chatId=$chatId messageId=$messageId code=${result.error.code} msg=${result.error.message}"
            )
            return@withContext null
        }
        val payload = result.payload as? TdApi.Message ?: return@withContext null
        toMediaItem(client, chatId, payload)
    }

    suspend fun searchAllVideos(query: String, limit: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        if (!TgGate.mirrorOnly()) return@withContext emptyList()
        val client = ensureClientReady() ?: return@withContext emptyList()
        val filter = TdLibReflection.buildSearchMessagesFilterVideo()
        val chatList = TdLibReflection.buildChatListMain()
        val fn = TdLibReflection.buildSearchMessages(
            chatList = chatList,
            query = trimmed,
            sender = null,
            fromMessageId = 0L,
            offset = 0,
            limit = limit.coerceIn(10, 120),
            filter = filter
        ) ?: return@withContext emptyList()
        val result = TdLibReflection.sendForResultDetailed(
            client,
            fn,
            timeoutMs = 5_000,
            retries = 1,
            traceTag = "TelegramLiveRepo:SearchAll"
        )
        if (result.error != null) {
            Log.w(
                TAG,
                "Global search failed query=$trimmed code=${result.error.code} msg=${result.error.message}"
            )
            return@withContext emptyList()
        }
        val payload = result.payload ?: return@withContext emptyList()
        TdLibReflection.extractMessagesArray(payload).mapNotNull { msgObj ->
            val message = msgObj as? TdApi.Message ?: return@mapNotNull null
            toMediaItem(client, message.chatId, message)
        }
    }

    suspend fun chatTitle(chatId: Long): String? = withContext(Dispatchers.IO) {
        if (!TgGate.mirrorOnly()) return@withContext null
        val client = ensureClientReady() ?: return@withContext null
        val fn = TdLibReflection.buildGetChat(chatId) ?: return@withContext null
        val result = TdLibReflection.sendForResultDetailed(
            client,
            fn,
            timeoutMs = 2_000,
            retries = 1,
            traceTag = "TelegramLiveRepo:GetChat[$chatId]"
        )
        if (result.error != null) return@withContext null
        result.payload?.let { TdLibReflection.extractChatTitle(it) }
    }

    private inner class TelegramChatPagingSource(
        private val chatId: Long
    ) : PagingSource<Long, MediaItem>() {

        override suspend fun load(params: LoadParams<Long>): LoadResult<Long, MediaItem> {
            return withContext(Dispatchers.IO) {
                try {
                    if (!TgGate.mirrorOnly()) {
                        return@withContext LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
                    }
                    val client = ensureClientReady()
                        ?: return@withContext LoadResult.Page(emptyList(), prevKey = null, nextKey = null)

                    val anchor = params.key ?: 0L
                    val loadSize = params.loadSize.coerceIn(20, 60)
                    val filter = TdLibReflection.buildSearchMessagesFilterVideo()
                    val fn = TdLibReflection.buildSearchChatMessages(
                        chatId = chatId,
                        query = "",
                        sender = null,
                        fromMessageId = anchor,
                        offset = -loadSize,
                        limit = loadSize,
                        filter = filter,
                        messageThreadId = 0L
                    ) ?: return@withContext LoadResult.Page(emptyList(), null, null)

                    val result = TdLibReflection.sendForResultDetailed(
                        client,
                        fn,
                        timeoutMs = 5_000,
                        retries = 2,
                        traceTag = "TelegramLiveRepo:Search[$chatId]"
                    )
                    if (result.error != null) {
                        Log.w(
                            TAG,
                            "TDLib search failed chatId=$chatId code=${result.error.code} msg=${result.error.message}"
                        )
                        return@withContext LoadResult.Error(Exception(result.error.message))
                    }
                    val payload = result.payload ?: return@withContext LoadResult.Page(emptyList(), null, null)
                    val rawMessages = TdLibReflection.extractMessagesArray(payload)
                    if (rawMessages.isEmpty()) {
                        return@withContext LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
                    }

                    val mapped = rawMessages.mapNotNull { toMediaItem(client, chatId, it) }
                    if (mapped.isEmpty()) {
                        return@withContext LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
                    }
                    val oldest = mapped.minOf { it.tgMessageId ?: Long.MAX_VALUE }
                    val nextKey = if (oldest == Long.MAX_VALUE || oldest <= 0L) null else oldest
                    LoadResult.Page(
                        data = mapped,
                        prevKey = null,
                        nextKey = nextKey
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "Paging load failed chatId=$chatId: ${t.message}", t)
                    LoadResult.Error(t)
                }
            }
        }

        override fun getRefreshKey(state: PagingState<Long, MediaItem>): Long? = null
    }

    private suspend fun ensureClientReady(): TdLibReflection.ClientHandle? {
        if (!TdLibReflection.available()) return null
        val handle = TdLibReflection.getOrCreateClient(appContext, authState) ?: return null
        if (authState.value == TdLibReflection.AuthState.AUTHENTICATED) return handle
        val ready = withTimeoutOrNull(2_000) {
            authState.filter { it == TdLibReflection.AuthState.AUTHENTICATED }.first()
        }
        return if (ready == TdLibReflection.AuthState.AUTHENTICATED) handle else null
    }

    private fun toMediaItem(
        client: TdLibReflection.ClientHandle?,
        chatId: Long,
        messageObj: Any
    ): MediaItem? {
        val message = messageObj as? TdApi.Message ?: return null
        val messageId = message.id
        if (messageId <= 0L) return null
        val content = message.content ?: return null
        val fileObj = TdLibReflection.findPrimaryFile(content) ?: return null
        val fileInfo = TdLibReflection.extractFileInfo(fileObj) ?: return null
        val caption = TdLibReflection.extractCaptionOrText(message) ?: ""
        val fallbackName = TdLibReflection.extractFileName(content) ?: "Telegram $messageId"
        val heuristicsInput = caption.ifBlank { fallbackName }
        val heuristics = runCatching { TelegramHeuristics.parse(heuristicsInput) }
            .getOrElse { TelegramHeuristics.fallbackParse(fallbackName) }
        val fallbackNumbers = parseSeasonEpisode(heuristicsInput)
        val resolvedSeason = heuristics.season ?: fallbackNumbers?.first
        val resolvedEpisode = heuristics.episode ?: fallbackNumbers?.second
        val hasSeriesHint = heuristics.isSeries || TdLibReflection.isSeriesLike(content, message)
        val isSeries = hasSeriesHint && resolvedSeason != null && resolvedEpisode != null

        val seriesNameCandidate = heuristics.seriesTitle?.trim().takeUnless { it.isNullOrBlank() }
            ?: if (hasSeriesHint) heuristics.title?.trim().takeUnless { it.isNullOrBlank() } else null

        val title = if (isSeries) {
            val base = seriesNameCandidate ?: "Telegram $chatId"
            buildString {
                append(base)
                append(' ')
                append('S')
                append(resolvedSeason!!.toString().padStart(2, '0'))
                append('E')
                append(resolvedEpisode!!.toString().padStart(2, '0'))
            }
        } else {
            val raw = heuristics.title ?: caption
            TelegramHeuristics.cleanMovieTitle(raw).ifBlank { "Telegram $messageId" }
        }

        // 1) Versuche, ein echtes Thumbnail-File aus TDLib zu bekommen (bevorzugt wie bei Xtream: file:// Pfad)
        //    a) direkter Thumb (z. B. video.thumbnail.file.id)
        //    b) bei Photos die größte Size
        var poster: String? = null
        runCatching {
            val thumbIdDirect = TdLibReflection.extractThumbFileId(content)
            val thumbIdFromPhoto = TdLibReflection.extractBestPhotoSizeFileId(content)
            val thumbId = when {
                thumbIdDirect != null && thumbIdDirect > 0 -> thumbIdDirect
                thumbIdFromPhoto != null && thumbIdFromPhoto > 0 -> thumbIdFromPhoto
                else -> null
            }
            if (thumbId != null && client != null) {
                // GetFile(thumbId) -> lokalen Pfad lesen
                val fn = TdLibReflection.buildGetFile(thumbId)
                val res = if (fn != null) TdLibReflection.sendForResultDetailed(
                    client, fn, timeoutMs = 1_000, retries = 1,
                    traceTag = "TelegramLiveRepo:GetThumbFile[$thumbId]"
                ) else null
                val fileInfo = res?.payload?.let { TdLibReflection.extractFileInfo(it) }
                val localPath = fileInfo?.localPath
                if (!localPath.isNullOrEmpty()) {
                    poster = java.io.File(localPath).toURI().toString() // -> file://...
                } else {
                    // Kein lokaler Pfad? Download anstoßen (best effort), ohne zu blockieren.
                    val dl = TdLibReflection.buildDownloadFile(thumbId, /*priority*/8, /*offset*/0, /*limit*/0, /*sync*/false)
                    if (dl != null) {
                        TdLibReflection.sendForResult(
                            client, dl, timeoutMs = 500, retries = 0,
                            traceTag = "TelegramLiveRepo:DownloadThumb[$thumbId]"
                        )
                    }
                }
            }
        }.onFailure {
            Log.d(TAG, "Thumb resolve failed: ${it.message}")
        }

        // 2) Fallback: minithumbnail als data URI benutzen, wenn noch kein file:// zur Verfügung steht.
        if (poster.isNullOrEmpty()) {
            val dataThumb = TdLibReflection.extractMiniThumbnailBytes(content)?.let { bytes ->
                if (bytes.isNotEmpty()) "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP) else null
            }
            poster = dataThumb
        }

        when {
            poster?.startsWith("file://") == true -> Log.d(TAG, "Poster ready (cached file)")
            poster?.startsWith("data:image") == true -> Log.d(TAG, "Poster uses minithumb fallback.")
        }

        val mime = TdLibReflection.extractMimeType(content)
        val duration = TdLibReflection.extractDurationSecs(content)

        val id = if (isSeries) telegramSeriesId(chatId, messageId) else telegramVodId(chatId, messageId)
        return MediaItem(
            id = id,
            type = if (isSeries) "series" else "vod",
            name = title,
            sortTitle = title.lowercase(),
            poster = poster,
            images = poster?.let { listOf(it) } ?: emptyList(),
            source = "TG",
            url = "tg://message?chatId=$chatId&messageId=$messageId",
            tgChatId = chatId,
            tgMessageId = messageId,
            tgFileId = fileInfo.fileId,
            durationSecs = duration,
            plot = caption.ifBlank { null },
            containerExt = mime?.substringAfterLast('/')?.lowercase(),
            providerKey = "telegram_chat_$chatId",
            genreKey = heuristics.language,
            year = heuristics.year
        )
    }

    private fun parseSeasonEpisode(text: String): Pair<Int, Int>? {
        if (text.isBlank()) return null
        val normalized = text.replace('_', ' ')
        for (pattern in SERIES_NUMBER_PATTERNS) {
            val match = pattern.regex.find(normalized)
            if (match != null) {
                val season = match.groupValues.getOrNull(pattern.seasonIndex)?.toIntOrNull()
                val episode = match.groupValues.getOrNull(pattern.episodeIndex)?.toIntOrNull()
                if (season != null && episode != null) return season to episode
            }
        }
        return null
    }

    private fun telegramVodId(chatId: Long, messageId: Long): Long {
        val combined = ((chatId and 0x1FFFFFL) shl 32) or (messageId and 0xFFFFFFFFL)
        return 2_000_000_000_000L + (combined and 0xFFFFFFFFFFFFL)
    }

    private fun telegramSeriesId(chatId: Long, messageId: Long): Long {
        val combined = ((chatId and 0x1FFFFFL) shl 32) or (messageId and 0xFFFFFFFFL)
        return 3_000_000_000_000L + (combined and 0xFFFFFFFFFFFFL)
    }

    companion object {
        private const val TAG = "TelegramLiveRepo"
        private const val PAGE_SIZE = 30
        private val SERIES_NUMBER_PATTERNS = listOf(
            SeriesNumberPattern(Regex("(?i)S\\s*(\\d{1,2})\\s*E\\s*(\\d{1,3})"), seasonIndex = 1, episodeIndex = 2),
            SeriesNumberPattern(Regex("(?i)(\\d{1,2})[x×](\\d{1,3})"), seasonIndex = 1, episodeIndex = 2),
            SeriesNumberPattern(
                Regex("(?i)(?:Season|Staffel)\\s*(\\d{1,2})\\s*(?:Episode|Folge|Ep\\.?|E)\\s*(\\d{1,3})"),
                seasonIndex = 1,
                episodeIndex = 2
            )
        )
    }
}
