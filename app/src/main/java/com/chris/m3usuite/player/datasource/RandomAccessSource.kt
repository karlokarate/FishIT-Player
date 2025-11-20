package com.chris.m3usuite.player.datasource

import java.io.Closeable

/**
 * Minimal random access abstraction used by the player-facing data sources. Implementations
 * expose metadata (size + MIME) and provide offset-based reads into a caller-provided buffer.
 */
interface RandomAccessSource : Closeable {
    /** Total uncompressed size in bytes if known, otherwise `-1`. */
    fun size(): Long

    /** MIME type of the content. */
    fun mime(): String

    /**
     * Reads up to [len] bytes from [offset] into [dst] at [off]. Returns the number of bytes
     * read or `-1` on EOF.
     */
    fun read(
        offset: Long,
        dst: ByteArray,
        off: Int,
        len: Int,
    ): Int
}
