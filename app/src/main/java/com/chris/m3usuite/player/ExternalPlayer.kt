package com.chris.m3usuite.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri

object ExternalPlayer {
    private const val VLC = "org.videolan.vlc"
    private const val MX_FREE = "com.mxtech.videoplayer.ad"
    private const val MX_PRO  = "com.mxtech.videoplayer.pro"
    private const val JUST    = "com.brouken.player"

    /**
     * @param startPositionMs optional Startposition in Millisekunden (best-effort an Player übergeben)
     */
    fun open(
        context: Context,
        url: String,
        headers: Map<String,String> = emptyMap(),
        preferredPkg: String? = null,
        startPositionMs: Long? = null
    ) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(url.toUri(), guessMime(url))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (headers.isNotEmpty()) {
            val b = Bundle()
            headers.forEach { (k,v) -> b.putString(k,v) }
            intent.putExtra("headers", b)
            intent.putExtra("android.media.intent.extra.HTTP_HEADERS", b)
        }

        // Best-effort Startposition für gängige Player
        startPositionMs?.let { pos ->
            intent.putExtra("android.media.intent.extra.START_PLAYBACK_POSITION_MILLIS", pos) // generisch
            intent.putExtra("position", pos)            // MX Player
            intent.putExtra("extra_position", pos)      // Just Player
            intent.putExtra("seek_position", pos)       // manche Player
        }

        // bevorzugter Player (Default VLC)
        intent.`package` = preferredPkg?.takeIf { it.isNotBlank() } ?: VLC

        runCatching { context.startActivity(intent) }.onFailure {
            intent.`package` = null
            context.startActivity(Intent.createChooser(intent, "Open with"))
        }
    }

    private fun guessMime(url: String): String = when {
        url.endsWith(".m3u8", true) -> "application/vnd.apple.mpegurl"
        url.endsWith(".mpd", true) -> "application/dash+xml"
        url.endsWith(".mp4", true) -> "video/mp4"
        url.endsWith(".mkv", true) -> "video/x-matroska"
        url.endsWith(".ts", true)  -> "video/MP2T"
        else -> "video/*"
    }
}
