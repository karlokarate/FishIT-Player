package com.chris.m3usuite.player.internal.source

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import com.chris.m3usuite.core.logging.AppLog
import com.chris.m3usuite.core.playback.PlayUrlHelper
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramItem
import com.chris.m3usuite.data.obx.ObxTelegramItem_
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chris.m3usuite.model.MediaItem as AppMediaItem

/**
 * BUG 1 FIX: Enhanced ResolvedPlaybackSource with diagnostic fields.
 *
 * Now includes:
 * - isLiveFromUrl: URL-based live detection heuristic
 * - inferredExtension: File extension extracted from URL
 * - telegramDurationMs: Duration in milliseconds from Telegram URL (if available)
 * - telegramFileSizeBytes: File size in bytes from Telegram URL (if available)
 *
 * These fields enable better LIVE/VOD detection and debug visibility.
 */
data class ResolvedPlaybackSource(
    val uri: Uri,
    val mimeType: String,
    val appMediaItem: AppMediaItem?,
    val isTelegram: Boolean,
    /** BUG 1 FIX: True if URL patterns suggest this is a live stream */
    val isLiveFromUrl: Boolean = false,
    /** BUG 1 FIX: File extension inferred from URL path */
    val inferredExtension: String? = null,
    /** Duration in milliseconds from Telegram URL query parameters (if available) */
    val telegramDurationMs: Long? = null,
    /** File size in bytes from Telegram URL query parameters (if available) */
    val telegramFileSizeBytes: Long? = null,
)

/**
 * Kapselt:
 * - Mime-Erkennung
 * - Telegram-OBX-Fallback (wenn kein MediaItem mitkommt)
 * - Xtream-Mime-Fallback via PlayUrlHelper
 * - BUG 1 FIX: URL-based LIVE detection heuristics
 */
