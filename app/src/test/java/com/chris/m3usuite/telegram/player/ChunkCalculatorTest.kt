package com.chris.m3usuite.telegram.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ChunkCalculator.
 *
 * Tests cover:
 * - Basic chunk calculation
 * - Edge cases (small videos, large videos, exact multiples)
 * - Position/offset lookups
 * - Cache size calculations
 */
class ChunkCalculatorTest {
    @Test
    fun `test basic chunk calculation - 120min 2GB video`() {
        // Arrange: 2-hour video, 2GB, 30-min chunks
        val durationMs = 120 * 60 * 1000L // 120 minutes
        val fileSizeBytes = 2_000_000_000L // 2GB
        val chunkDurationMs = 30 * 60 * 1000L // 30 minutes

        // Act
        val chunks = ChunkCalculator.calculateChunks(durationMs, fileSizeBytes, chunkDurationMs)

        // Assert: Should have 4 chunks
        assertEquals(4, chunks.size)

        // Verify first chunk
        val chunk0 = chunks[0]
        assertEquals(0, chunk0.index)
        assertEquals(0L, chunk0.startMs)
        assertEquals(30 * 60 * 1000L, chunk0.endMs)
        assertEquals(0L, chunk0.startByte)
        assertTrue("Chunk 0 size should be ~500MB", chunk0.sizeBytes in 499_000_000..501_000_000)

        // Verify last chunk
        val chunk3 = chunks[3]
        assertEquals(3, chunk3.index)
        assertEquals(90 * 60 * 1000L, chunk3.startMs)
        assertEquals(120 * 60 * 1000L, chunk3.endMs)
        assertEquals(fileSizeBytes, chunk3.endByte) // Should end exactly at file size

        // Verify chunk continuity (no gaps)
        for (i in 0 until chunks.size - 1) {
            assertEquals(
                "Chunk ${i + 1} should start where chunk $i ends",
                chunks[i].endMs,
                chunks[i + 1].startMs,
            )
            assertEquals(
                "Byte ranges should be continuous",
                chunks[i].endByte,
                chunks[i + 1].startByte,
            )
        }
    }

