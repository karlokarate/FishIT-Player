/**
 * Unit tests for SourceRefBuilder.
 *
 * Verifies source item kind mapping, clean item key extraction, and live/EPG field handling
 * per NX_SSOT_CONTRACT invariants.
 */
package com.fishit.player.infra.data.nx.writer.builder

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SourceRefBuilderTest {
    
    private val builder = SourceRefBuilder()
    
    @Test
    fun `build() extracts numeric item key from xtream VOD source key`() {
        val raw = RawMediaMetadata(
            originalTitle = "Test Movie",
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream Source",
            sourceId = "xtream:vod:12345",
            mediaType = MediaType.MOVIE,
        )
        
        val sourceRef = builder.build(
            raw = raw,
            workKey = "movie:test",
            accountKey = "user@server",
            sourceKey = "xtream:user@server:vod:12345"
        )
        
        assertEquals("12345", sourceRef.sourceItemKey)
        assertEquals("user@server", sourceRef.accountKey)
        assertEquals(NxWorkSourceRefRepository.SourceType.XTREAM, sourceRef.sourceType)
    }
    
    @Test
    fun `build() extracts numeric item key from xtream live source key`() {
        val raw = RawMediaMetadata(
            originalTitle = "Test Channel",
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream Source",
            sourceId = "xtream:live:67890",
            mediaType = MediaType.LIVE,
        )
        
        val sourceRef = builder.build(
            raw = raw,
            workKey = "live:test",
            accountKey = "user@server",
            sourceKey = "xtream:user@server:live:67890"
        )
        
        assertEquals("67890", sourceRef.sourceItemKey)
    }
    
    @Test
    fun `build() maps source item kind for VOD`() {
        val raw = RawMediaMetadata(
            originalTitle = "Test Movie",
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream Source",
            sourceId = "xtream:vod:12345",
            mediaType = MediaType.MOVIE,
        )
        
        val sourceRef = builder.build(
            raw = raw,
            workKey = "movie:test",
            accountKey = "user@server",
            sourceKey = "xtream:user@server:vod:12345"
        )
        
        assertEquals(NxWorkSourceRefRepository.SourceItemKind.VOD, sourceRef.sourceItemKind)
    }
    
    @Test
    fun `build() maps source item kind for live`() {
        val raw = RawMediaMetadata(
            originalTitle = "Test Channel",
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream Source",
            sourceId = "xtream:live:67890",
            mediaType = MediaType.LIVE,
        )
        
        val sourceRef = builder.build(
            raw = raw,
            workKey = "live:test",
            accountKey = "user@server",
            sourceKey = "xtream:user@server:live:67890"
        )
        
        assertEquals(NxWorkSourceRefRepository.SourceItemKind.LIVE, sourceRef.sourceItemKind)
    }
    
    @Test
    fun `build() maps source item kind for series`() {
        val raw = RawMediaMetadata(
            originalTitle = "Test Series",
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream Source",
            sourceId = "xtream:series:11111",
            mediaType = MediaType.SERIES,
        )
        
        val sourceRef = builder.build(
            raw = raw,
            workKey = "series:test",
            accountKey = "user@server",
            sourceKey = "xtream:user@server:series:11111"
        )
        
        assertEquals(NxWorkSourceRefRepository.SourceItemKind.SERIES, sourceRef.sourceItemKind)
    }
    
    @Test
    fun `build() includes live EPG fields when present`() {
        val raw = RawMediaMetadata(
            originalTitle = "Test Channel",
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream Source",
            sourceId = "xtream:live:67890",
            mediaType = MediaType.LIVE,
            epgChannelId = "bbc-one-hd",
            tvArchive = 1,
            tvArchiveDuration = 7,
        )
        
        val sourceRef = builder.build(
            raw = raw,
            workKey = "live:test",
            accountKey = "user@server",
            sourceKey = "xtream:user@server:live:67890"
        )
        
        assertEquals("bbc-one-hd", sourceRef.epgChannelId)
        assertEquals(1, sourceRef.tvArchive)
        assertEquals(7, sourceRef.tvArchiveDuration)
    }
    
    @Test
    fun `build() omits EPG fields when not present`() {
        val raw = RawMediaMetadata(
            originalTitle = "Test Channel",
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream Source",
            sourceId = "xtream:live:67890",
            mediaType = MediaType.LIVE,
        )
        
        val sourceRef = builder.build(
            raw = raw,
            workKey = "live:test",
            accountKey = "user@server",
            sourceKey = "xtream:user@server:live:67890"
        )
        
        assertEquals(null, sourceRef.epgChannelId)
        assertEquals(0, sourceRef.tvArchive)
        assertEquals(0, sourceRef.tvArchiveDuration)
    }
    
    @Test
    fun `build() extracts clean item key from telegram source`() {
        val raw = RawMediaMetadata(
            originalTitle = "Test Video",
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram Source",
            sourceId = "msg:123456:789",
            mediaType = MediaType.CLIP,
        )
        
        val sourceRef = builder.build(
            raw = raw,
            workKey = "clip:test",
            accountKey = "telegram:123456",
            sourceKey = "telegram:123456:msg:123456:789"
        )
        
        // Telegram uses composite key, not extracted
        assertEquals("msg:123456:789", sourceRef.sourceItemKey)
    }
    
    @Test
    fun `build() populates timestamps correctly`() {
        val now = System.currentTimeMillis()
        val raw = RawMediaMetadata(
            originalTitle = "Test Movie",
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream Source",
            sourceId = "xtream:vod:12345",
            mediaType = MediaType.MOVIE,
            lastModifiedTimestamp = 1234567890000L,
        )
        
        val sourceRef = builder.build(
            raw = raw,
            workKey = "movie:test",
            accountKey = "user@server",
            sourceKey = "xtream:user@server:vod:12345",
            now = now
        )
        
        assertEquals(now, sourceRef.firstSeenAtMs)
        assertEquals(now, sourceRef.lastSeenAtMs)
        assertEquals(1234567890000L, sourceRef.sourceLastModifiedMs)
    }
}
