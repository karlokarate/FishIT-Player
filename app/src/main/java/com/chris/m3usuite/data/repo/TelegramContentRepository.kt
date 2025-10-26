package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.TelegramHeuristics
import com.chris.m3usuite.telegram.containerExt
import com.chris.m3usuite.telegram.posterUri
import com.chris.m3usuite.telegram.telegramUri
import com.chris.m3usuite.tg.TgGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale

class TelegramContentRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    private val store get() = ObxStore.get(context)

    private fun isVideoLike(row: ObxTelegramMessage): Boolean {
        val mime = row.mimeType?.lowercase(Locale.getDefault()).orEmpty()
        val containerExt = row.containerExt()
        if (mime.startsWith("video/")) return true
        if (mime == "application/x-mpegurl" || mime == "application/vnd.apple.mpegurl") return true
        if (mime == "application/octet-stream" && containerExt != null) return true
        if (containerExt != null) return true
        val fileName = row.fileName?.lowercase(Locale.getDefault()).orEmpty()
        val videoExtensions = listOf(
            ".mp4", ".mkv", ".avi", ".ts", ".mov", ".webm", ".m4v", ".mpg", ".mpeg", ".wmv", ".flv", ".ogv", ".3gp"
        )
        if (videoExtensions.any { fileName.endsWith(it) }) return true
        val duration = row.durationSecs ?: 0
        return row.supportsStreaming == true && duration > 0
    }

    private suspend fun selectedChatIds(): List<Long> = settings.tgSelectedChatsCsv.first()
        .split(',')
        .mapNotNull { it.trim().toLongOrNull() }
        .distinct()

    suspend fun selectedChatsVod(): List<Long> {
        if (TgGate.mirrorOnly()) return emptyList()
        return selectedChatIds()
    }

    suspend fun selectedChatsSeries(): List<Long> {
        if (TgGate.mirrorOnly()) return emptyList()
        return selectedChatIds()
    }

    suspend fun recentVodByChat(chatId: Long, limit: Int = 60, offset: Int = 0): List<MediaItem> = withContext(Dispatchers.IO) {
        if (TgGate.mirrorOnly()) return@withContext emptyList()
        if (!selectedChatsVod().contains(chatId)) return@withContext emptyList()
        val box = store.boxFor(ObxTelegramMessage::class.java)
        val q = box.query(
            ObxTelegramMessage_.chatId.equal(chatId)
                .and(ObxTelegramMessage_.captionLower.notNull())
        ).orderDesc(ObxTelegramMessage_.date).build()
        val rows = q.find(offset.toLong(), limit.toLong())
        rows.asSequence()
            .mapNotNull { row ->
                if (!isVideoLike(row)) return@mapNotNull null
                val parsed = TelegramHeuristics.parse(row.caption)
                if (parsed.isSeries && (parsed.season != null || parsed.episode != null)) return@mapNotNull null
                row.toVodMediaItem(parsed)
            }
            .toList()
    }

    suspend fun recentSeriesByChat(chatId: Long, limit: Int = 60, offset: Int = 0): List<MediaItem> = withContext(Dispatchers.IO) {
        if (TgGate.mirrorOnly()) return@withContext emptyList()
        if (!selectedChatsSeries().contains(chatId)) return@withContext emptyList()
        val box = store.boxFor(ObxTelegramMessage::class.java)
        val q = box.query(
            ObxTelegramMessage_.chatId.equal(chatId)
                .and(ObxTelegramMessage_.captionLower.notNull())
        ).orderDesc(ObxTelegramMessage_.date).build()
        val rows = q.find(offset.toLong(), limit.toLong())
        rows.asSequence()
            .mapNotNull { row ->
                if (!isVideoLike(row)) return@mapNotNull null
                val parsed = TelegramHeuristics.parse(row.caption)
                if (!parsed.isSeries || parsed.seriesTitle.isNullOrBlank() || parsed.season == null || parsed.episode == null)
                    return@mapNotNull null
                row.toSeriesItem(parsed)
            }
            .toList()
    }

    suspend fun searchAllChats(query: String, limit: Int = 120): List<MediaItem> = withContext(Dispatchers.IO) {
        if (TgGate.mirrorOnly()) return@withContext emptyList()
        if (!settings.tgEnabled.first()) return@withContext emptyList()
        val chats = selectedChatIds()
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
                if (!isVideoLike(row)) return@forEach
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
        val rawTitle = parsed.title ?: this.caption ?: "Telegram ${this.messageId}"
        val title = TelegramHeuristics.cleanMovieTitle(rawTitle).ifBlank { "Telegram ${this.messageId}" }
        val posterUri = posterUri(context)
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
            genreKey = parsed.language,
            year = parsed.year,
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
        val posterUri = posterUri(context)
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
            genreKey = parsed.language,
            year = parsed.year,
        )
    }
    private fun telegramVodId(chatId: Long, messageId: Long): Long {
        val combined = ((chatId and 0x1FFFFFL) shl 32) or (messageId and 0xFFFFFFFFL)
        return 2_000_000_000_000L + (combined and 0xFFFFFFFFFFFFL)
    }

    private fun telegramSeriesId(chatId: Long, messageId: Long): Long {
        val combined = ((chatId and 0x1FFFFFL) shl 32) or (messageId and 0xFFFFFFFFL)
        return 3_000_000_000_000L + (combined and 0xFFFFFFFFFFFFL)
    }
}