class PlaybackSourceResolver(
    private val context: Context,
) {
    suspend fun resolve(
        url: String,
        explicitMimeType: String?,
        preparedMediaItem: AppMediaItem?,
    ): ResolvedPlaybackSource {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: Uri.parse(url)
        val isTelegram = url.startsWith("tg://", ignoreCase = true)

        // BUG 1 FIX: Extract extension from URL
        val inferredExtension = inferExtensionFromUrl(url)

        // BUG 1 FIX: Detect if URL patterns suggest live streaming
        val isLiveFromUrl = isLikelyLiveUrl(url)

        // Extract Telegram metadata from URL if available
        val telegramDurationMs =
            if (isTelegram) {
                parsed.getQueryParameter("durationMs")?.toLongOrNull()
            } else {
                null
            }
        val telegramFileSizeBytes =
            if (isTelegram) {
                parsed.getQueryParameter("fileSizeBytes")?.toLongOrNull()
            } else {
                null
            }

        val (mime, item) =
            when {
                explicitMimeType != null -> explicitMimeType to preparedMediaItem
                preparedMediaItem != null -> {
                    // Prefer explicit mime on the media item (e.g., HLS/DASH), fallback to ext heuristics
                    val inferred =
                        PlayUrlHelper.guessMimeType(preparedMediaItem.url, preparedMediaItem.containerExt)
                            ?: inferMimeTypeFromFileName(preparedMediaItem.url)
                    (inferred ?: MimeTypes.VIDEO_MP4) to preparedMediaItem
                }
                isTelegram -> {
                    val mime = resolveTelegramMimeFromObx(parsed) ?: MimeTypes.VIDEO_MP4
                    mime to null
                }
                else -> {
                    val guessed = PlayUrlHelper.guessMimeType(url, null)
                    (guessed ?: MimeTypes.APPLICATION_M3U8) to null
                }
            }

        val resolved =
            ResolvedPlaybackSource(
                uri = parsed,
                mimeType = mime,
                appMediaItem = item,
                isTelegram = isTelegram,
                isLiveFromUrl = isLiveFromUrl,
                inferredExtension = inferredExtension,
                telegramDurationMs = telegramDurationMs,
                telegramFileSizeBytes = telegramFileSizeBytes,
            )
        AppLog.log(
            category = "player",
            level = AppLog.Level.DEBUG,
            message = "resolved mime=$mime telegram=$isTelegram live=$isLiveFromUrl ext=$inferredExtension dur=$telegramDurationMs size=$telegramFileSizeBytes",
            extras =
                buildMap {
                    put("url", url)
                    explicitMimeType?.let { put("explicit", it) }
                    preparedMediaItem?.containerExt?.let { put("containerExt", it) }
                    put("isLiveFromUrl", isLiveFromUrl.toString())
                    telegramDurationMs?.let { put("telegramDurationMs", it.toString()) }
                    telegramFileSizeBytes?.let { put("telegramFileSizeBytes", it.toString()) }
                },
        )
        return resolved
    }

    /**
     * BUG 1 FIX: Detects if a URL is likely a live stream based on URL patterns.
     *
     * Patterns checked:
     * - `/live/` path segment (Xtream live streams)
     * - `.ts` extension (MPEG-TS, commonly used for live)
     * - `stream_type=live` query parameter
     * - `/live.m3u8` or similar live HLS patterns
     *
     * Note: This is a heuristic and may not be 100% accurate. The explicit PlaybackType
     * passed via navigation parameters always takes precedence.
     */
    private fun isLikelyLiveUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("/live/") ||
            lowerUrl.contains("/live.m3u8") ||
            lowerUrl.contains("stream_type=live") ||
            lowerUrl.contains("/streaming/") ||
            (
                lowerUrl.endsWith(".ts") &&
                    !lowerUrl.contains("/movie/") &&
                    !lowerUrl.contains("/series/") &&
                    !lowerUrl.contains("/vod/") &&
                    !lowerUrl.contains("/recordings/") &&
                    !lowerUrl.contains("/archive/")
            )
    }

    /**
     * BUG 1 FIX: Extracts file extension from URL path.
     *
     * Examples:
     * - "http://server/video.mp4" → "mp4"
     * - "http://server/live/stream.m3u8" → "m3u8"
     * - "http://server/live/123" → null
     */
    private fun inferExtensionFromUrl(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            val path = uri.path ?: return null
            val lastDot = path.lastIndexOf('.')
            if (lastDot >= 0 && lastDot < path.length - 1) {
                val ext = path.substring(lastDot + 1).lowercase()
                // Only return valid extensions.
                // Limit to 1-5 characters to exclude query parameters or malformed extensions (e.g., ".mp4?token=abc").
                // Most common media file extensions are 5 characters or fewer.
                if (ext.matches(Regex("^[a-z0-9]{1,5}$"))) ext else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Leichtgewichtiger OBX-Lookup für Telegram-MIME.
     * Versucht zuerst ObxTelegramItem (neue Tabelle), dann ObxTelegramMessage (legacy).
     */
    private suspend fun resolveTelegramMimeFromObx(uri: Uri): String? =
        withContext(Dispatchers.IO) {
            val messageId =
                uri
                    .getQueryParameter("messageId")
                    ?.toLongOrNull()
            val chatId =
                uri
                    .getQueryParameter("chatId")
                    ?.toLongOrNull()

            // Phase D: Try ObxTelegramItem first (new parser pipeline)
            if (chatId != null && messageId != null) {
                val mimeFromNewTable = resolveMimeFromTelegramItem(chatId, messageId)
                if (mimeFromNewTable != null) return@withContext mimeFromNewTable
            }

            // Legacy fallback: Try ObxTelegramMessage
            val fileId =
                uri
                    .getQueryParameter("fileId")
                    ?.toIntOrNull()
            if (fileId == null && messageId == null) return@withContext null

            val store = ObxStore.get(context)
            val box = store.boxFor(ObxTelegramMessage::class.java)
            val queryBuilder = box.query()
            if (fileId != null) {
                queryBuilder.equal(ObxTelegramMessage_.fileId, fileId.toLong())
            } else if (messageId != null) {
                queryBuilder.equal(ObxTelegramMessage_.messageId, messageId)
                if (chatId != null) {
                    queryBuilder.equal(ObxTelegramMessage_.chatId, chatId)
                }
            }

            val query = queryBuilder.build()
            try {
                val msg =
                    query.findFirst()
                        ?: return@withContext null

                msg.mimeType
                    ?: inferMimeTypeFromFileName(msg.fileName)
            } finally {
                query.close()
            }
        }

    /**
     * Resolve MIME type from ObxTelegramItem (new Phase D table).
     */
    private fun resolveMimeFromTelegramItem(
        chatId: Long,
        anchorMessageId: Long,
    ): String? {
        val store = ObxStore.get(context)
        val box = store.boxFor(ObxTelegramItem::class.java)
        val query =
            box
                .query()
                .equal(ObxTelegramItem_.chatId, chatId)
                .equal(ObxTelegramItem_.anchorMessageId, anchorMessageId)
                .build()
        try {
            val item = query.findFirst() ?: return null
            // Use video MIME type if available
            return item.videoMimeType
                ?: item.documentMimeType
                ?: inferMimeTypeFromFileName(item.documentFileName)
        } finally {
            query.close()
        }
    }
}

/**
 * Helper: mimet aus Dateiendung – identisch wie im alten InternalPlayerScreen.
 */
fun inferMimeTypeFromFileName(fileName: String?): String? =
    when {
        fileName == null -> null
        fileName.endsWith(".mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
        fileName.endsWith(".mkv", ignoreCase = true) -> MimeTypes.VIDEO_MATROSKA
        fileName.endsWith(".webm", ignoreCase = true) -> MimeTypes.VIDEO_WEBM
        fileName.endsWith(".avi", ignoreCase = true) -> MimeTypes.VIDEO_AVI
        fileName.endsWith(".mov", ignoreCase = true) -> MimeTypes.VIDEO_MP4 // QuickTime, MP4-based
        else -> null
    }
