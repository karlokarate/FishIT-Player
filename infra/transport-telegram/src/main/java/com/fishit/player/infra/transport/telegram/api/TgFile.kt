package com.fishit.player.infra.transport.telegram.api

/**
 * Transport-layer file descriptor for Telegram files.
 *
 * This is a pure DTO with no TDLib dependencies. Created by mapping
 * TDLib `File` objects in the transport layer.
 *
 * **v2 Architecture:**
 * - Transport produces this DTO
 * - Pipeline/Playback consume it
 * - No TDLib types leak outside transport
 *
 * @property id TDLib internal file ID (may become stale after cache eviction)
 * @property remoteId Stable remote file identifier (use for cache key/recovery)
 * @property uniqueId Unique file identifier (persistent across sessions)
 * @property size File size in bytes (if known)
 * @property expectedSize Expected total file size (useful when size is 0)
 * @property localPath Path to local file if downloaded, null otherwise
 * @property isDownloadingActive True if download is currently in progress
 * @property isDownloadingCompleted True if file is fully downloaded
 * @property downloadedSize Total bytes downloaded (may differ from prefix if seeking)
 * @property downloadedPrefixSize Bytes downloaded from start of file (for streaming readiness)
 */
data class TgFile(
    val id: Int,
    val remoteId: String?,
    val uniqueId: String? = null,
    val size: Long = 0L,
    val expectedSize: Long = 0L,
    val localPath: String? = null,
    val isDownloadingActive: Boolean = false,
    val isDownloadingCompleted: Boolean = false,
    val downloadedSize: Long = 0L,
    val downloadedPrefixSize: Long = 0L,
) {
    /**
     * Alias for [id] for compatibility with existing code referencing fileId.
     */
    val fileId: Int get() = id
}
