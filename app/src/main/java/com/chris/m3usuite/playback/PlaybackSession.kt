package com.chris.m3usuite.playback

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.atomic.AtomicReference

/**
 * Global ExoPlayer holder so the TV mini player can reattach to the active session.
 * Keeps the latest player instance and remembers which URL was configured on it.
 */
object PlaybackSession {
    data class Holder(val player: ExoPlayer, val isNew: Boolean)

    private val playerRef = AtomicReference<ExoPlayer?>()
    private val sourceRef = AtomicReference<String?>(null)

    fun acquire(context: Context, builder: () -> ExoPlayer): Holder {
        var created = false
        var current = playerRef.get()
        if (current == null) {
            synchronized(this) {
                current = playerRef.get()
                if (current == null) {
                    current = builder()
                    playerRef.set(current)
                    created = true
                }
            }
        }
        return Holder(current!!, created)
    }

    fun current(): ExoPlayer? = playerRef.get()

    fun set(player: ExoPlayer?) {
        val previous = playerRef.getAndSet(player)
        if (previous != null && previous !== player) {
            runCatching { previous.release() }
        }
        if (player == null) {
            sourceRef.set(null)
        }
    }

    fun currentSource(): String? = sourceRef.get()

    fun setSource(url: String?) {
        sourceRef.set(url)
    }
}
