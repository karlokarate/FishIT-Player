package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.TelegramHeuristics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class TelegramContentRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    private val store get() = ObxStore.get(context)

    suspend fun selectedChatsVod(): List<Long> = settings.tgSelectedVodChatsCsv.first()
        .split(',')
        .mapNotNull { it.trim().toLongOrNull() }
        .distinct()

    suspend fun selectedChatsSeries(): List<Long> = settings.tgSelectedSeriesChatsCsv.first()
        .split(',')
        .mapNotNull { it.trim().toLongOrNull() }
        .distinct()

    suspend fun recentVodByChat(chatId: Long, limit: Int = 60, offset: Int = 0): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!selectedChatsVod().contains(chatId)) return@withContext emptyList()
        val box = store.boxFor(ObxTelegramMessage::class.java)
        val q = box.query(
            ObxTelegramMessage_.chatId.equal(chatId)
                .and(ObxTelegramMessage_.captionLower.notNull())
        ).orderDesc(ObxTelegramMessage_.date).build()
        val rows = q.find(offset.toLong(), limit.toLong())
        rows.asSequence()
            .mapNotNull { row ->
                // Treat supportsStreaming as a hint only; consider video types streamable via progressive download.
                val isVideo = (row.mimeType?.lowercase()?.startsWith("video/") == true) || (row.containerExt() != null)
                if (!isVideo) return@mapNotNull null
                val parsed = TelegramHeuristics.parse(row.caption)
                if (parsed.isSeries) return@mapNotNull null
                row.toVodMediaItem(parsed)
            }
            .toList()
    }

    suspend fun recentSeriesByChat(chatId: Long, limit: Int = 60, offset: Int = 0): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!selectedChatsSeries().contains(chatId)) return@withContext emptyList()
        val box = store.boxFor(ObxTelegramMessage::class.java)
        val q = box.query(
            ObxTelegramMessage_.chatId.equal(chatId)
                .and(ObxTelegramMessage_.captionLower.notNull())
        ).orderDesc(ObxTelegramMessage_.date).build()
        val rows = q.find(offset.toLong(), limit.toLong())
        rows.asSequence()
            .mapNotNull { row ->
                val isVideo = (row.mimeType?.lowercase()?.startsWith("video/") == true) || (row.containerExt() != null)
                if (!isVideo) return@mapNotNull null
                val parsed = TelegramHeuristics.parse(row.caption)
                if (!parsed.isSeries || parsed.seriesTitle.isNullOrBlank() || parsed.season == null || parsed.episode == null)
                    return@mapNotNull null
                row.toSeriesItem(parsed)
            }
            .toList()
    }

    suspend fun searchAllChats(query: String, limit: Int = 120): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!settings.tgEnabled.first()) return@withContext emptyList()
        val chats = (selectedChatsVod() + selectedChatsSeries()).distinct()
        if (chats.isEmpty()) return@withContext emptyList()
        val box = store.boxFor(ObxTelegramMessage::class.java)
        val needle = query.trim().lowercase()
        val acc = mutableListOf<MediaItem>()
        for (chat in chats) {
            val q = box.query(
                ObxTelegramMessage_.chatId.equal(chat)
                    .and(ObxTelegramMessage_.captionLower.contains(needle))
            ).orderDesc(ObxTelegramMessage_.date).build()
            val rows = q.find(0, 60).toList()
            rows.asSequence().forEach { row ->
                val isVideo = (row.mimeType?.lowercase()?.startsWith("video/") == true) || (row.containerExt() != null)
                if (!isVideo) return@forEach
                val parsed = TelegramHeuristics.parse(row.caption)
                if (parsed.isSeries && parsed.seriesTitle != null && parsed.season != null && parsed.episode != null) {
                    row.toSeriesItem(parsed)?.let { acc += it }
                } else {
                    row.toVodMediaItem(parsed)?.let { acc += it }
                }
            }
            if (acc.size >= limit) break
        }
        acc.distinctBy { Triple(it.source, it.tgChatId, it.tgMessageId) }.take(limit)
    }

    private fun ObxTelegramMessage.toVodMediaItem(parsed: TelegramHeuristics.ParseResult): MediaItem? {
        val title = (parsed.title ?: this.caption ?: "Telegram ${this.messageId}").trim()
        val posterUri = posterUri()
        return MediaItem(
            id = telegramVodId(chatId, messageId),
            type = "vod",
            name = title.ifBlank { "Telegram ${messageId}" },
            sortTitle = title.lowercase(Locale.getDefault()),
            poster = posterUri,
            images = posterUri?.let { listOf(it) } ?: emptyList(),
            source = "TG",
            url = telegramUri(),
            tgChatId = this.chatId,
            tgMessageId = this.messageId,
            tgFileId = this.fileId,
            durationSecs = this.durationSecs,
            plot = this.caption,
            containerExt = containerExt(),
            providerKey = "telegram_chat_${this.chatId}",
            genreKey = parsed.language
        )
    }

    private fun ObxTelegramMessage.toSeriesItem(parsed: TelegramHeuristics.ParseResult): MediaItem? {
        val season = parsed.season ?: return null
        val episode = parsed.episode ?: return null
        val baseTitle = parsed.seriesTitle ?: return null
        val episodeLabel = buildString {
            append(baseTitle.trim())
            append(' ')
            append('S')
            append(season.toString().padStart(2, '0'))
            append('E')
            append(episode.toString().padStart(2, '0'))
        }
        val posterUri = posterUri()
        return MediaItem(
            id = telegramSeriesId(chatId, messageId),
            type = "series",
            name = episodeLabel,
            sortTitle = episodeLabel.lowercase(Locale.getDefault()),
            poster = posterUri,
            images = posterUri?.let { listOf(it) } ?: emptyList(),
            source = "TG",
            url = telegramUri(),
            tgChatId = this.chatId,
            tgMessageId = this.messageId,
            tgFileId = this.fileId,
            durationSecs = this.durationSecs,
            plot = this.caption,
            containerExt = containerExt(),
            providerKey = "telegram_chat_${this.chatId}",
            genreKey = parsed.language
        )
    }

    private fun ObxTelegramMessage.posterUri(): String? {
        // Prefer downloaded thumbnail path if available
        val thumb = this.thumbLocalPath
        if (!thumb.isNullOrBlank()) {
            val f = File(thumb)
            if (f.exists()) return f.toURI().toString()
        }
        // Best-effort: if a thumbnail file id exists but no local path yet, try to resolve quickly via TDLib
        val thumbId = this.thumbFileId
        if ((thumbId ?: 0) > 0 && com.chris.m3usuite.telegram.TdLibReflection.available()) {
            runCatching {
                val authFlow = kotlinx.coroutines.flow.MutableStateFlow(com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNKNOWN)
                val client = com.chris.m3usuite.telegram.TdLibReflection.getOrCreateClient(context, authFlow)
                if (client != null) {
                    val auth = com.chris.m3usuite.telegram.TdLibReflection.mapAuthorizationState(
                        com.chris.m3usuite.telegram.TdLibReflection.buildGetAuthorizationState()
                            ?.let { com.chris.m3usuite.telegram.TdLibReflection.sendForResult(client, it, 500) }
                    )
                    if (auth == com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED) {
                        // Nudge download and poll briefly for local path
                        com.chris.m3usuite.telegram.TdLibReflection.buildDownloadFile(thumbId!!, 8, 0, 0, false)
                            ?.let { com.chris.m3usuite.telegram.TdLibReflection.sendForResult(client, it, 100) }
                        var attempts = 0
                        var path: String? = null
                        while (attempts < 15 && (path.isNullOrBlank() || !File(path!!).exists())) {
                            val get = com.chris.m3usuite.telegram.TdLibReflection.buildGetFile(thumbId)
                            val res = if (get != null) com.chris.m3usuite.telegram.TdLibReflection.sendForResult(client, get, 250) else null
                            val info = res?.let { com.chris.m3usuite.telegram.TdLibReflection.extractFileInfo(it) }
                            path = info?.localPath
                            if (!path.isNullOrBlank() && File(path!!).exists()) {
                                // Persist for future queries
                                val obx = ObxStore.get(context)
                                val b = obx.boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)
                                val row = b.query(
                                    com.chris.m3usuite.data.obx.ObxTelegramMessage_.chatId.equal(this.chatId)
                                        .and(com.chris.m3usuite.data.obx.ObxTelegramMessage_.messageId.equal(this.messageId))
                                ).build().findFirst() ?: this
                                row.thumbLocalPath = path
                                b.put(row)
                                return File(path!!).toURI().toString()
                            }
                            Thread.sleep(100)
                            attempts++
                        }
                    }
                }
            }
        }
        // Fallback to media local path if it is an image (rare) or if the image loader can handle it
        val media = this.localPath
        if (!media.isNullOrBlank()) {
            val f = File(media)
            if (f.exists()) return f.toURI().toString()
        }
        return null
    }

    private fun ObxTelegramMessage.containerExt(): String? {
        val mt = this.mimeType?.lowercase(Locale.getDefault()) ?: return null
        return when {
            mt.contains("mp4") -> "mp4"
            mt.contains("matroska") || mt.contains("mkv") -> "mkv"
            mt.contains("webm") -> "webm"
            mt.contains("quicktime") || mt.contains("mov") -> "mov"
            mt.contains("avi") -> "avi"
            mt.contains("mp2t") || mt.contains("mpeg2-ts") || mt.contains("ts") -> "ts"
            else -> null
        }
    }

    private fun ObxTelegramMessage.telegramUri(): String = "tg://message?chatId=${this.chatId}&messageId=${this.messageId}"

    private fun telegramVodId(chatId: Long, messageId: Long): Long {
        val combined = ((chatId and 0x1FFFFFL) shl 32) or (messageId and 0xFFFFFFFFL)
        return 2_000_000_000_000L + (combined and 0xFFFFFFFFFFFFL)
    }

    private fun telegramSeriesId(chatId: Long, messageId: Long): Long {
        val combined = ((chatId and 0x1FFFFFL) shl 32) or (messageId and 0xFFFFFFFFL)
        return 3_000_000_000_000L + (combined and 0xFFFFFFFFFFFFL)
    }
}
