package com.chris.m3usuite.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.WorkerParameters
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.Episode
import com.chris.m3usuite.data.db.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.data.db.TelegramMessage
import com.chris.m3usuite.telegram.TdLibReflection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs

class TelegramSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val mode = inputData.getString(KEY_MODE) ?: return@withContext Result.success()
        val store = SettingsStore(applicationContext)
        val enabled = store.tgEnabled.first()
        if (!enabled || !TdLibReflection.available()) return@withContext Result.success()

        val flow = kotlinx.coroutines.flow.MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
        val client = TdLibReflection.getOrCreateClient(applicationContext, flow) ?: return@withContext Result.retry()
        // Ensure authenticated before syncing
        val authQuery = TdLibReflection.buildGetAuthorizationState()
        val authObj = if (authQuery != null) TdLibReflection.sendForResult(client, authQuery, 2000) else null
        val auth = TdLibReflection.mapAuthorizationState(authObj)
        if (auth != TdLibReflection.AuthState.AUTHENTICATED) {
            return@withContext Result.success() // do nothing unless authorized
        }
        val db = DbProvider.get(applicationContext)

        val chatIdsCsv = when (mode) {
            MODE_VOD -> store.tgSelectedVodChatsCsv.first()
            MODE_SERIES -> store.tgSelectedSeriesChatsCsv.first()
            else -> ""
        }
        val chatIds = chatIdsCsv.split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
        if (chatIds.isEmpty()) return@withContext Result.success()

        var totalProcessed = 0
        for (chatId in chatIds) {
            setProgress(workDataOf("chatId" to chatId, "processed" to totalProcessed))
            val chatObj = TdLibReflection.buildGetChat(chatId)?.let { TdLibReflection.sendForResult(client, it) }
            val chatTitle = chatObj?.let { TdLibReflection.extractChatTitle(it) } ?: "Telegram ${chatId}"

            // For series, ensure parent MediaItem exists
            var seriesStreamId: Int? = null
            if (mode == MODE_SERIES) {
                seriesStreamId = stableSeriesIdFromChat(chatId)
                val parent = db.mediaDao().seriesByStreamId(seriesStreamId)
                if (parent == null) {
                    val m = MediaItem(
                        id = 0,
                        type = "series",
                        streamId = seriesStreamId,
                        name = chatTitle,
                        sortTitle = chatTitle,
                        categoryId = null,
                        categoryName = "Telegram",
                        logo = null,
                        poster = downloadChatPhotoAsPoster(client, chatObj),
                        backdrop = null,
                        epgChannelId = null,
                        year = null,
                        rating = null,
                        durationSecs = null,
                        plot = null,
                        url = null,
                        extraJson = null,
                        source = "TG",
                        tgChatId = chatId,
                        tgMessageId = null,
                        tgFileId = null
                    )
                    db.mediaDao().upsertAll(listOf(m))
                }
            }

            var fromMessageId = 0L
            var page = 0
            val maxPages = 10 // safety (e.g., up to ~1000 msgs)
            while (page < maxPages) {
                val req = TdLibReflection.buildGetChatHistory(chatId, fromMessageId, -1, 100, false) ?: break
                val msgsObj = TdLibReflection.sendForResult(client, req) ?: break
                val list = TdLibReflection.extractMessagesArray(msgsObj)
                if (list.isEmpty()) break
                for (msg in list) {
                    val msgId = TdLibReflection.extractMessageId(msg) ?: continue
                    val content = runCatching { msg.javaClass.getDeclaredField("content").apply { isAccessible = true }.get(msg) }.getOrNull()
                    val fileObj = TdLibReflection.findFirstFile(content) ?: continue
                    val fileInfo = TdLibReflection.extractFileInfo(fileObj) ?: continue
                    val fileUniqueId = TdLibReflection.extractFileUniqueId(fileObj)
                    val supportsStreaming = TdLibReflection.extractSupportsStreaming(content)
                    val date = TdLibReflection.extractMessageDate(msg)
                    val thumbFileId = TdLibReflection.extractThumbFileId(content)
                    val title = TdLibReflection.extractCaptionOrText(msg)?.takeIf { it.isNotBlank() } ?: "Telegram ${msgId}"

                    // index the message
                    db.telegramDao().upsertAll(listOf(TelegramMessage(
                        chatId = chatId,
                        messageId = msgId,
                        fileId = fileInfo.fileId,
                        fileUniqueId = fileUniqueId,
                        supportsStreaming = supportsStreaming,
                        caption = title,
                        date = date,
                        localPath = fileInfo.localPath,
                        thumbFileId = thumbFileId
                    )))

                    if (mode == MODE_VOD) {
                        val posterPath = thumbFileId?.let { downloadSmallFile(client, it) }?.let { p -> if (p.isNotBlank()) "file://${p}" else null }
                        upsertVod(db, chatId, msgId, fileInfo.fileId, title, posterPath, content)
                    } else {
                        val epKey = stableEpisodeId(chatId, msgId)
                        val (season, epnum) = parseSeasonEpisode(title) ?: continue
                        val posterPath = thumbFileId?.let { downloadSmallFile(client, it) }?.let { p -> if (p.isNotBlank()) "file://${p}" else null }
                        upsertEpisode(db, seriesStreamId!!, epKey, season, epnum, title, chatId, msgId, fileInfo.fileId, posterPath, content)
                    }
                    totalProcessed++
                    setProgress(workDataOf("chatId" to chatId, "processed" to totalProcessed))
                }
                fromMessageId = (TdLibReflection.extractMessageId(list.last()) ?: 0L)
                page++
            }
        }
        Result.success()
    }

    private suspend fun upsertVod(
        db: com.chris.m3usuite.data.db.AppDatabase,
        chatId: Long,
        messageId: Long,
        fileId: Int,
        title: String,
        posterFileUrl: String?,
        content: Any?
    ) {
        val dao = db.mediaDao()
        val existing = dao.byTelegram(chatId, messageId)
        val sorted = title
        val duration = extractDurationSecs(content)
        val containerExt = deriveExtension(content, title)
        val item = MediaItem(
            id = existing?.id ?: 0,
            type = "vod",
            streamId = null,
            name = title,
            sortTitle = sorted,
            categoryId = null,
            categoryName = "Telegram",
            logo = null,
            poster = posterFileUrl ?: existing?.poster,
            backdrop = null,
            epgChannelId = null,
            year = null,
            rating = null,
            durationSecs = duration ?: existing?.durationSecs,
            plot = null,
            url = null,
            extraJson = null,
            source = "TG",
            tgChatId = chatId,
            tgMessageId = messageId,
            tgFileId = fileId
        )
        dao.upsertAll(listOf(item))
    }

    private suspend fun upsertEpisode(
        db: com.chris.m3usuite.data.db.AppDatabase,
        seriesStreamId: Int,
        episodeId: Int,
        season: Int,
        epnum: Int,
        title: String,
        chatId: Long,
        messageId: Long,
        fileId: Int,
        posterFileUrl: String?,
        content: Any?
    ) {
        val dao = db.episodeDao()
        val prior = dao.byEpisodeId(episodeId)
        val duration = extractDurationSecs(content)
        val containerExt = deriveExtension(content, title)
        val ep = Episode(
            id = prior?.id ?: 0,
            seriesStreamId = seriesStreamId,
            episodeId = episodeId,
            season = season,
            episodeNum = epnum,
            title = title,
            plot = null,
            durationSecs = duration ?: prior?.durationSecs,
            containerExt = containerExt ?: prior?.containerExt,
            poster = posterFileUrl ?: prior?.poster,
            tgChatId = chatId,
            tgMessageId = messageId,
            tgFileId = fileId
        )
        dao.upsertAll(listOf(ep))
    }

    private fun parseSeasonEpisode(text: String): Pair<Int, Int>? {
        val s = text.lowercase()
        val r1 = Regex("s(\\d{1,2})e(\\d{1,2})")
        val r2 = Regex("(\\d{1,2})x(\\d{1,2})")
        val r3 = Regex("staffel\\s*(\\d{1,2}).{0,4}folge\\s*(\\d{1,2})")
        r1.find(s)?.let { m -> return m.groupValues[1].toInt() to m.groupValues[2].toInt() }
        r2.find(s)?.let { m -> return m.groupValues[1].toInt() to m.groupValues[2].toInt() }
        r3.find(s)?.let { m -> return m.groupValues[1].toInt() to m.groupValues[2].toInt() }
        return null
    }

    private fun stableSeriesIdFromChat(chatId: Long): Int = (abs((chatId % 1_000_000_000L).toInt()))
    private fun stableEpisodeId(chatId: Long, messageId: Long): Int = abs(((chatId xor messageId) % 1_000_000_000L).toInt())

    // --- Helpers (downloads & metadata) ---
    private fun downloadSmallFile(client: TdLibReflection.ClientHandle, fileId: Int): String? {
        val dl = TdLibReflection.buildDownloadFile(fileId, 8, 0, 0, false)
        if (dl != null) TdLibReflection.sendForResult(client, dl, 100)
        var attempts = 0
        while (attempts < 30) {
            val gf = TdLibReflection.buildGetFile(fileId) ?: break
            val fo = TdLibReflection.sendForResult(client, gf) ?: break
            val info = TdLibReflection.extractFileInfo(fo)
            val p = info?.localPath
            if (!p.isNullOrBlank() && java.io.File(p).exists()) return p
            Thread.sleep(100)
            attempts++
        }
        return null
    }

    private fun downloadChatPhotoAsPoster(client: TdLibReflection.ClientHandle, chatObj: Any?): String? {
        if (chatObj == null) return null
        return try {
            val photo = chatObj.javaClass.getDeclaredField("photo").apply { isAccessible = true }.get(chatObj)
            val small = photo?.javaClass?.getDeclaredField("small")?.apply { isAccessible = true }?.get(photo)
            if (small != null) {
                val id = small.javaClass.getDeclaredField("id").apply { isAccessible = true }.getInt(small)
                downloadSmallFile(client, id)?.let { p -> "file://${p}" }
            } else null
        } catch (_: Throwable) { null }
    }

    private fun extractDurationSecs(content: Any?): Int? {
        if (content == null) return null
        val fields = listOf("duration", "videoDuration")
        for (fn in fields) {
            val v = runCatching { content.javaClass.getDeclaredField(fn).apply { isAccessible = true }.getInt(content) }.getOrNull()
            if (v != null && v > 0) return v
        }
        content.javaClass.declaredFields.forEach { f ->
            f.isAccessible = true
            val nested = runCatching { f.get(content) }.getOrNull()
            if (nested != null) for (fn in fields) {
                val v = runCatching { nested.javaClass.getDeclaredField(fn).apply { isAccessible = true }.getInt(nested) }.getOrNull()
                if (v != null && v > 0) return v
            }
        }
        return null
    }

    private fun deriveExtension(content: Any?, title: String): String? {
        val name = TdLibReflection.extractFileName(content) ?: title
        val m = Regex("\\.([a-z0-9]{2,4})$").find(name.lowercase())
        return m?.groupValues?.getOrNull(1)
    }

    companion object {
        const val KEY_MODE = "mode"
        const val MODE_VOD = "vod"
        const val MODE_SERIES = "series"

        fun enqueue(context: Context, mode: String) {
            val data = Data.Builder().putString(KEY_MODE, mode).build()
            val req = OneTimeWorkRequestBuilder<TelegramSyncWorker>().setInputData(data).build()
            val unique = if (mode == MODE_VOD) "tg_sync_vod" else "tg_sync_series"
            WorkManager.getInstance(context).enqueueUniqueWork(unique, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
