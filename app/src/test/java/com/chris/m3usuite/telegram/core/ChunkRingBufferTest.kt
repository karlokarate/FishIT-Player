package com.chris.m3usuite.telegram.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ChunkRingBuffer.
 * Tests the chunk-based ringbuffer implementation for streaming cache.
 */
class ChunkRingBufferTest {
    @Test
    fun `write and read within single chunk`() {
        // Arrange: 4KB chunks, max 4 chunks
        val buffer = ChunkRingBuffer(chunkSize = 4096, maxChunks = 4)
        val data = ByteArray(1024) { it.toByte() }
        val result = ByteArray(1024)

        // Act: Write 1KB at position 0
        val written = buffer.write(position = 0L, src = data, srcOffset = 0, length = 1024)

        // Assert: Should write all 1024 bytes
        assertEquals(1024, written)

        // Act: Read 1KB from position 0
        val read = buffer.read(position = 0L, dest = result, destOffset = 0, length = 1024)

        // Assert: Should read all 1024 bytes and match original data
        assertEquals(1024, read)
        assertTrue(data.contentEquals(result))
    }

    @Test
    fun `write and read across chunk boundaries`() {
        // Arrange: 1KB chunks, max 10 chunks
        val buffer = ChunkRingBuffer(chunkSize = 1024, maxChunks = 10)
        val data = ByteArray(3000) { (it % 256).toByte() }
        val result = ByteArray(3000)

        // Act: Write 3KB across multiple chunks starting at position 512
        // This will span: chunk 0 (512 bytes), chunk 1 (1024 bytes), chunk 2 (1024 bytes), chunk 3 (440 bytes)
        val written = buffer.write(position = 512L, src = data, srcOffset = 0, length = 3000)

        // Assert: Should write all 3000 bytes
        assertEquals(3000, written)

        // Act: Read back the data
        val read = buffer.read(position = 512L, dest = result, destOffset = 0, length = 3000)

        // Assert: Should read all 3000 bytes and match original data
        assertEquals(3000, read)
        assertTrue(data.contentEquals(result))
    }

    @Test
    fun `containsRange returns true for available data`() {
        // Arrange
        val buffer = ChunkRingBuffer(chunkSize = 1024, maxChunks = 4)
        val data = ByteArray(2048) { it.toByte() }

        // Act: Write 2KB
        buffer.write(position = 0L, src = data, srcOffset = 0, length = 2048)

        // Assert: Range checks
        assertTrue(buffer.containsRange(position = 0L, length = 2048))
        assertTrue(buffer.containsRange(position = 0L, length = 1024))
        assertTrue(buffer.containsRange(position = 1024L, length = 1024))
        assertTrue(buffer.containsRange(position = 512L, length = 512))
    }

    @Test
    fun `containsRange returns false for unavailable data`() {
        // Arrange
        val buffer = ChunkRingBuffer(chunkSize = 1024, maxChunks = 4)
        val data = ByteArray(1024) { it.toByte() }

        // Act: Write 1KB at position 0
        buffer.write(position = 0L, src = data, srcOffset = 0, length = 1024)

        // Assert: Range checks for unavailable data
        assertFalse(buffer.containsRange(position = 1024L, length = 1024)) // Next chunk not written
        assertFalse(buffer.containsRange(position = 0L, length = 2048)) // Spans into unavailable chunk
        assertFalse(buffer.containsRange(position = 2048L, length = 512)) // Completely outside
    }

    @Test
    fun `read returns partial data when chunk not available`() {
        // Arrange
        val buffer = ChunkRingBuffer(chunkSize = 1024, maxChunks = 4)
        val data = ByteArray(1024) { it.toByte() }
        val result = ByteArray(2048)

        // Act: Write 1KB at position 0 (chunk 0)
        buffer.write(position = 0L, src = data, srcOffset = 0, length = 1024)

        // Act: Try to read 2KB (chunk 0 and chunk 1, but chunk 1 is not written)
        val read = buffer.read(position = 0L, dest = result, destOffset = 0, length = 2048)

        // Assert: Should only read 1KB from chunk 0
        assertEquals(1024, read)
    }

