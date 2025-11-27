package com.chris.m3usuite.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri

object ExternalPlayer {
    private const val VLC = "org.videolan.vlc"
    private const val MX_FREE = "com.mxtech.videoplayer.ad"
    private const val MX_PRO = "com.mxtech.videoplayer.pro"
    private const val JUST = "com.brouken.player"

    /**
     * @param startPositionMs optional Startposition in Millisekunden (best-effort an Player übergeben)
     */
    fun open(
        context: Context,
        url: String,
        headers: Map<String, String> = emptyMap(),
        preferredPkg: String? = null,
        startPositionMs: Long? = null,
    ) {
        val uri = url.toUri()
        com.chris.m3usuite.core.logging.AppLog.log(
            category = "player",
            level = com.chris.m3usuite.core.logging.AppLog.Level.DEBUG,
            message = "open external pkg=${preferredPkg ?: "<chooser>"} url=${uri.scheme}://${uri.host}${uri.path?.let {
                if (it.length > 24) {
                    it
                        .takeLast(
                            24,
                        )
                } else {
                    it
                }
            } ?: ""}",
        )
        // Build candidate intents (typed + generic) for robustness with different players
        val typed =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, guessMime(url))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val generic =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val dataOnly =
            Intent(Intent.ACTION_VIEW).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        fun putHeaders(i: Intent) {
            val b = Bundle()
            headers.forEach { (k, v) -> b.putString(k, v) }
            // Standard Android/most players: Bundle under the standard key
            i.putExtra("android.media.intent.extra.HTTP_HEADERS", b)
            // VLC expects String[] of "Key: Value" under the "headers" key
            if (headers.isNotEmpty()) {
                val asArray = headers.entries.map { (k, v) -> "$k: $v" }.toTypedArray()
                i.putExtra("headers", asArray)
            }
        }
        if (headers.isNotEmpty()) {
            putHeaders(typed)
            putHeaders(generic)
            putHeaders(dataOnly)
        }

        // Best-effort Startposition für gängige Player
        startPositionMs?.let { pos ->
            fun putStart(i: Intent) {
                i.putExtra("android.media.intent.extra.START_PLAYBACK_POSITION_MILLIS", pos) // generisch
                i.putExtra("position", pos) // MX Player
                i.putExtra("extra_position", pos) // Just Player
                i.putExtra("seek_position", pos) // manche Player
            }
            listOf(typed, generic, dataOnly).forEach(::putStart)
        }

        // bevorzugter Player: nur setzen, wenn gewünscht; sonst System-Chooser anzeigen
        val pkg = preferredPkg?.takeIf { it.isNotBlank() }
        if (pkg != null) {
            // Try typed → generic → dataOnly for the target package
            for (i in listOf(typed, generic, dataOnly)) {
                i.`package` = pkg
                val r = runCatching { context.startActivity(i) }
                if (r.isSuccess) return
            }
            // Fallback: system chooser without package
            context.startActivity(Intent.createChooser(typed, "Mit öffnen"))
        } else {
            // Ohne bevorzugtes Paket: immer den System-Chooser anzeigen (typed Intent)
            context.startActivity(Intent.createChooser(typed, "Mit öffnen"))
        }
    }

    private fun guessMime(url: String): String =
        when {
            url.endsWith(".m3u8", true) -> "application/vnd.apple.mpegurl"
            url.endsWith(".mpd", true) -> "application/dash+xml"
            url.endsWith(".mp4", true) -> "video/mp4"
            url.endsWith(".mkv", true) -> "video/x-matroska"
            url.endsWith(".ts", true) -> "video/MP2T"
            else -> "video/*"
        }
}
