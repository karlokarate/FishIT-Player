package com.chris.m3usuite.telegram.player

/**
 * Utility for calculating video chunk boundaries for memory-efficient streaming.
 *
 * **Purpose:**
 * Split large video files into manageable chunks for progressive streaming,
 * allowing the app to:
 * - Load only necessary chunks (current + preload)
 * - Delete old chunks to free up cache space
 * - Provide seamless playback across chunk boundaries
 *
 * **Typical Usage:**
 * ```kotlin
 * val chunks = ChunkCalculator.calculateChunks(
 *     durationMs = 120 * 60 * 1000L, // 120 minutes
 *     fileSizeBytes = 2_000_000_000L, // 2GB
 *     chunkDurationMs = 30 * 60 * 1000L // 30 minutes per chunk
 * )
 * // Result: 4 chunks of ~500MB each
 * ```
 *
 * @see TelegramChunkedDataSource for DataSource implementation using chunks
 */
object ChunkCalculator {
    /**
     * Default chunk duration: 30 minutes.
     * This provides a good balance between:
     * - Memory usage (2 chunks = ~1GB for typical video)
     * - Preload time (30min chunk loads in 5-10 seconds on good connection)
     * - Seek granularity (max 30min wait for far seeks)
     */
    const val DEFAULT_CHUNK_DURATION_MS = 30 * 60 * 1000L // 30 minutes

    /**
     * Minimum chunk size: 10MB.
     * Prevents creating too many tiny chunks for short videos.
     */
    const val MIN_CHUNK_SIZE_BYTES = 10 * 1024 * 1024L // 10MB

    /**
     * Calculate video chunks based on duration and file size.
     *
     * **Algorithm:**
     * 1. Calculate bytes per millisecond (bitrate)
     * 2. Iterate through video timeline in chunk-duration steps
     * 3. Compute byte ranges for each chunk
     * 4. Handle edge cases (last chunk, unknown duration)
     *
     * **Example:**
     * ```
     * Video: 120 min (7,200,000 ms), 2GB (2,147,483,648 bytes)
     * Bitrate: 2,147,483,648 / 7,200,000 ≈ 298 bytes/ms
     * Chunk 0: 0-30min (0-1,800,000ms) → 0-536,870,912 bytes (~512MB)
     * Chunk 1: 30-60min → 536,870,912-1,073,741,824 bytes (~512MB)
     * Chunk 2: 60-90min → 1,073,741,824-1,610,612,736 bytes (~512MB)
     * Chunk 3: 90-120min → 1,610,612,736-2,147,483,648 bytes (~512MB)
     * ```
     *
     * @param durationMs Total video duration in milliseconds
     * @param fileSizeBytes Total file size in bytes
     * @param chunkDurationMs Desired chunk duration (default: 30 minutes)
     * @return List of ChunkInfo objects with byte ranges and time ranges
     * @throws IllegalArgumentException if durationMs or fileSizeBytes <= 0
     */
    fun calculateChunks(
        durationMs: Long,
        fileSizeBytes: Long,
        chunkDurationMs: Long = DEFAULT_CHUNK_DURATION_MS,
    ): List<ChunkInfo> {
        require(durationMs > 0) { "durationMs must be positive, got: $durationMs" }
        require(fileSizeBytes > 0) { "fileSizeBytes must be positive, got: $fileSizeBytes" }
        require(chunkDurationMs > 0) { "chunkDurationMs must be positive, got: $chunkDurationMs" }

        val chunks = mutableListOf<ChunkInfo>()

        // Calculate bitrate (bytes per millisecond)
        val bytesPerMs = fileSizeBytes.toDouble() / durationMs.toDouble()

        var currentMs = 0L
        var chunkIndex = 0

        while (currentMs < durationMs) {
            val endMs = minOf(currentMs + chunkDurationMs, durationMs)
            val startByte = (currentMs * bytesPerMs).toLong()
            val endByte = (endMs * bytesPerMs).toLong()
            val sizeBytes = endByte - startByte

            // Skip chunks that are too small (edge case protection)
            if (sizeBytes < MIN_CHUNK_SIZE_BYTES && currentMs > 0) {
                // Merge with previous chunk
                val prevChunk = chunks.removeLast()
                chunks.add(
                    prevChunk.copy(
                        endMs = endMs,
                        endByte = endByte,
                        sizeBytes = endByte - prevChunk.startByte,
                    ),
                )
            } else {
                chunks.add(
                    ChunkInfo(
                        index = chunkIndex,
                        startMs = currentMs,
                        endMs = endMs,
                        startByte = startByte,
                        endByte = endByte,
                        sizeBytes = sizeBytes,
                    ),
                )
                chunkIndex++
            }

            currentMs = endMs
        }

        return chunks
    }