    @Test
    fun `LRU eviction removes oldest chunks when limit exceeded`() {
        // Arrange: Small buffer with 2KB chunks, max 2 chunks (4KB total)
        val buffer = ChunkRingBuffer(chunkSize = 2048, maxChunks = 2)
        val chunk0 = ByteArray(2048) { 0 }
        val chunk1 = ByteArray(2048) { 1 }
        val chunk2 = ByteArray(2048) { 2 }

        // Act: Write chunk 0 at position 0
        buffer.write(position = 0L, src = chunk0, srcOffset = 0, length = 2048)
        assertTrue(buffer.containsRange(position = 0L, length = 2048))

        // Act: Write chunk 1 at position 2048
        buffer.write(position = 2048L, src = chunk1, srcOffset = 0, length = 2048)
        assertTrue(buffer.containsRange(position = 0L, length = 2048))
        assertTrue(buffer.containsRange(position = 2048L, length = 2048))

        // Act: Write chunk 2 at position 4096 (should evict chunk 0)
        buffer.write(position = 4096L, src = chunk2, srcOffset = 0, length = 2048)

        // Assert: Chunk 0 should be evicted (oldest), chunks 1 and 2 should remain
        assertFalse(buffer.containsRange(position = 0L, length = 2048))
        assertTrue(buffer.containsRange(position = 2048L, length = 2048))
        assertTrue(buffer.containsRange(position = 4096L, length = 2048))
    }

    @Test
    fun `clear removes all chunks`() {
        // Arrange
        val buffer = ChunkRingBuffer(chunkSize = 1024, maxChunks = 4)
        val data = ByteArray(2048) { it.toByte() }

        // Act: Write 2KB
        buffer.write(position = 0L, src = data, srcOffset = 0, length = 2048)
        assertTrue(buffer.containsRange(position = 0L, length = 2048))

        // Act: Clear the buffer
        buffer.clear()

        // Assert: No data should be available
        assertFalse(buffer.containsRange(position = 0L, length = 1))
        assertFalse(buffer.containsRange(position = 0L, length = 2048))
    }

    @Test
    fun `write with offset and length works correctly`() {
        // Arrange
        val buffer = ChunkRingBuffer(chunkSize = 1024, maxChunks = 4)
        val data = ByteArray(100) { it.toByte() }
        val result = ByteArray(50)

        // Act: Write 50 bytes from offset 25 to position 0
        val written = buffer.write(position = 0L, src = data, srcOffset = 25, length = 50)
        assertEquals(50, written)

        // Act: Read back the data
        val read = buffer.read(position = 0L, dest = result, destOffset = 0, length = 50)
        assertEquals(50, read)

        // Assert: Verify the data matches the slice we wrote
        val expectedSlice = data.sliceArray(25 until 75)
        assertTrue(expectedSlice.contentEquals(result))
    }

    @Test
    fun `read with offset in destination works correctly`() {
        // Arrange
        val buffer = ChunkRingBuffer(chunkSize = 1024, maxChunks = 4)
        val data = ByteArray(50) { it.toByte() }
        val result = ByteArray(100) { (-1).toByte() } // Initialize with -1 to detect writes

        // Act: Write 50 bytes at position 0
        buffer.write(position = 0L, src = data, srcOffset = 0, length = 50)

        // Act: Read into result starting at offset 25
        val read = buffer.read(position = 0L, dest = result, destOffset = 25, length = 50)
        assertEquals(50, read)

        // Assert: First 25 bytes should still be -1, next 50 bytes should match data, rest should be -1
        for (i in 0 until 25) {
            assertEquals((-1).toByte(), result[i])
        }
        for (i in 0 until 50) {
            assertEquals(data[i], result[i + 25])
        }
        for (i in 75 until 100) {
            assertEquals((-1).toByte(), result[i])
        }
    }

    @Test
    fun `empty buffer returns zero on read`() {
        // Arrange
        val buffer = ChunkRingBuffer(chunkSize = 1024, maxChunks = 4)
        val result = ByteArray(1024)

        // Act: Try to read from empty buffer
        val read = buffer.read(position = 0L, dest = result, destOffset = 0, length = 1024)

        // Assert: Should read 0 bytes
        assertEquals(0, read)
    }
}
