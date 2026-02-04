/**
 * Unit tests for VariantBuilder.
 *
 * Verifies variant key/container extraction and defaulting logic.
 */
package com.fishit.player.infra.data.nx.writer.builder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VariantBuilderTest {
    
    private val builder = VariantBuilder()
    
    @Test
    fun `build() extracts container from xtream containerExtension hint`() {
        val playbackHints = mapOf(
            "xtream.containerExtension" to "mkv"
        )
        
        val variant = builder.build(
            variantKey = "xtream:user@server:vod:12345#default",
            workKey = "movie:test",
            sourceKey = "xtream:user@server:vod:12345",
            playbackHints = playbackHints,
            durationMs = 7200000L
        )
        
        assertEquals("mkv", variant.container)
    }
    
    @Test
    fun `build() extracts container from containerExtension hint`() {
        val playbackHints = mapOf(
            "containerExtension" to "mp4"
        )
        
        val variant = builder.build(
            variantKey = "xtream:user@server:vod:12345#default",
            workKey = "movie:test",
            sourceKey = "xtream:user@server:vod:12345",
            playbackHints = playbackHints,
            durationMs = 7200000L
        )
        
        assertEquals("mp4", variant.container)
    }
    
    @Test
    fun `build() extracts container from extension hint`() {
        val playbackHints = mapOf(
            "extension" to "avi"
        )
        
        val variant = builder.build(
            variantKey = "xtream:user@server:vod:12345#default",
            workKey = "movie:test",
            sourceKey = "xtream:user@server:vod:12345",
            playbackHints = playbackHints,
            durationMs = 7200000L
        )
        
        assertEquals("avi", variant.container)
    }
    
    @Test
    fun `build() normalizes m3u8 to hls`() {
        val playbackHints = mapOf(
            "containerExtension" to "m3u8"
        )
        
        val variant = builder.build(
            variantKey = "xtream:user@server:vod:12345#default",
            workKey = "movie:test",
            sourceKey = "xtream:user@server:vod:12345",
            playbackHints = playbackHints,
            durationMs = 7200000L
        )
        
        assertEquals("hls", variant.container)
    }
    
    @Test
    fun `build() normalizes m3u to hls`() {
        val playbackHints = mapOf(
            "containerExtension" to "m3u"
        )
        
        val variant = builder.build(
            variantKey = "xtream:user@server:vod:12345#default",
            workKey = "movie:test",
            sourceKey = "xtream:user@server:vod:12345",
            playbackHints = playbackHints,
            durationMs = 7200000L
        )
        
        assertEquals("hls", variant.container)
    }
    
    @Test
    fun `build() uses empty container when no hints`() {
        val variant = builder.build(
            variantKey = "xtream:user@server:vod:12345#default",
            workKey = "movie:test",
            sourceKey = "xtream:user@server:vod:12345",
            playbackHints = emptyMap(),
            durationMs = 7200000L
        )
        
        assertEquals("", variant.container)
    }
    
    @Test
    fun `build() sets label to Original as default`() {
        val variant = builder.build(
            variantKey = "xtream:user@server:vod:12345#default",
            workKey = "movie:test",
            sourceKey = "xtream:user@server:vod:12345",
            playbackHints = emptyMap(),
            durationMs = 7200000L
        )
        
        assertEquals("Original", variant.label)
    }
    
    @Test
    fun `build() sets isDefault to true`() {
        val variant = builder.build(
            variantKey = "xtream:user@server:vod:12345#default",
            workKey = "movie:test",
            sourceKey = "xtream:user@server:vod:12345",
            playbackHints = emptyMap(),
            durationMs = 7200000L
        )
        
        assertEquals(true, variant.isDefault)
    }
    
    @Test
    fun `build() sets durationMs from parameter`() {
        val variant = builder.build(
            variantKey = "xtream:user@server:vod:12345#default",
            workKey = "movie:test",
            sourceKey = "xtream:user@server:vod:12345",
            playbackHints = emptyMap(),
            durationMs = 7200000L
        )
        
        assertEquals(7200000L, variant.durationMs)
    }
    
    @Test
    fun `build() sets createdAtMs and updatedAtMs to current time`() {
        val beforeBuild = System.currentTimeMillis()
        val variant = builder.build(
            variantKey = "xtream:user@server:vod:12345#default",
            workKey = "movie:test",
            sourceKey = "xtream:user@server:vod:12345",
            playbackHints = emptyMap(),
            durationMs = 7200000L
        )
        val afterBuild = System.currentTimeMillis()
        
        assertTrue(variant.createdAtMs in beforeBuild..afterBuild)
        assertTrue(variant.updatedAtMs in beforeBuild..afterBuild)
    }
    
    @Test
    fun `build() prioritizes xtream containerExtension over generic`() {
        val playbackHints = mapOf(
            "xtream.containerExtension" to "mkv",
            "containerExtension" to "mp4"
        )
        
        val variant = builder.build(
            variantKey = "xtream:user@server:vod:12345#default",
            workKey = "movie:test",
            sourceKey = "xtream:user@server:vod:12345",
            playbackHints = playbackHints,
            durationMs = 7200000L
        )
        
        assertEquals("mkv", variant.container)
    }
}
