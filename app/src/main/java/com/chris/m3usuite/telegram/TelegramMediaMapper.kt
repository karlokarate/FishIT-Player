package com.chris.m3usuite.telegram

import android.content.Context
import android.os.Looper
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Shared helpers to map Telegram ObjectBox rows into media-facing data.
 */
internal fun ObxTelegramMessage.posterUri(context: Context): String? {
    val existing = existingPosterPath()
    if (Looper.myLooper() == Looper.getMainLooper()) {
        return existing
    }
    if (existing != null) return existing
    val resolved = runBlocking {
        withContext(Dispatchers.IO) {
            downloadThumbIfNeeded(context)
        }
    }
    if (!resolved.isNullOrBlank()) return resolved
    return existingPosterPath()
}

private fun ObxTelegramMessage.existingPosterPath(): String? {
    val thumb = thumbLocalPath
    if (!thumb.isNullOrBlank()) {
        val file = File(thumb)
        if (file.exists()) return file.toURI().toString()
    }
    val media = localPath
    if (!media.isNullOrBlank()) {
        val file = File(media)
        if (file.exists()) return file.toURI().toString()
    }
    return null
}

private suspend fun ObxTelegramMessage.downloadThumbIfNeeded(context: Context): String? {
    val thumbId = thumbFileId ?: return null
    if (thumbId <= 0 || !TdLibReflection.available()) return null
    return runCatching {
        val authFlow = MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
        val client = TdLibReflection.getOrCreateClient(context, authFlow) ?: return@runCatching null
        val authState = TdLibReflection.buildGetAuthorizationState()?.let {
            TdLibReflection.sendForResult(client, it, timeoutMs = 500, retries = 1, traceTag = "MediaMapper:Auth")
        }
        val mapped = TdLibReflection.mapAuthorizationState(authState)
        if (mapped != TdLibReflection.AuthState.AUTHENTICATED) return@runCatching null

        TdLibReflection.buildDownloadFile(thumbId, 8, 0, 0, false)?.let {
            TdLibReflection.sendForResult(client, it, timeoutMs = 200, retries = 1, traceTag = "MediaMapper:DownloadThumb[$thumbId]")
        }

        repeat(10) {
            val get = TdLibReflection.buildGetFile(thumbId)
            val res = get?.let {
                TdLibReflection.sendForResult(client, it, timeoutMs = 250, retries = 1, traceTag = "MediaMapper:GetThumb[$thumbId]")
            }
            val path = res?.let { TdLibReflection.extractFileInfo(it) }?.localPath
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists()) {
                    val obx = ObxStore.get(context)
                    val box = obx.boxFor(ObxTelegramMessage::class.java)
                    val row = box.query(
                        ObxTelegramMessage_.chatId.equal(this.chatId)
                            .and(ObxTelegramMessage_.messageId.equal(this.messageId))
                    ).build().findFirst() ?: this
                    row.thumbLocalPath = path
                    box.put(row)
                    return@runCatching file.toURI().toString()
                }
            }
            delay(120)
        }
        null
    }.getOrNull()
}

internal fun ObxTelegramMessage.containerExt(): String? {
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

internal fun ObxTelegramMessage.telegramUri(): String =
    "tg://message?chatId=${this.chatId}&messageId=${this.messageId}"
