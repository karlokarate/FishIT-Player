package com.chris.m3usuite.telegram.core

/**
 * Thread-safe chunk-based ringbuffer for in-memory streaming cache.
 *
 * This class provides a memory-efficient ringbuffer implementation that:
 * - Divides the byte stream into fixed-size chunks
 * - Uses LRU (Least Recently Used) eviction when capacity is exceeded
 * - Supports random access reads and writes
 * - Thread-safe for concurrent access
 *
 * The ringbuffer is generic and not specific to Telegram - it can be used
 * for any byte-range caching scenario.
 *
 * @param chunkSize Size of each chunk in bytes (e.g., 256 KB)
 * @param maxChunks Maximum number of chunks to keep in memory (e.g., 64)
 */
internal class ChunkRingBuffer(
    private val chunkSize: Int,
    private val maxChunks: Int,
) {
    // Map: chunkIndex -> ByteArray(chunkSize), with LRU-Eviction
    private val chunks =
        object : LinkedHashMap<Long, ByteArray>(maxChunks, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>): Boolean {
                return size > maxChunks
            }
        }

    /**
     * Clear all chunks from the buffer.
     * Thread-safe.
     */
    @Synchronized
    fun clear() {
        chunks.clear()
    }

    /**
     * Write data to the buffer at the specified position.
     *
     * The write operation automatically spans across chunk boundaries if needed.
     * Chunks are created on-demand if they don't exist.
     *
     * @param position Starting byte position in the virtual stream
     * @param src Source byte array
     * @param srcOffset Offset in source array to start reading from
     * @param length Number of bytes to write
     * @return Number of bytes written (always equals length)
     */
    @Synchronized
    fun write(
        position: Long,
        src: ByteArray,
        srcOffset: Int,
        length: Int,
    ): Int {
        var remaining = length
        var srcPos = srcOffset
        var pos = position

        while (remaining > 0) {
            val chunkIndex = pos / chunkSize
            val offsetInChunk = (pos % chunkSize).toInt()

            val chunk = chunks.getOrPut(chunkIndex) { ByteArray(chunkSize) }

            val copyLen = minOf(remaining, chunkSize - offsetInChunk)
            System.arraycopy(src, srcPos, chunk, offsetInChunk, copyLen)

            remaining -= copyLen
            srcPos += copyLen
            pos += copyLen
        }

        return length
    }

    /**
     * Read data from the buffer at the specified position.
     *
     * The read operation automatically spans across chunk boundaries if needed.
     * If a required chunk is not in the buffer, the read stops and returns
     * the number of bytes read so far.
     *
     * @param position Starting byte position in the virtual stream
     * @param dest Destination byte array
     * @param destOffset Offset in destination array to start writing to
     * @param length Maximum number of bytes to read
     * @return Number of bytes actually read (may be less than length if data not available)
     */
    @Synchronized
    fun read(
        position: Long,
        dest: ByteArray,
        destOffset: Int,
        length: Int,
    ): Int {
        var remaining = length
        var destPos = destOffset
        var pos = position
        var totalRead = 0

        while (remaining > 0) {
            val chunkIndex = pos / chunkSize
            val offsetInChunk = (pos % chunkSize).toInt()

            val chunk = chunks[chunkIndex] ?: break

            val available = chunkSize - offsetInChunk
            if (available <= 0) break

            val copyLen = minOf(remaining, available)
            System.arraycopy(chunk, offsetInChunk, dest, destPos, copyLen)

            remaining -= copyLen
            destPos += copyLen
            pos += copyLen
            totalRead += copyLen
        }

        return totalRead
    }

    /**
     * Check if the buffer contains all data for the specified range.
     *
     * This method verifies that all chunks required to satisfy the read
     * are present in the buffer without actually reading the data.
     *
     * @param position Starting byte position in the virtual stream
     * @param length Number of bytes to check
     * @return true if all required chunks are present, false otherwise
     */
    @Synchronized
    fun containsRange(
        position: Long,
        length: Int,
    ): Boolean {
        var remaining = length
        var pos = position

        while (remaining > 0) {
            val chunkIndex = pos / chunkSize
            val offsetInChunk = (pos % chunkSize).toInt()

            val chunk = chunks[chunkIndex] ?: return false
            val available = chunkSize - offsetInChunk
            if (available <= 0) return false

            val step = minOf(remaining, available)
            remaining -= step
            pos += step
        }

        return true
    }
}
