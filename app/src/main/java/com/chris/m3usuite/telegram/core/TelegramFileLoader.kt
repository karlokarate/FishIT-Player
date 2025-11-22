package com.chris.m3usuite.telegram.core

import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import dev.g000sha256.tdl.dto.DownloadFile
import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.delay
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
 */
class TelegramFileLoader(
    private val serviceClient: T_TelegramServiceClient,
) {
    companion object {
        private const val TAG = "TelegramFileLoader"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val DEFAULT_PRIORITY = 16 // Lower priority for thumbnails
        private const val POLL_INTERVAL_MS = 100L
    }

    /**
     * Ensure thumbnail is downloaded and return local path (Requirement 3).
     *
     * Downloads the file with low priority and polls until complete.
     * Returns the local file path if successful, null otherwise.
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
        return withTimeoutOrNull(timeoutMs) {
            try {
                TelegramLogRepository.debug(
                    source = TAG,
                    message = "ensureThumbDownloaded: Starting download for fileId=$fileId",
                )

                // Get current file state
                val session = serviceClient.getSessionOrNull()
                if (session == null) {
                    TelegramLogRepository.warn(
                        source = TAG,
                        message = "ensureThumbDownloaded: No active session",
                    )
                    return@withTimeoutOrNull null
                }

                // Request download with low priority
                val downloadRequest =
                    DownloadFile(
                        fileId = fileId,
                        priority = DEFAULT_PRIORITY,
                        offset = 0,
                        limit = 0, // Download entire file
                        synchronous = false,
                    )

                val downloadResult = session.client.send(downloadRequest)
                when (downloadResult) {
                    is dev.g000sha256.tdl.TdlResult.Success -> {
                        val file = downloadResult.result
                        
                        // If already complete, return path
                        if (file.local?.isDownloadingCompleted == true) {
                            val path = file.local?.path
                            if (!path.isNullOrEmpty()) {
                                TelegramLogRepository.debug(
                                    source = TAG,
                                    message = "ensureThumbDownloaded: Already complete fileId=$fileId, path=$path",
                                )
                                return@withTimeoutOrNull path
                            }
                        }

                        // Poll until download completes
                        val startTime = System.currentTimeMillis()
                        while (true) {
                            delay(POLL_INTERVAL_MS)
                            
                            // Check timeout
                            if (System.currentTimeMillis() - startTime > timeoutMs) {
                                TelegramLogRepository.warn(
                                    source = TAG,
                                    message = "ensureThumbDownloaded: Timeout for fileId=$fileId",
                                )
                                return@withTimeoutOrNull null
                            }

                            // Get file state
                            val getFileRequest = dev.g000sha256.tdl.dto.GetFile(fileId)
                            val fileResult = session.client.send(getFileRequest)
                            when (fileResult) {
                                is dev.g000sha256.tdl.TdlResult.Success -> {
                                    val currentFile = fileResult.result
                                    if (currentFile.local?.isDownloadingCompleted == true) {
                                        val path = currentFile.local?.path
                                        if (!path.isNullOrEmpty()) {
                                            TelegramLogRepository.debug(
                                                source = TAG,
                                                message = "ensureThumbDownloaded: Complete fileId=$fileId, path=$path",
                                            )
                                            return@withTimeoutOrNull path
                                        }
                                    }
                                }
                                is dev.g000sha256.tdl.TdlResult.Failure -> {
                                    TelegramLogRepository.error(
                                        source = TAG,
                                        message = "ensureThumbDownloaded: GetFile failed for fileId=$fileId",
                                        error = Exception("TDLib error ${fileResult.code}: ${fileResult.message}"),
                                    )
                                    return@withTimeoutOrNull null
                                }
                            }
                        }
                    }
                    is dev.g000sha256.tdl.TdlResult.Failure -> {
                        TelegramLogRepository.error(
                            source = TAG,
                            message = "ensureThumbDownloaded: DownloadFile failed for fileId=$fileId",
                            error = Exception("TDLib error ${downloadResult.code}: ${downloadResult.message}"),
                        )
                        return@withTimeoutOrNull null
                    }
                }
            } catch (e: Exception) {
                TelegramLogRepository.error(
                    source = TAG,
                    message = "ensureThumbDownloaded: Exception for fileId=$fileId",
                    error = e,
                )
                return@withTimeoutOrNull null
            }
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
            val session = serviceClient.getSessionOrNull() ?: return null
            
            val getFileRequest = dev.g000sha256.tdl.dto.GetFile(fileId)
            val fileResult = session.client.send(getFileRequest)
            
            when (fileResult) {
                is dev.g000sha256.tdl.TdlResult.Success -> {
                    val file = fileResult.result
                    if (file.local?.isDownloadingCompleted == true) {
                        file.local?.path?.takeUnless { it.isEmpty() }
                    } else {
                        null
                    }
                }
                is dev.g000sha256.tdl.TdlResult.Failure -> null
            }
        } catch (e: Exception) {
            TelegramLogRepository.error(
                source = TAG,
                message = "getLocalPathIfAvailable: Exception for fileId=$fileId",
                error = e,
            )
            null
        }
    }

    /**
     * Ensure file is ready for zero-copy playback (Requirement 6).
     * Downloads with high priority and ensures sufficient prefix is available.
     *
     * @param fileId TDLib file ID
     * @param minPrefixBytes Minimum bytes to download from start
     * @return Local file path or null
     */
    suspend fun ensureFileForPlayback(
        fileId: Int,
        minPrefixBytes: Long = 1024 * 1024, // 1 MB default
        timeoutMs: Long = 60_000L,
    ): String? {
        return withTimeoutOrNull(timeoutMs) {
            try {
                val session = serviceClient.getSessionOrNull() ?: return@withTimeoutOrNull null

                // Request download with high priority for playback
                val downloadRequest =
                    DownloadFile(
                        fileId = fileId,
                        priority = 32, // High priority for playback
                        offset = 0,
                        limit = minPrefixBytes.toInt(),
                        synchronous = false,
                    )

                val downloadResult = session.client.send(downloadRequest)
                when (downloadResult) {
                    is dev.g000sha256.tdl.TdlResult.Success -> {
                        val file = downloadResult.result
                        
                        // Wait for sufficient data
                        val startTime = System.currentTimeMillis()
                        while (true) {
                            delay(POLL_INTERVAL_MS)
                            
                            if (System.currentTimeMillis() - startTime > timeoutMs) {
                                TelegramLogRepository.warn(
                                    source = TAG,
                                    message = "ensureFileForPlayback: Timeout for fileId=$fileId",
                                )
                                return@withTimeoutOrNull null
                            }

                            val getFileRequest = dev.g000sha256.tdl.dto.GetFile(fileId)
                            val fileResult = session.client.send(getFileRequest)
                            when (fileResult) {
                                is dev.g000sha256.tdl.TdlResult.Success -> {
                                    val currentFile = fileResult.result
                                    val downloadedBytes = currentFile.local?.downloadedSize ?: 0
                                    
                                    // Return if we have enough data or download is complete
                                    if (downloadedBytes >= minPrefixBytes ||
                                        currentFile.local?.isDownloadingCompleted == true
                                    ) {
                                        val path = currentFile.local?.path
                                        if (!path.isNullOrEmpty()) {
                                            TelegramLogRepository.info(
                                                source = TAG,
                                                message = "ensureFileForPlayback: Ready fileId=$fileId, downloaded=$downloadedBytes bytes, path=$path",
                                            )
                                            return@withTimeoutOrNull path
                                        }
                                    }
                                }
                                is dev.g000sha256.tdl.TdlResult.Failure -> {
                                    return@withTimeoutOrNull null
                                }
                            }
                        }
                    }
                    is dev.g000sha256.tdl.TdlResult.Failure -> {
                        TelegramLogRepository.error(
                            source = TAG,
                            message = "ensureFileForPlayback: DownloadFile failed for fileId=$fileId",
                            error = Exception("TDLib error ${downloadResult.code}: ${downloadResult.message}"),
                        )
                        return@withTimeoutOrNull null
                    }
                }
            } catch (e: Exception) {
                TelegramLogRepository.error(
                    source = TAG,
                    message = "ensureFileForPlayback: Exception for fileId=$fileId",
                    error = e,
                )
                return@withTimeoutOrNull null
            }
        }
    }
}
