package com.fishit.player.pipeline.telegram.download

/**
 * Stub implementation of TelegramDownloadManager for Phase 2.
 *
 * Returns deterministic mock results without performing actual downloads.
 */
class TelegramDownloadManagerStub : TelegramDownloadManager {
    /**
     * Returns false (download not started) in stub phase.
     */
    override suspend fun downloadFile(
        fileId: Int,
        priority: Int,
    ): Boolean = false

    /**
     * No-op in stub phase.
     */
    override suspend fun cancelDownload(fileId: Int) {
        // No-op
    }

    /**
     * Returns NOT_DOWNLOADED status for all files in stub phase.
     */
    override suspend fun getDownloadStatus(fileId: Int): TelegramDownloadManager.DownloadStatus =
        TelegramDownloadManager.DownloadStatus.NOT_DOWNLOADED

    /**
     * Returns null (no local file) in stub phase.
     */
    override suspend fun getLocalPath(fileId: Int): String? = null

    /**
     * No-op in stub phase.
     */
    override suspend fun deleteDownload(fileId: Int) {
        // No-op
    }

    /**
     * Returns 0 (no downloads) in stub phase.
     */
    override suspend fun getTotalDownloadSize(): Long = 0L
}
