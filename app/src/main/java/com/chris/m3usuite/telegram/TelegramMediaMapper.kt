package com.chris.m3usuite.telegram

import android.content.Context
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import java.io.File
import java.util.Locale

/**
 * Shared helpers to map Telegram ObjectBox rows into media-facing data.
 */
internal fun ObxTelegramMessage.posterUri(context: Context): String? {
    // Prefer downloaded thumbnail path if available
    val thumb = this.thumbLocalPath
    if (!thumb.isNullOrBlank()) {
        val f = File(thumb)
        if (f.exists()) return f.toURI().toString()
    }
    // Best-effort: if a thumbnail file id exists but no local path yet, try to resolve quickly via TDLib
    val thumbId = this.thumbFileId
    if ((thumbId ?: 0) > 0 && TdLibReflection.available()) {
        runCatching {
            val authFlow = kotlinx.coroutines.flow.MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
            val client = TdLibReflection.getOrCreateClient(context, authFlow)
            if (client != null) {
                val auth = TdLibReflection.mapAuthorizationState(
                    TdLibReflection.buildGetAuthorizationState()
                        ?.let { TdLibReflection.sendForResult(client, it, 500) }
                )
                if (auth == TdLibReflection.AuthState.AUTHENTICATED) {
                    // Nudge download and poll briefly for local path
                    TdLibReflection.buildDownloadFile(thumbId!!, 8, 0, 0, false)
                        ?.let { TdLibReflection.sendForResult(client, it, 100) }
                    var attempts = 0
                    var path: String? = null
                    while (attempts < 15 && (path.isNullOrBlank() || !File(path!!).exists())) {
                        val get = TdLibReflection.buildGetFile(thumbId)
                        val res = if (get != null) TdLibReflection.sendForResult(client, get, 250) else null
                        val info = res?.let { TdLibReflection.extractFileInfo(it) }
                        path = info?.localPath
                        if (!path.isNullOrBlank() && File(path!!).exists()) {
                            // Persist for future queries
                            val obx = ObxStore.get(context)
                            val b = obx.boxFor(ObxTelegramMessage::class.java)
                            val row = b.query(
                                ObxTelegramMessage_.chatId.equal(this.chatId)
                                    .and(ObxTelegramMessage_.messageId.equal(this.messageId))
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
