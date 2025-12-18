package com.fishit.player.v2.di

import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.fishit.player.core.imaging.fetcher.TelegramThumbFetcher
import com.fishit.player.core.model.ImageRef
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import java.io.File
import java.io.IOException
import kotlinx.coroutines.withTimeout
import okio.buffer
import okio.source

/**
 * TelegramThumbFetcher implementation using TelegramTransportClient.
 *
 * **Purpose:**
 * - Resolves [ImageRef.TelegramThumb] references via TDLib
 * - Downloads thumbnail files if not cached locally
 * - Returns local file path for Coil to decode
 *
 * **Architecture:**
 * - core:ui-imaging defines the [TelegramThumbFetcher] interface
 * - This implementation lives in app-v2 (the only module with both dependencies)
 * - Wired via [ImagingModule] DI
 *
 * **Download Strategy:**
 * 1. Check if file is already downloaded (localPath available)
 * 2. If not, request download via TelegramTransportClient
 * 3. Wait for download completion (with timeout)
 * 4. Return SourceFetchResult with local file
 */
class TelegramThumbFetcherImpl(
        private val telegramClient: TelegramTransportClient,
        private val ref: ImageRef.TelegramThumb,
        private val options: Options,
) : TelegramThumbFetcher {
    companion object {
        /** Timeout for thumbnail download (thumbnails are small, should be fast) */
        private const val DOWNLOAD_TIMEOUT_MS = 15_000L

        /** Retry delay between download checks */
        private const val RETRY_DELAY_MS = 100L

        /** Maximum retries for download completion check */
        private const val MAX_RETRIES = 50
    }

    override suspend fun fetch(
            ref: ImageRef.TelegramThumb,
            options: Options,
    ): FetchResult {
        return withTimeout(DOWNLOAD_TIMEOUT_MS) {
            // Step 1: Resolve file by remoteId first
            val resolvedFile = telegramClient.resolveFileByRemoteId(ref.remoteId)

            // Step 2: Check if already downloaded
            val initialPath = resolvedFile.localPath
            if (resolvedFile.isDownloadingCompleted && !initialPath.isNullOrBlank()) {
                return@withTimeout createResult(initialPath)
            }

            // Step 3: Request download using resolved fileId
            telegramClient.requestFileDownload(
                    fileId = resolvedFile.id,
                    priority = 24, // High priority for thumbnails
            )

            // Step 4: Poll for download completion
            var retries = 0
            while (retries < MAX_RETRIES) {
                kotlinx.coroutines.delay(RETRY_DELAY_MS)

                val updated = telegramClient.resolveFile(resolvedFile.id)
                val updatedPath = updated.localPath

                if (updated.isDownloadingCompleted && !updatedPath.isNullOrBlank()) {
                    return@withTimeout createResult(updatedPath)
                }

                if (!updated.isDownloadingActive && updatedPath.isNullOrBlank()) {
                    throw IOException(
                            "Telegram thumbnail download failed for remoteId=${ref.remoteId}",
                    )
                }

                retries++
            }

            throw IOException(
                    "Telegram thumbnail download timeout for remoteId=${ref.remoteId}",
            )
        }
    }

    private fun createResult(localPath: String): FetchResult {
        val file = File(localPath)

        if (!file.exists()) {
            throw IOException("Downloaded file not found: $localPath")
        }

        if (!file.canRead()) {
            throw IOException("Cannot read downloaded file: $localPath")
        }

        return SourceFetchResult(
                source =
                        ImageSource(
                                source = file.inputStream().source().buffer(),
                                fileSystem = options.fileSystem,
                        ),
                mimeType = guessMimeType(localPath),
                dataSource = DataSource.DISK,
        )
    }

    private fun guessMimeType(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg" // Default for Telegram thumbnails
        }
    }

    /** Factory for creating [TelegramThumbFetcherImpl] instances. */
    class Factory(
            private val telegramClient: TelegramTransportClient,
    ) : TelegramThumbFetcher.Factory {
        override fun create(
                ref: ImageRef.TelegramThumb,
                options: Options,
        ): TelegramThumbFetcher = TelegramThumbFetcherImpl(telegramClient, ref, options)
    }
}
