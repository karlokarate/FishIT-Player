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
                if (row.supportsStreaming == false) return@mapNotNull null
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
                if (row.supportsStreaming == false) return@mapNotNull null
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
                if (row.supportsStreaming == false) return@forEach
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
        val path = this.localPath
        if (path.isNullOrBlank()) return null
        val file = File(path)
        return if (file.exists()) file.toURI().toString() else null
    }

    private fun ObxTelegramMessage.containerExt(): String? {
        val mt = this.mimeType?.lowercase(Locale.getDefault()) ?: return null
        return when {
            mt.contains("mp4") -> "mp4"
            mt.contains("matroska") || mt.contains("mkv") -> "mkv"
            mt.contains("quicktime") -> "mov"
            mt.contains("avi") -> "avi"
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
