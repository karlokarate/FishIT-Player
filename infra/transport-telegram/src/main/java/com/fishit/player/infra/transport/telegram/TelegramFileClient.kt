package com.fishit.player.infra.transport.telegram

import com.fishit.player.infra.transport.telegram.api.TgFile
import kotlinx.coroutines.flow.Flow

/**
 * Typed interface for Telegram file download operations.
 *
 * This is part of the v2 Transport API Surface. Playback layer consumes
 * this interface for progressive file downloads during streaming.
 *
 * **v2 Architecture:**
 * - Transport handles TDLib file download primitives
 * - No MP4 parsing or playback-specific logic (belongs in playback layer)
 * - Returns [TgFile] wrapper types with download state
 *
 * **RemoteId-First Design:**
 * Files are identified by stable `remoteId` rather than `fileId`.
 * If a fileId becomes stale (TDLib cache eviction), resolve via remoteId.
 *
 * **Implementation:** [DefaultTelegramClient] implements this interface internally.
 *
 * @see TelegramAuthClient for authentication
 * @see TelegramHistoryClient for message fetching
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
interface TelegramFileClient {

    /**
     * Stream of file download updates.
     *
     * Emits [TgFileUpdate] events for all active downloads:
     * - Progress updates (downloaded bytes, total size)
     * - Completion events (local path available)
     * - Error events
     *
     * Playback layer observes this to determine streaming readiness.
     */
    val fileUpdates: Flow<TgFileUpdate>

    /**
     * Start downloading a file.
     *
     * Downloads are queued and managed internally with concurrency limits.
     * Observe [fileUpdates] for progress.
     *
     * **Priority values:**
     * - 32 = High (playback, current item)
     * - 16 = Medium (prefetch, next items)
     * - 1-8 = Low (background backfill)
     *
     * @param fileId TDLib file ID
     * @param priority Download priority (1-32, higher = more urgent)
     * @param offset Starting offset for partial download (default 0)
     * @param limit Bytes to download (0 = entire file)
     */
    suspend fun startDownload(
        fileId: Int,
        priority: Int = 1,
        offset: Long = 0,
        limit: Long = 0
    )

    /**
     * Cancel an active download.
     *
     * @param fileId TDLib file ID
     * @param deleteLocalCopy Whether to delete partially downloaded file
     */
    suspend fun cancelDownload(fileId: Int, deleteLocalCopy: Boolean = false)

    /**
     * Get current file state.
     *
     * @param fileId TDLib file ID
     * @return Current file state or null if not found
     */
    suspend fun getFile(fileId: Int): TgFile?

    /**
     * Resolve remoteId to current fileId.
     *
     * Use this when a fileId becomes stale. TDLib may reassign fileIds
     * after cache eviction, but remoteId remains stable.
     *
     * **Fallback pattern:**
     * 1. Try operation with cached fileId
     * 2. If "file not found" error → resolve via remoteId
     * 3. Retry operation with new fileId
     *
     * @param remoteId Stable remote file identifier
     * @return Resolved [TgFile] with current fileId, or null if not found
     */
    suspend fun resolveRemoteId(remoteId: String): TgFile?

    /**
     * Get downloaded prefix size for a file.
     *
     * Used by playback layer to determine if enough data is available
     * for streaming to begin.
     *
     * @param fileId TDLib file ID
     * @return Number of bytes downloaded from the beginning of the file
     */
    suspend fun getDownloadedPrefixSize(fileId: Int): Long

    /**
     * Get storage statistics.
     *
     * Returns fast approximation of TDLib cache usage.
     *
     * @return Storage stats (total size, file counts by type)
     */
    suspend fun getStorageStats(): TgStorageStats

    /**
     * Optimize storage by removing old files.
     *
     * TDLib will remove files based on size/age thresholds.
     *
     * @param maxSizeBytes Maximum cache size to maintain
     * @param maxAgeDays Maximum file age in days
     * @return Number of bytes freed
     */
    suspend fun optimizeStorage(
        maxSizeBytes: Long = 5L * 1024 * 1024 * 1024, // 5GB default
        maxAgeDays: Int = 30
    ): Long
}

/**
 * File download update event.
 */
sealed class TgFileUpdate {
    /** File ID this update refers to */
    abstract val fileId: Int

    /**
     * Download progress update.
     *
     * @param fileId TDLib file ID
     * @param downloadedSize Bytes downloaded so far
     * @param totalSize Total file size (0 if unknown)
     * @param downloadedPrefixSize Bytes downloaded from beginning
     */
    data class Progress(
        override val fileId: Int,
        val downloadedSize: Long,
        val totalSize: Long,
        val downloadedPrefixSize: Long
    ) : TgFileUpdate()

    /**
     * Download completed.
     *
     * @param fileId TDLib file ID
     * @param localPath Full path to downloaded file
     */
    data class Completed(
        override val fileId: Int,
        val localPath: String
    ) : TgFileUpdate()

    /**
     * Download failed.
     *
     * @param fileId TDLib file ID
     * @param error Error description
     * @param errorCode TDLib error code (if available)
     */
    data class Failed(
        override val fileId: Int,
        val error: String,
        val errorCode: Int? = null
    ) : TgFileUpdate()
}

/**
 * Storage statistics from TDLib.
 */
data class TgStorageStats(
    val totalSize: Long,
    val photoCount: Int,
    val videoCount: Int,
    val documentCount: Int,
    val audioCount: Int,
    val otherCount: Int
)
