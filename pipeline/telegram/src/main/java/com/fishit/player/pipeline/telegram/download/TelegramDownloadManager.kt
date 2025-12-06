package com.fishit.player.pipeline.telegram.download

/**
 * Interface for managing Telegram file downloads.
 *
 * Phase 2: Stub interface only.
 * Phase 3+: Real implementation using TDLib file download APIs.
 *
 * This interface will handle:
 * - Progressive file downloads for offline viewing
 * - Download queue management
 * - Download progress tracking
 * - Local file storage management
 */
interface TelegramDownloadManager {
    /**
     * Download status for a file.
     */
    enum class DownloadStatus {
        NOT_DOWNLOADED,
        DOWNLOADING,
        DOWNLOADED,
        FAILED,
    }

    /**
     * Request download of a Telegram file.
     *
     * @param fileId TDLib file identifier
     * @param priority Download priority (higher = sooner)
     * @return true if download started, false if already downloaded or failed
     */
    suspend fun downloadFile(
        fileId: Int,
        priority: Int = 0,
    ): Boolean

    /**
     * Cancel an ongoing download.
     *
     * @param fileId TDLib file identifier
     */
    suspend fun cancelDownload(fileId: Int)

    /**
     * Get download status for a file.
     *
     * @param fileId TDLib file identifier
     * @return Current download status
     */
    suspend fun getDownloadStatus(fileId: Int): DownloadStatus

    /**
     * Get local file path if downloaded.
     *
     * @param fileId TDLib file identifier
     * @return Local file path, or null if not downloaded
     */
    suspend fun getLocalPath(fileId: Int): String?

    /**
     * Delete a downloaded file from local storage.
     *
     * @param fileId TDLib file identifier
     */
    suspend fun deleteDownload(fileId: Int)

    /**
     * Get total size of all downloaded files.
     *
     * @return Total size in bytes
     */
    suspend fun getTotalDownloadSize(): Long
}
