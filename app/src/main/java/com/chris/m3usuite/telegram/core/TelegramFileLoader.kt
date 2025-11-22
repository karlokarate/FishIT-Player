package com.chris.m3usuite.telegram.core

import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles downloading and accessing Telegram files via TDLib.
 * Supports lazy thumbnail loading and zero-copy file access (Requirement 3, 6).
 *
 * Key Features:
 * - ensureThumbDownloaded(): Coroutine-based thumbnail downloading
 * - Returns local file paths from TDLib cache
 * - No file copying - uses TDLib's local directory
 * - Automatic retry with exponential backoff
 * - Progress logging for debugging
 *
 * This is a thin wrapper around T_TelegramFileDownloader for convenience.
 */
class TelegramFileLoader(
    private val serviceClient: T_TelegramServiceClient,
) {
    private val downloader = serviceClient.downloader()

    companion object {
        private const val TAG = "TelegramFileLoader"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val DEFAULT_PRIORITY = 16 // Lower priority for thumbnails
    }

    /**
     * Ensure thumbnail is downloaded and return local path (Requirement 3).
     *
     * Thin wrapper around T_TelegramFileDownloader.ensureFileReady().
     * Downloads the file with low priority and returns local path.
     *
     * This is designed to be called from LaunchedEffect in tiles.
     *
     * @param fileId TDLib file ID
     * @param timeoutMs Maximum time to wait for download
     * @return Local file path or null
     */
    suspend fun ensureThumbDownloaded(
        fileId: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): String? {
        return try {
            TelegramLogRepository.debug(
                source = TAG,
                message = "ensureThumbDownloaded: Starting download for fileId=$fileId",
            )

            // Use downloader to ensure the file is ready (entire file for thumbnails)
            val path =
                downloader.ensureFileReady(
                    fileId = fileId,
                    startPosition = 0,
                    minBytes = 0, // Download entire file
                    timeoutMs = timeoutMs,
                )

            TelegramLogRepository.debug(
                source = TAG,
                message = "ensureThumbDownloaded: Complete fileId=$fileId, path=$path",
            )
            path
        } catch (e: Exception) {
            TelegramLogRepository.error(
                source = TAG,
                message = "ensureThumbDownloaded: Exception for fileId=$fileId",
                exception = e,
            )
            null
        }
    }

    /**
     * Get local path for a file if already downloaded (Requirement 6).
     * Does not trigger download - returns null if not locally available.
     *
     * @param fileId TDLib file ID
     * @return Local file path or null
     */
    suspend fun getLocalPathIfAvailable(fileId: Int): String? {
        return try {
            val fileInfo = downloader.getFileInfo(fileId) ?: return null

            if (fileInfo.local?.isDownloadingCompleted == true) {
                fileInfo.local?.path?.takeUnless { it.isEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            TelegramLogRepository.error(
                source = TAG,
                message = "getLocalPathIfAvailable: Exception for fileId=$fileId",
                exception = e,
            )
            null
        }
    }

    /**
     * Optional helper for future direct file-path playback.
     * Currently unused by the main Telegram playback path,
     * which relies on TelegramFileDataSource + downloader.
     *
     * Thin wrapper around T_TelegramFileDownloader.ensureFileReady().
     * Downloads with high priority and ensures sufficient prefix is available.
     *
     * @param fileId TDLib file ID
     * @param minPrefixBytes Minimum bytes to download from start
     * @param timeoutMs Maximum time to wait
     * @return Local file path or null
     */
    suspend fun ensureFileForPlayback(
        fileId: Int,
        minPrefixBytes: Long = 1024 * 1024, // 1 MB default
        timeoutMs: Long = 60_000L,
    ): String? {
        return try {
            TelegramLogRepository.info(
                source = TAG,
                message = "ensureFileForPlayback: Starting for fileId=$fileId, minPrefix=$minPrefixBytes",
            )

            val path =
                downloader.ensureFileReady(
                    fileId = fileId,
                    startPosition = 0,
                    minBytes = minPrefixBytes,
                    timeoutMs = timeoutMs,
                )

            TelegramLogRepository.info(
                source = TAG,
                message = "ensureFileForPlayback: Ready fileId=$fileId, path=$path",
            )
            path
        } catch (e: Exception) {
            TelegramLogRepository.error(
                source = TAG,
                message = "ensureFileForPlayback: Exception for fileId=$fileId",
                exception = e,
            )
            null
        }
    }
}
