package com.fishit.player.nextlib

import android.content.Context
import androidx.media3.exoplayer.RenderersFactory
import com.fishit.player.infra.logging.UnifiedLog
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * Interface for configuring codec support in the internal player.
 *
 * This abstraction allows the player layer to use FFmpeg-based decoders
 * without directly depending on NextLib implementation details.
 */
interface NextlibCodecConfigurator {
    /**
     * Creates a [RenderersFactory] with extended codec support.
     *
     * @param context Application or activity context.
     * @return A configured RenderersFactory for use with ExoPlayer.Builder.
     */
    fun createRenderersFactory(context: Context): RenderersFactory
}

/**
 * Default implementation using NextLib's FFmpeg-based renderers.
 *
 * Provides software decoding for:
 * - **Audio:** Vorbis, Opus, FLAC, ALAC, MP3, AAC, AC3, EAC3, DTS, TrueHD
 * - **Video:** H.264, HEVC, VP8, VP9
 *
 * NextLib is GPL-3.0 licensed due to FFmpeg.
 */
class DefaultNextlibCodecConfigurator : NextlibCodecConfigurator {
    
    companion object {
        private const val TAG = "NextlibCodecs"
    }
    
    override fun createRenderersFactory(context: Context): RenderersFactory {
        UnifiedLog.i(TAG, "Creating NextLib FFmpeg renderers factory")
        
        val factory = NextRenderersFactory(context)
        
        UnifiedLog.d(TAG,
            "NextRenderersFactory configured with FFmpeg decoders for: " +
            "Audio(Vorbis, Opus, FLAC, ALAC, MP3, AAC, AC3, EAC3, DTS, TrueHD), " +
            "Video(H.264, HEVC, VP8, VP9)"
        )
        
        return factory
    }
}