    @Test
    fun `test small video - single chunk`() {
        // Arrange: 10-minute video, 100MB
        val durationMs = 10 * 60 * 1000L
        val fileSizeBytes = 100 * 1024 * 1024L
        val chunkDurationMs = 30 * 60 * 1000L

        // Act
        val chunks = ChunkCalculator.calculateChunks(durationMs, fileSizeBytes, chunkDurationMs)

        // Assert: Should have only 1 chunk (video shorter than chunk duration)
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startMs)
        assertEquals(durationMs, chunks[0].endMs)
        assertEquals(0L, chunks[0].startByte)
        assertEquals(fileSizeBytes, chunks[0].endByte)
    }

    @Test
    fun `test exact multiple - 60min video with 30min chunks`() {
        // Arrange: Exactly 2 chunks
        val durationMs = 60 * 60 * 1000L // 60 minutes
        val fileSizeBytes = 1_000_000_000L // 1GB
        val chunkDurationMs = 30 * 60 * 1000L

        // Act
        val chunks = ChunkCalculator.calculateChunks(durationMs, fileSizeBytes, chunkDurationMs)

        // Assert
        assertEquals(2, chunks.size)
        assertEquals(0L, chunks[0].startMs)
        assertEquals(30 * 60 * 1000L, chunks[0].endMs)
        assertEquals(30 * 60 * 1000L, chunks[1].startMs)
        assertEquals(60 * 60 * 1000L, chunks[1].endMs)

        // Both chunks should be ~500MB
        assertTrue("Chunk 0 should be ~500MB", chunks[0].sizeBytes in 499_000_000..501_000_000)
        assertTrue("Chunk 1 should be ~500MB", chunks[1].sizeBytes in 499_000_000..501_000_000)
    }

    @Test
    fun `test large video - 3 hour 5GB`() {
        // Arrange
        val durationMs = 180 * 60 * 1000L // 3 hours
        val fileSizeBytes = 5_000_000_000L // 5GB
        val chunkDurationMs = 30 * 60 * 1000L

        // Act
        val chunks = ChunkCalculator.calculateChunks(durationMs, fileSizeBytes, chunkDurationMs)

        // Assert: Should have 6 chunks (180min / 30min)
        assertEquals(6, chunks.size)

        // Verify total coverage
        assertEquals(0L, chunks.first().startMs)
        assertEquals(durationMs, chunks.last().endMs)
        assertEquals(0L, chunks.first().startByte)
        assertEquals(fileSizeBytes, chunks.last().endByte)

        // Verify each chunk is ~30 minutes and ~833MB
        chunks.forEach { chunk ->
            assertTrue(
                "Chunk ${chunk.index} duration should be ~30min",
                chunk.durationMs in (29 * 60 * 1000)..(31 * 60 * 1000),
            )
            assertTrue(
                "Chunk ${chunk.index} size should be ~833MB",
                chunk.sizeBytes in 800_000_000..866_000_000,
            )
        }
    }

    @Test
    fun `test different chunk sizes - 15min chunks`() {
        // Arrange
        val durationMs = 60 * 60 * 1000L // 60 minutes
        val fileSizeBytes = 1_000_000_000L // 1GB
        val chunkDurationMs = 15 * 60 * 1000L // 15 minutes

        // Act
        val chunks = ChunkCalculator.calculateChunks(durationMs, fileSizeBytes, chunkDurationMs)

        // Assert: Should have 4 chunks (60min / 15min)
        assertEquals(4, chunks.size)

        // Each chunk should be ~15 minutes and ~250MB
        chunks.forEach { chunk ->
            assertEquals(15 * 60 * 1000L, chunk.durationMs)
            assertTrue("Chunk should be ~250MB", chunk.sizeBytes in 249_000_000..251_000_000)
        }
    }

    @Test
    fun `test getChunkIndexForPosition - middle of chunk`() {
        // Arrange
        val chunks =
            ChunkCalculator.calculateChunks(
                durationMs = 120 * 60 * 1000L,
                fileSizeBytes = 2_000_000_000L,
                chunkDurationMs = 30 * 60 * 1000L,
            )

        // Act & Assert
        // Position at 15 minutes (middle of chunk 0)
        assertEquals(0, ChunkCalculator.getChunkIndexForPosition(chunks, 15 * 60 * 1000L))

        // Position at 45 minutes (middle of chunk 1)
        assertEquals(1, ChunkCalculator.getChunkIndexForPosition(chunks, 45 * 60 * 1000L))

        // Position at 105 minutes (middle of chunk 3)
        assertEquals(3, ChunkCalculator.getChunkIndexForPosition(chunks, 105 * 60 * 1000L))
    }

    @Test
    fun `test getChunkIndexForPosition - chunk boundaries`() {
        // Arrange
        val chunks =
            ChunkCalculator.calculateChunks(
                durationMs = 120 * 60 * 1000L,
                fileSizeBytes = 2_000_000_000L,
                chunkDurationMs = 30 * 60 * 1000L,
            )

        // Act & Assert
        // Position at exactly 0 (start of chunk 0)
        assertEquals(0, ChunkCalculator.getChunkIndexForPosition(chunks, 0L))

        // Position at exactly 30 minutes (start of chunk 1)
        assertEquals(1, ChunkCalculator.getChunkIndexForPosition(chunks, 30 * 60 * 1000L))

        // Position at exactly 60 minutes (start of chunk 2)
        assertEquals(2, ChunkCalculator.getChunkIndexForPosition(chunks, 60 * 60 * 1000L))

        // Position at exactly 120 minutes (end of video, belongs to last chunk)
        assertEquals(3, ChunkCalculator.getChunkIndexForPosition(chunks, 120 * 60 * 1000L))
    }

    @Test
    fun `test getChunkIndexForPosition - edge cases`() {
        // Arrange
        val chunks =
            ChunkCalculator.calculateChunks(
                durationMs = 120 * 60 * 1000L,
                fileSizeBytes = 2_000_000_000L,
                chunkDurationMs = 30 * 60 * 1000L,
            )

        // Act & Assert
        // Negative position
        assertEquals(-1, ChunkCalculator.getChunkIndexForPosition(chunks, -1000L))

        // Position beyond video duration
        assertEquals(3, ChunkCalculator.getChunkIndexForPosition(chunks, 200 * 60 * 1000L))

        // Empty chunks list
        assertEquals(-1, ChunkCalculator.getChunkIndexForPosition(emptyList(), 1000L))
    }

    @Test
    fun `test getChunkIndexForByteOffset - basic`() {
        // Arrange
        val chunks =
            ChunkCalculator.calculateChunks(
                durationMs = 120 * 60 * 1000L,
                fileSizeBytes = 2_000_000_000L,
                chunkDurationMs = 30 * 60 * 1000L,
            )

        // Act & Assert
        // Byte 0 (start of chunk 0)
        assertEquals(0, ChunkCalculator.getChunkIndexForByteOffset(chunks, 0L))

        // Byte in middle of chunk 0
        assertEquals(0, ChunkCalculator.getChunkIndexForByteOffset(chunks, 250_000_000L))

        // Byte at start of chunk 1 (~500MB)
        assertEquals(1, ChunkCalculator.getChunkIndexForByteOffset(chunks, 500_000_000L))

        // Byte at end of file
        assertEquals(3, ChunkCalculator.getChunkIndexForByteOffset(chunks, 2_000_000_000L))
    }

    @Test
    fun `test calculateRequiredCacheSize - current chunk only`() {
        // Arrange
        val chunks =
            ChunkCalculator.calculateChunks(
                durationMs = 120 * 60 * 1000L,
                fileSizeBytes = 2_000_000_000L,
                chunkDurationMs = 30 * 60 * 1000L,
            )

        // Act: Current chunk 1, no preload, no keep-behind
        val cacheSize =
            ChunkCalculator.calculateRequiredCacheSize(
                chunks = chunks,
                currentChunkIndex = 1,
                preloadCount = 0,
                keepBehindCount = 0,
            )

        // Assert: Should be ~500MB (size of chunk 1)
        assertTrue("Cache should be ~500MB", cacheSize in 499_000_000..501_000_000)
    }

    @Test
    fun `test calculateRequiredCacheSize - with preload and keep-behind`() {
        // Arrange
        val chunks =
            ChunkCalculator.calculateChunks(
                durationMs = 120 * 60 * 1000L,
                fileSizeBytes = 2_000_000_000L,
                chunkDurationMs = 30 * 60 * 1000L,
            )

        // Act: Current chunk 1, preload 1, keep-behind 1
        // Should include chunks 0, 1, 2
        val cacheSize =
            ChunkCalculator.calculateRequiredCacheSize(
                chunks = chunks,
                currentChunkIndex = 1,
                preloadCount = 1,
                keepBehindCount = 1,
            )

        // Assert: Should be ~1.5GB (3 chunks × 500MB)
        assertTrue("Cache should be ~1.5GB", cacheSize in 1_490_000_000..1_510_000_000)
    }

    @Test
    fun `test calculateRequiredCacheSize - first chunk with preload`() {
        // Arrange
        val chunks =
            ChunkCalculator.calculateChunks(
                durationMs = 120 * 60 * 1000L,
                fileSizeBytes = 2_000_000_000L,
                chunkDurationMs = 30 * 60 * 1000L,
            )

        // Act: Current chunk 0, preload 1, keep-behind 1 (but no chunk before 0)
        // Should include chunks 0, 1
        val cacheSize =
            ChunkCalculator.calculateRequiredCacheSize(
                chunks = chunks,
                currentChunkIndex = 0,
                preloadCount = 1,
                keepBehindCount = 1,
            )

        // Assert: Should be ~1GB (2 chunks × 500MB)
        assertTrue("Cache should be ~1GB", cacheSize in 999_000_000..1_001_000_000)
    }

    @Test
    fun `test calculateRequiredCacheSize - last chunk`() {
        // Arrange
        val chunks =
            ChunkCalculator.calculateChunks(
                durationMs = 120 * 60 * 1000L,
                fileSizeBytes = 2_000_000_000L,
                chunkDurationMs = 30 * 60 * 1000L,
            )

        // Act: Current chunk 3 (last), preload 1 (but no chunk after 3), keep-behind 1
        // Should include chunks 2, 3
        val cacheSize =
            ChunkCalculator.calculateRequiredCacheSize(
                chunks = chunks,
                currentChunkIndex = 3,
                preloadCount = 1,
                keepBehindCount = 1,
            )

        // Assert: Should be ~1GB (2 chunks × 500MB)
        assertTrue("Cache should be ~1GB", cacheSize in 999_000_000..1_001_000_000)
    }

    @Test
    fun `test ChunkInfo toString formatting`() {
        // Arrange
        val chunk =
            ChunkCalculator.ChunkInfo(
                index = 0,
                startMs = 0L,
                endMs = 30 * 60 * 1000L,
                startByte = 0L,
                endByte = 500_000_000L,
                sizeBytes = 500_000_000L,
            )

        // Act
        val str = chunk.toString()
        val durationStr = chunk.durationString
        val sizeStr = chunk.sizeString

        // Assert
        assertEquals("30:00", durationStr)
        assertEquals("476.8 MB", sizeStr)
        assertTrue("toString should contain chunk info", str.contains("Chunk #0"))
        assertTrue("toString should contain duration", str.contains("30:00"))
        assertTrue("toString should contain size", str.contains("476.8 MB"))
    }

    @Test
    fun `test invalid input - zero duration`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChunkCalculator.calculateChunks(
                durationMs = 0L,
                fileSizeBytes = 1_000_000_000L,
                chunkDurationMs = 30 * 60 * 1000L,
            )
        }
    }

    @Test
    fun `test invalid input - negative file size`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChunkCalculator.calculateChunks(
                durationMs = 120 * 60 * 1000L,
                fileSizeBytes = -1L,
                chunkDurationMs = 30 * 60 * 1000L,
            )
        }
    }

    @Test
    fun `test invalid input - zero chunk duration`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChunkCalculator.calculateChunks(
                durationMs = 120 * 60 * 1000L,
                fileSizeBytes = 1_000_000_000L,
                chunkDurationMs = 0L,
            )
        }
    }

    @Test
    fun `test realistic scenario - 90min movie 1-5GB`() {
        // Arrange: Typical high-quality movie
        val durationMs = 90 * 60 * 1000L // 90 minutes
        val fileSizeBytes = 1_500_000_000L // 1.5GB
        val chunkDurationMs = 30 * 60 * 1000L

        // Act
        val chunks = ChunkCalculator.calculateChunks(durationMs, fileSizeBytes, chunkDurationMs)

        // Assert
        assertEquals(3, chunks.size) // 3 chunks of 30min each

        // Typical playback scenario: start at beginning
        val startChunkIndex = ChunkCalculator.getChunkIndexForPosition(chunks, 0L)
        assertEquals(0, startChunkIndex)

        // Calculate cache needed for start (current + 1 preload)
        val startCacheSize =
            ChunkCalculator.calculateRequiredCacheSize(
                chunks = chunks,
                currentChunkIndex = startChunkIndex,
                preloadCount = 1,
                keepBehindCount = 0,
            )
        // Should be ~1GB (2 chunks)
        assertTrue("Start cache should be ~1GB", startCacheSize in 999_000_000..1_001_000_000)

        // Mid-movie: position at 45 minutes
        val midChunkIndex = ChunkCalculator.getChunkIndexForPosition(chunks, 45 * 60 * 1000L)
        assertEquals(1, midChunkIndex)

        // Calculate cache for mid-movie (keep 1 behind, current, preload 1)
        val midCacheSize =
            ChunkCalculator.calculateRequiredCacheSize(
                chunks = chunks,
                currentChunkIndex = midChunkIndex,
                preloadCount = 1,
                keepBehindCount = 1,
            )
        // Should be ~1.5GB (all 3 chunks)
        assertEquals(fileSizeBytes, midCacheSize)
    }
}
