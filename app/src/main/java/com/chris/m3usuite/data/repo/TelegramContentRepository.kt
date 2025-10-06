package com.chris.m3usuite.data.repo

import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

/**
 * Repository that maps Telegram-indexed ObjectBox messages into MediaItem rows for UI.
 *
 * Notes:
 * - We intentionally keep ObjectBox access dynamic (reflection), so this file does not hard-depend
 *   on the ObxTelegramMessage class package name. This allows the entity to live under different
 *   packages without breaking this repository.
 * - Ordering is newest-first by (date desc, messageId desc).
 * - Items are partitioned into VOD vs Series using simple SxxEyy heuristics; this keeps the two
 *   sections disjoint.
 * - Media IDs are bridged into a Telegram domain using a large offset to avoid collision with
 *   existing live/vod/series ranges.
 */
class TelegramContentRepository(
    private val boxStore: BoxStore
) {

    companion object {
        // 5e12 reserved for Telegram-sourced items
        private const val TG_MEDIA_ID_OFFSET: Long = 5_000_000_000_000L
        private const val SOURCE_TELEGRAM: String = "TG"
    }

    private enum class Kind { VOD, SERIES }

    suspend fun recentVodByChat(
        chatId: Long,
        limit: Int = 60,
        offset: Int = 0
    ): List<com.chris.m3usuite.model.MediaItem> = recentByChatInternal(chatId, Kind.VOD, limit, offset)

    suspend fun recentSeriesByChat(
        chatId: Long,
        limit: Int = 60,
        offset: Int = 0
    ): List<com.chris.m3usuite.model.MediaItem> = recentByChatInternal(chatId, Kind.SERIES, limit, offset)

    private suspend fun recentByChatInternal(
        chatId: Long,
        kind: Kind,
        limit: Int,
        offset: Int
    ): List<com.chris.m3usuite.model.MediaItem> = withContext(Dispatchers.IO) {
        if (chatId == 0L || limit <= 0) return@withContext emptyList()

        val box = telegramBox() ?: return@withContext emptyList()
        val all = try {
            box.getAll()
        } catch (_: Throwable) {
            emptyList()
        }

        val filtered = all.asSequence()
            .filter { msg -> optLong(msg, "chatId") == chatId }
            .filter { msg -> optBool(msg, "supportsStreaming") != false } // default true if missing
            .map { msg ->
                val messageId = optLong(msg, "messageId") ?: 0L
                val date = optLong(msg, "date") ?: 0L
                val caption = optString(msg, "caption")
                val localPath = optString(msg, "localPath")
                val fileId = optString(msg, "fileId")
                val thumbFileId = optString(msg, "thumbFileId")
                val durationSecs = optLong(msg, "durationSecs") ?: 0L
                val mimeType = optString(msg, "mimeType")

                val parseInput = when {
                    !caption.isNullOrBlank() -> caption
                    !localPath.isNullOrBlank() -> extractFileName(localPath)
                    !mimeType.isNullOrBlank() -> mimeType
                    else -> ""
                }

                val parsed = quickParse(parseInput)
                val isSeries = parsed.season != null && parsed.episode != null

                Mapped(
                    entityId = optLong(msg, "id") ?: 0L,
                    chatId = chatId,
                    messageId = messageId,
                    date = date,
                    parsed = parsed,
                    rawCaption = caption,
                    durationSecs = durationSecs,
                    fileId = fileId,
                    thumbFileId = thumbFileId,
                    isSeries = isSeries
                )
            }
            .filter { mapped ->
                when (kind) {
                    Kind.VOD -> !mapped.isSeries
                    Kind.SERIES -> mapped.isSeries
                }
            }
            .sortedWith(
                compareByDescending<Mapped> { safeDate(it.date) }
                    .thenByDescending { it.messageId }
            )
            .drop(max(0, offset))
            .take(limit)
            .map { mapped ->
                val nameParts = buildNameParts(mapped.parsed, kind)
                val id = if (mapped.entityId != 0L) {
                    TG_MEDIA_ID_OFFSET + mapped.entityId
                } else {
                    // Fallback: derive stable-ish id from chat+message
                    TG_MEDIA_ID_OFFSET + (mapped.chatId xor (mapped.messageId shl 1))
                }

                com.chris.m3usuite.model.MediaItem(
                    id = id,
                    type = when (kind) {
                        Kind.VOD -> "vod"
                        Kind.SERIES -> "series"
                    },
                    name = nameParts.display,
                    sortTitle = nameParts.sortKey,
                    year = mapped.parsed.year,
                    durationSecs = mapped.durationSecs,
                    plot = mapped.rawCaption,
                    source = SOURCE_TELEGRAM,
                    tgChatId = mapped.chatId,
                    tgMessageId = mapped.messageId,
                    tgFileId = mapped.fileId
                )
            }
            .toList()

        filtered
    }

    // region ObjectBox dynamic wiring

    @Suppress("UNCHECKED_CAST")
    private fun telegramBox(): Box<Any>? {
        val entityClass: Class<Any> = resolveTelegramEntityClass() ?: return null
        return try {
            boxStore.boxFor(entityClass)
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveTelegramEntityClass(): Class<Any>? {
        val candidates = listOf(
            "com.chris.m3usuite.telegram.db.ObxTelegramMessage",
            "com.chris.m3usuite.telegram.ObxTelegramMessage",
            "com.chris.m3usuite.data.obx.ObxTelegramMessage",
            "com.chris.m3usuite.data.db.ObxTelegramMessage"
        )
        for (fqcn in candidates) {
            try {
                val cls = Class.forName(fqcn) as Class<Any>
                return cls
            } catch (_: ClassNotFoundException) {
                // try next
            }
        }
        return null
    }

    // endregion

    // region Reflection helpers

    private fun optLong(target: Any, name: String): Long? {
        // Try getter method
        try {
            val m = target.javaClass.methods.firstOrNull { it.name.equals("get${name.capitalize()}", ignoreCase = true) && it.parameterCount == 0 }
            if (m != null) {
                val v = m.invoke(target)
                if (v is Number) return v.toLong()
            }
        } catch (_: Throwable) {
        }
        // Try direct field
        try {
            val f = target.javaClass.getDeclaredField(name)
            f.isAccessible = true
            val v = f.get(target)
            if (v is Number) return v.toLong()
        } catch (_: Throwable) {
        }
        return null
    }

    private fun optBool(target: Any, name: String): Boolean? {
        // Try isX / getX methods
        try {
            val isM = target.javaClass.methods.firstOrNull { it.name.equals("is${name.capitalize()}", ignoreCase = true) && it.parameterCount == 0 }
            if (isM != null) {
                val v = isM.invoke(target)
                if (v is Boolean) return v
            }
            val getM = target.javaClass.methods.firstOrNull { it.name.equals("get${name.capitalize()}", ignoreCase = true) && it.parameterCount == 0 }
            if (getM != null) {
                val v = getM.invoke(target)
                if (v is Boolean) return v
            }
        } catch (_: Throwable) {
        }
        // Try direct field
        try {
            val f = target.javaClass.getDeclaredField(name)
            f.isAccessible = true
            val v = f.get(target)
            if (v is Boolean) return v
        } catch (_: Throwable) {
        }
        return null
    }

    private fun optString(target: Any, name: String): String? {
        // Try getter method
        try {
            val m = target.javaClass.methods.firstOrNull { it.name.equals("get${name.capitalize()}", ignoreCase = true) && it.parameterCount == 0 }
            if (m != null) {
                val v = m.invoke(target)
                if (v is String) return v
            }
        } catch (_: Throwable) {
        }
        // Try direct field
        try {
            val f = target.javaClass.getDeclaredField(name)
            f.isAccessible = true
            val v = f.get(target)
            if (v is String) return v
        } catch (_: Throwable) {
        }
        return null
    }

    // endregion

    // region Mapping helpers

    private data class Mapped(
        val entityId: Long,
        val chatId: Long,
        val messageId: Long,
        val date: Long,
        val parsed: Parsed,
        val rawCaption: String?,
        val durationSecs: Long,
        val fileId: String?,
        val thumbFileId: String?,
        val isSeries: Boolean
    )

    private data class NameParts(
        val display: String,
        val sortKey: String
    )

    private fun buildNameParts(parsed: Parsed, kind: Kind): NameParts {
        val base = parsed.title.ifBlank { "Unknown" }
        val se = if (kind == Kind.SERIES && parsed.season != null && parsed.episode != null) {
            String.format(Locale.US, " S%02dE%02d", parsed.season, parsed.episode)
        } else {
            ""
        }
        val y = parsed.year?.let { " ($it)" } ?: ""
        val display = base + se + y
        val sort = normalizeSortKey(base) + (parsed.year?.let { " $it" } ?: "")
        return NameParts(display = display.trim(), sortKey = sort)
    }

    private fun normalizeSortKey(s: String): String {
        val lowered = s.lowercase(Locale.getDefault())
        // Strip leading articles and punctuation commonly found in file names
        var t = lowered
            .replace('_', ' ')
            .replace('.', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        t = t.removePrefix("the ").removePrefix("a ").removePrefix("an ")
        return t.trim()
    }

    private fun extractFileName(path: String): String {
        val idx = max(path.lastIndexOf('/'), path.lastIndexOf('\\'))
        return if (idx >= 0 && idx + 1 < path.length) path.substring(idx + 1) else path
    }

    private fun safeDate(d: Long?): Long {
        // Expecting seconds; if it's in ms, reduce
        val v = d ?: 0L
        return when {
            v > 9_999_999_999L -> v / 1000L // looks like ms
            else -> v
        }
    }

    // endregion

    // region Minimal heuristics

    /**
     * Minimal, non-throwing parser to extract title/year and series SxxEyy from a caption or filename.
     * This is intentionally conservative; a dedicated TelegramHeuristics should supersede this.
     */
    private data class Parsed(
        val title: String,
        val season: Int?,
        val episode: Int?,
        val year: Int?
    )

    private fun quickParse(raw: String?): Parsed {
        if (raw.isNullOrBlank()) return Parsed(title = "", season = null, episode = null, year = null)

        // Normalize separators to spaces for tokenization
        val cleaned = raw
            .replace('_', ' ')
            .replace('.', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        // Detect SxxEyy or 1x02 patterns
        var season: Int? = null
        var episode: Int? = null
        Regex("""(?i)\bS(\d{1,2})[^\w]?E(\d{1,2})\b""").find(cleaned)?.let { m ->
            season = m.groupValues.getOrNull(1)?.toIntOrNull()
            episode = m.groupValues.getOrNull(2)?.toIntOrNull()
        } ?: run {
            Regex("""(?i)\b(\d{1,2})x(\d{1,2})\b""").find(cleaned)?.let { m ->
                season = m.groupValues.getOrNull(1)?.toIntOrNull()
                episode = m.groupValues.getOrNull(2)?.toIntOrNull()
            }
        }

        // Detect year (prefer four-digit 19xx/20xx near end or in parentheses/brackets)
        val year = Regex("""(?:(?:\(|\[)\s*(19|20)\d{2}\s*(?:\)|\]))|(?:\b(19|20)\d{2}\b)""")
            .findAll(cleaned)
            .lastOrNull()
            ?.value
            ?.filter(Char::isDigit)
            ?.toIntOrNull()

        // Remove common junk tokens like [1080p], [GERMAN], 720p, HDR, WEB-DL, etc., and the SxxEyy itself
        var titleCandidate = cleaned
            .replace(Regex("""(?i)\bS\d{1,2}[^\w]?E\d{1,2}\b"""), " ")
            .replace(Regex("""(?i)\b\d{1,2}x\d{1,2}\b"""), " ")
            .replace(Regex("""(?i)\b(4k|2160p|1080p|720p|480p|hdr|dv|x265|x264|hevc|aac|dts|ac3|bluray|webrip|web[-\s]?dl|bdrip|dvdrip|remux|hdtv|cam|ts|r5)\b"""), " ")
            .replace(Regex("""(?i)[\[\(].*?(4k|2160p|1080p|720p|480p|hdr|dv|x265|x264|hevc|aac|dts|ac3|bluray|webrip|web[-\s]?dl|bdrip|dvdrip|remux|hdtv|cam|ts|r5).*?[\]\)]"""), " ")
            .replace(Regex("""(?i)\b(ger|german|deutsch|en|eng|english|multi|dual)\b"""), " ")
            .replace(Regex("""(?i)\b(extended|uncut|proper|repack|internal|limited|complete|part\s?\d+)\b"""), " ")
            .replace(Regex("""(?i)\b(uhd|sd|hd|fullhd)\b"""), " ")

        // Remove bracketed/parenthesized groups that are mostly technical tags
        titleCandidate = titleCandidate
            .replace(Regex("""\[[^\]]*]"""), " ")
            .replace(Regex("""\([^\)]*\)"""), " ")

        // Collapse whitespace and trim
        titleCandidate = titleCandidate.replace(Regex("""\s+"""), " ").trim()

        // Strip trailing year if present from the title portion
        val title = titleCandidate
            .replace(Regex("""(?i)\b(19|20)\d{2}\b$"""), "")
            .trim()

        return Parsed(
            title = title,
            season = season,
            episode = episode,
            year = year
        )
    }

    // endregion
}

private fun String.capitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}