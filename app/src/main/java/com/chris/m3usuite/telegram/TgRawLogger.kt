package com.chris.m3usuite.telegram

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Lightweight logger for raw TDLib payloads.
 * The GitHub workflow stores these logs in Logcat; callers can provide structured prefixes
 * so pagination/debugging stays readable without crashing Logcat length limits.
 */
object TgRawLogger {
    private const val TAG = "TgRaw"
    private const val CHUNK = 3500
    private val overlayFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val overlayEvents: SharedFlow<String> = overlayFlow

    fun log(prefix: String, obj: Any?) {
        val body = runCatching { objString(obj) }.getOrElse { "<error:${it.message.orEmpty()}>" }
        val message = if (prefix.isBlank()) body else "$prefix\n$body"
        emitChunks(message)
    }

    private fun objString(obj: Any?): String = when (obj) {
        null -> "null"
        is String -> obj
        is ByteArray -> obj.joinToString(prefix = "[", postfix = "]") { it.toUByte().toString() }
        else -> obj.toString()
    }

    private fun emitChunks(message: String) {
        emitOverlayPreview(message)
        if (message.length <= CHUNK) {
            Log.v(TAG, message)
        } else {
            var index = 0
            var part = 1
            while (index < message.length) {
                val end = minOf(message.length, index + CHUNK)
                val chunk = message.substring(index, end)
                Log.v(TAG, "[$part] $chunk")
                index = end
                part++
            }
        }
    }

    private fun emitOverlayPreview(message: String) {
        val lines = message.lineSequence().take(2).toList()
        if (lines.isEmpty()) return
        val combined = lines.joinToString(separator = " ⏐ ")
        val preview = if (combined.length <= 220) combined else combined.take(217) + "…"
        overlayFlow.tryEmit(preview)
    }
}
