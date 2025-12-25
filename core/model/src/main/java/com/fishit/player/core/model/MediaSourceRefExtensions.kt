package com.fishit.player.core.model

import com.fishit.player.core.model.ids.PipelineItemId

/**
 * Extension functions for creating MediaSourceRef from various pipeline items.
 *
 * Each pipeline should use these helpers to create consistent source references when linking items
 * to canonical media.
 */

/**
 * Create a MediaSourceRef for a Telegram media item.
 *
 * @param chatId Telegram chat ID
 * @param messageId Telegram message ID
 * @param chatName Human-readable chat name
 * @param filename Original filename for quality/language detection
 * @param sizeBytes File size in bytes
 */
fun createTelegramSourceRef(
    chatId: Long,
    messageId: Long,
    chatName: String,
    filename: String?,
    sizeBytes: Long? = null,
    width: Int? = null,
    height: Int? = null,
    priority: Int = 0,
): MediaSourceRef {
    val sourceId = "telegram:$chatId:$messageId"

    return MediaSourceRef(
        sourceType = SourceType.TELEGRAM,
        sourceId = PipelineItemId(sourceId),
        sourceLabel = "Telegram: $chatName",
        quality =
            filename?.let { MediaQuality.fromFilename(it) }
                ?: MediaQuality.fromDimensions(width, height),
        languages = filename?.let { LanguageInfo.fromFilename(it) },
        format = filename?.let { MediaFormat.fromFilename(it) },
        sizeBytes = sizeBytes,
        priority = priority,
    )
}

/**
 * Create a MediaSourceRef for an Xtream VOD item.
 *
 * @param vodId Xtream VOD stream ID
 * @param providerName Xtream provider name
 * @param containerExt Container extension (e.g., "mkv", "mp4")
 */
fun createXtreamVodSourceRef(
    vodId: Int,
    providerName: String,
    title: String? = null,
    containerExt: String? = null,
    sizeBytes: Long? = null,
    priority: Int = 0,
): MediaSourceRef {
    val sourceId = "xtream:vod:$vodId"

    return MediaSourceRef(
        sourceType = SourceType.XTREAM,
        sourceId = PipelineItemId(sourceId),
        sourceLabel = "Xtream: $providerName",
        quality = title?.let { MediaQuality.fromFilename(it) },
        languages = title?.let { LanguageInfo.fromFilename(it) },
        format = containerExt?.let { MediaFormat(container = it) },
        sizeBytes = sizeBytes,
        priority = priority,
    )
}

/**
 * Create a MediaSourceRef for an Xtream Series Episode.
 *
 * @param seriesId Xtream series ID
 * @param season Season number
 * @param episode Episode number
 * @param providerName Xtream provider name
 */
fun createXtreamEpisodeSourceRef(
    seriesId: Int,
    season: Int,
    episode: Int,
    providerName: String,
    title: String? = null,
    containerExt: String? = null,
    priority: Int = 0,
): MediaSourceRef {
    val sourceId = "xtream:series:$seriesId:S${season}E$episode"

    return MediaSourceRef(
        sourceType = SourceType.XTREAM,
        sourceId = PipelineItemId(sourceId),
        sourceLabel = "Xtream: $providerName",
        quality = title?.let { MediaQuality.fromFilename(it) },
        languages = title?.let { LanguageInfo.fromFilename(it) },
        format = containerExt?.let { MediaFormat(container = it) },
        priority = priority,
    )
}

/**
 * Create a MediaSourceRef for a local IO file.
 *
 * @param uri File URI
 * @param filename Original filename
 * @param sizeBytes File size in bytes
 */
fun createIoSourceRef(
    uri: String,
    filename: String,
    sizeBytes: Long? = null,
    priority: Int = 0,
): MediaSourceRef {
    val sourceId = "io:file:$uri"

    return MediaSourceRef(
        sourceType = SourceType.IO,
        sourceId = PipelineItemId(sourceId),
        sourceLabel = "Local: $filename",
        quality = MediaQuality.fromFilename(filename),
        languages = LanguageInfo.fromFilename(filename),
        format = MediaFormat.fromFilename(filename),
        sizeBytes = sizeBytes,
        priority = priority,
    )
}

