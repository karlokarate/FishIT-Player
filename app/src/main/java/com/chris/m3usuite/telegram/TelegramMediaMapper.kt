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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Shared helpers to map Telegram ObjectBox rows into media-facing data.
 */
internal fun ObxTelegramMessage.posterUri(context: Context): String? {
    fun existingLocalPath(): String? {
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

    existingLocalPath()?.let { return it }
    if (Looper.myLooper() == Looper.getMainLooper()) {
        return null
    }

    return runBlocking(Dispatchers.IO) {
        resolvePosterPath(context) ?: existingLocalPath()
    }
}

private suspend fun ObxTelegramMessage.resolvePosterPath(context: Context): String? = withContext(Dispatchers.IO) {
    val thumbId = thumbFileId ?: return@withContext null
    if (thumbId <= 0 || !TdLibReflection.available()) return@withContext null

    runCatching {
        val authFlow = kotlinx.coroutines.flow.MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
        val client = TdLibReflection.getOrCreateClient(context, authFlow) ?: return@runCatching null
        val auth = TdLibReflection.mapAuthorizationState(
            TdLibReflection.buildGetAuthorizationState()?.let {
                TdLibReflection.sendForResult(
                    client,
                    it,
                    timeoutMs = 500,
                    retries = 1,
                    traceTag = "MediaMapper:Auth"
                )
            }
        )
        if (auth != TdLibReflection.AuthState.AUTHENTICATED) return@runCatching null

        TdLibReflection.buildDownloadFile(thumbId, 8, 0, 0, false)?.let {
            TdLibReflection.sendForResult(
                client,
                it,
                timeoutMs = 200,
                retries = 1,
                traceTag = "MediaMapper:DownloadThumb[$thumbId]"
            )
        }

        repeat(15) {
            val get = TdLibReflection.buildGetFile(thumbId) ?: return@repeat
            val res = TdLibReflection.sendForResult(
                client,
                get,
                timeoutMs = 300,
                retries = 1,
                traceTag = "MediaMapper:GetThumb[$thumbId]"
            )
            val info = res?.let { TdLibReflection.extractFileInfo(it) }
            val path = info?.localPath
            if (!path.isNullOrBlank() && File(path).exists()) {
                val uri = File(path).toURI().toString()
                val obx = ObxStore.get(context)
                val box = obx.boxFor(ObxTelegramMessage::class.java)
                val row = box.query(
                    ObxTelegramMessage_.chatId.equal(chatId)
                        .and(ObxTelegramMessage_.messageId.equal(messageId))
                ).build().findFirst() ?: this
                row.thumbLocalPath = path
                this.thumbLocalPath = path
                box.put(row)
                return@runCatching uri
            }
            delay(100)
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
