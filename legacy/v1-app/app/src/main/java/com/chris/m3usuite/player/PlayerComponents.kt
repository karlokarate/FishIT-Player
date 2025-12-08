package com.chris.m3usuite.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory

/**
 * Central helpers for configuring Media3 components so every player instance
 * consistently prefers the bundled FFmpeg renderers while keeping decoder
 * fallback enabled.
 */
@UnstableApi
object PlayerComponents {
    /**
     * Builds a [DefaultRenderersFactory] that prefers FFmpeg extension
     * renderers and keeps decoder fallback enabled. This matches the
     * recommendation in the Media3 1.8.0 release notes for wiring optional
     * codec modules.
     */
    fun renderersFactory(context: Context): DefaultRenderersFactory =
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
}