/**
 * Create a MediaSourceRef for a Plex media item.
 *
 * @param ratingKey Plex rating key
 * @param serverName Plex server name
 * @param resolution Video resolution
 */
fun createPlexSourceRef(
    ratingKey: String,
    serverName: String,
    title: String? = null,
    resolution: Int? = null,
    codec: String? = null,
    audioCodec: String? = null,
    sizeBytes: Long? = null,
    priority: Int = 0,
): MediaSourceRef {
    val sourceId = "plex:$ratingKey"

    return MediaSourceRef(
        sourceType = SourceType.PLEX,
        sourceId = PipelineItemId(sourceId),
        sourceLabel = "Plex: $serverName",
        quality =
            MediaQuality(
                resolution = resolution,
                codec = codec,
            ),
        format = audioCodec?.let { MediaFormat(container = "plex", audioCodec = it) },
        sizeBytes = sizeBytes,
        priority = priority,
    )
}

/**
 * Create a MediaSourceRef for an audiobook item.
 *
 * @param bookId Book identifier
 * @param libraryName Library or source name
 * @param format Audio format (e.g., "mp3", "m4b")
 */
fun createAudiobookSourceRef(
    bookId: String,
    libraryName: String,
    format: String? = null,
    sizeBytes: Long? = null,
    priority: Int = 0,
): MediaSourceRef {
    val sourceId = "audiobook:$bookId"

    return MediaSourceRef(
        sourceType = SourceType.AUDIOBOOK,
        sourceId = PipelineItemId(sourceId),
        sourceLabel = "Audiobook: $libraryName",
        format = format?.let { MediaFormat(container = it) },
        sizeBytes = sizeBytes,
        priority = priority,
    )
}

/** Source ID parsing utilities. */
object SourceIdParser {
    /** Extract source type from a source ID. e.g., "telegram:123:456" → SourceType.TELEGRAM */
    fun extractSourceType(sourceId: String): SourceType? {
        val prefix = sourceId.substringBefore(":")
        return when (prefix.lowercase()) {
            "telegram", "tg" -> SourceType.TELEGRAM
            "xtream", "xc" -> SourceType.XTREAM
            "io", "file", "local" -> SourceType.IO
            "audiobook", "book" -> SourceType.AUDIOBOOK
            "plex" -> SourceType.PLEX
            else -> null
        }
    }

    /** Check if source ID is from Telegram. */
    fun isTelegram(sourceId: String): Boolean = sourceId.startsWith("telegram:") || sourceId.startsWith("tg:")

    /** Check if source ID is from Xtream. */
    fun isXtream(sourceId: String): Boolean = sourceId.startsWith("xtream:") || sourceId.startsWith("xc:")

    /** Check if source ID is from local IO. */
    fun isIo(sourceId: String): Boolean =
        sourceId.startsWith("io:") ||
            sourceId.startsWith("file:") ||
            sourceId.startsWith("local:")

    /**
     * Extract Telegram chat ID and message ID from source ID. e.g., "telegram:123:456" → Pair(123L,
     * 456L)
     */
    fun parseTelegramSourceId(sourceId: String): Pair<Long, Long>? {
        if (!isTelegram(sourceId)) return null
        val parts = sourceId.split(":")
        if (parts.size < 3) return null
        return try {
            Pair(parts[1].toLong(), parts[2].toLong())
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Extract Xtream VOD ID from source ID. e.g., "xtream:vod:789" → 789 */
    fun parseXtreamVodId(sourceId: String): Int? {
        if (!isXtream(sourceId)) return null
        if (!sourceId.contains(":vod:")) return null
        val parts = sourceId.split(":")
        if (parts.size < 3) return null
        return parts[2].toIntOrNull()
    }

    /**
     * Extract Xtream series info from source ID. e.g., "xtream:series:123:S01E05" → Triple(123, 1,
     * 5)
     */
    fun parseXtreamEpisodeId(sourceId: String): Triple<Int, Int, Int>? {
        if (!isXtream(sourceId)) return null
        if (!sourceId.contains(":series:")) return null

        val regex = Regex("""xtream:series:(\d+):S(\d+)E(\d+)""")
        val match = regex.find(sourceId) ?: return null

        return try {
            Triple(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
            )
        } catch (e: Exception) {
            null
        }
    }
}
