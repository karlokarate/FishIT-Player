package com.chris.m3usuite.player.internal.source

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import com.chris.m3usuite.core.playback.PlayUrlHelper
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.model.MediaItem as AppMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ResolvedPlaybackSource(
    val uri: Uri,
    val mimeType: String,
    val appMediaItem: AppMediaItem?,
    val isTelegram: Boolean,
)

/**
 * Kapselt:
 * - Mime-Erkennung
 * - Telegram-OBX-Fallback (wenn kein MediaItem mitkommt)
 * - Xtream-Mime-Fallback via PlayUrlHelper
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

        val (mime, item) =
            when {
                explicitMimeType != null -> explicitMimeType to preparedMediaItem
                preparedMediaItem != null -> {
                    val inferred = inferMimeTypeFromFileName(preparedMediaItem.url)
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

        return ResolvedPlaybackSource(
            uri = parsed,
            mimeType = mime,
            appMediaItem = item,
            isTelegram = isTelegram,
        )
    }

    /**
     * Leichtgewichtiger OBX-Lookup für Telegram-MIME.
     * Verwendet nur messageId + fileName, kein schweres Repo.
     */
    private suspend fun resolveTelegramMimeFromObx(uri: Uri): String? =
        withContext(Dispatchers.IO) {
            val messageId =
                uri.getQueryParameter("messageId")
                    ?.toLongOrNull()
                    ?: return@withContext null

            val store = ObxStore.get(context)
            val box = store.boxFor(ObxTelegramMessage::class.java)
            val msg =
                box
                    .query()
                    .equal(ObxTelegramMessage_.messageId, messageId)
                    .build()
                    .findFirst()
                    ?: return@withContext null

            inferMimeTypeFromFileName(msg.fileName)
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