    /**
     * Find the chunk index that contains a given playback position.
     *
     * @param chunks List of chunks (must be sorted by startMs)
     * @param positionMs Current playback position in milliseconds
     * @return Chunk index, or -1 if position is out of bounds
     */
    fun getChunkIndexForPosition(
        chunks: List<ChunkInfo>,
        positionMs: Long,
    ): Int {
        if (chunks.isEmpty()) return -1
        if (positionMs < 0) return -1

        // Binary search for efficiency with many chunks
        var left = 0
        var right = chunks.size - 1

        while (left <= right) {
            val mid = (left + right) / 2
            val chunk = chunks[mid]

            when {
                positionMs < chunk.startMs -> right = mid - 1
                positionMs >= chunk.endMs -> left = mid + 1
                else -> return mid // Found: startMs <= positionMs < endMs
            }
        }

        // Edge case: position exactly at last chunk's endMs
        if (positionMs >= chunks.last().endMs) {
            return chunks.size - 1
        }

        return -1 // Not found (shouldn't happen with valid input)
    }

    /**
     * Find the chunk index that contains a given byte offset.
     *
     * @param chunks List of chunks (must be sorted by startByte)
     * @param byteOffset Current byte offset in file
     * @return Chunk index, or -1 if offset is out of bounds
     */
    fun getChunkIndexForByteOffset(
        chunks: List<ChunkInfo>,
        byteOffset: Long,
    ): Int {
        if (chunks.isEmpty()) return -1
        if (byteOffset < 0) return -1

        // Binary search
        var left = 0
        var right = chunks.size - 1

        while (left <= right) {
            val mid = (left + right) / 2
            val chunk = chunks[mid]

            when {
                byteOffset < chunk.startByte -> right = mid - 1
                byteOffset >= chunk.endByte -> left = mid + 1
                else -> return mid // Found: startByte <= byteOffset < endByte
            }
        }

        // Edge case: offset exactly at last chunk's endByte
        if (byteOffset >= chunks.last().endByte) {
            return chunks.size - 1
        }

        return -1
    }

    /**
     * Calculate total cache size needed for active chunks.
     *
     * @param chunks List of all chunks
     * @param currentChunkIndex Current playback chunk
     * @param preloadCount Number of chunks to preload ahead
     * @param keepBehindCount Number of chunks to keep behind current
     * @return Total bytes needed in cache
     */
    fun calculateRequiredCacheSize(
        chunks: List<ChunkInfo>,
        currentChunkIndex: Int,
        preloadCount: Int = 1,
        keepBehindCount: Int = 1,
    ): Long {
        if (chunks.isEmpty() || currentChunkIndex < 0 || currentChunkIndex >= chunks.size) {
            return 0L
        }

        val startIndex = (currentChunkIndex - keepBehindCount).coerceAtLeast(0)
        val endIndex = (currentChunkIndex + preloadCount).coerceAtMost(chunks.size - 1)

        return (startIndex..endIndex).sumOf { chunks[it].sizeBytes }
    }

    /**
     * Information about a video chunk.
     *
     * @property index Sequential chunk number (0-based)
     * @property startMs Start time in video timeline (milliseconds)
     * @property endMs End time in video timeline (milliseconds)
     * @property startByte Start byte offset in file
     * @property endByte End byte offset in file (exclusive)
     * @property sizeBytes Chunk size in bytes (endByte - startByte)
     */
    data class ChunkInfo(
        val index: Int,
        val startMs: Long,
        val endMs: Long,
        val startByte: Long,
        val endByte: Long,
        val sizeBytes: Long,
    ) {
        /** Duration of this chunk in milliseconds. */
        val durationMs: Long
            get() = endMs - startMs

        /** Human-readable duration string (e.g., "30:00"). */
        val durationString: String
            get() {
                val totalSeconds = durationMs / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                return "%d:%02d".format(minutes, seconds)
            }

        /** Human-readable size string (e.g., "512 MB"). */
        val sizeString: String
            get() {
                val mb = sizeBytes / (1024.0 * 1024.0)
                return "%.1f MB".format(mb)
            }

        override fun toString(): String =
            "Chunk #$index: ${durationString} ($sizeString) | " +
                "Time: ${startMs / 1000}s-${endMs / 1000}s | " +
                "Bytes: $startByte-$endByte"
    }
}
