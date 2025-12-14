package com.fishit.player.infra.transport.telegram.imaging.coil

import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.fishit.player.core.imaging.fetcher.TelegramThumbFetcher
import com.fishit.player.core.model.ImageRef
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import kotlinx.coroutines.withTimeout
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException

/**
 * Coil TelegramThumbFetcher implementation using TelegramTransportClient.
 *
 * **Purpose:**
 * - Bridges Coil 3 image loading with Telegram transport layer
 * - Resolves ImageRef.TelegramThumb references via TDLib
 * - Downloads thumbnail files if not cached locally
 * - Returns local file path for Coil to decode
 *
 * **Architecture (Phase B2):**
 * - Migrated from app-v2 to infra/transport-telegram
 * - Implements core:ui-imaging TelegramThumbFetcher interface
 * - Uses infra/transport-telegram TelegramTransportClient
 * - Wired via DI in TelegramImagingModule
 *
 * **Download Strategy:**
 * 1. Check if file is already downloaded (localPath available)
 * 2. If not, request download via TelegramTransportClient
 * 3. Wait for download completion (with timeout)
 * 4. Return SourceFetchResult with local file
 */
class CoilTelegramThumbFetcher(
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
            // Step 1: Request file resolution/download
            val tgFile =
                telegramClient.requestFileDownload(
                    fileId = ref.fileId,
                    priority = 24, // High priority for thumbnails
                )

            // Step 2: Check if already downloaded
            val initialPath = tgFile.localPath
            if (tgFile.isDownloadingCompleted && !initialPath.isNullOrBlank()) {
                return@withTimeout createResult(initialPath)
            }

            // Step 3: Poll for download completion
            var retries = 0
            while (retries < MAX_RETRIES) {
                kotlinx.coroutines.delay(RETRY_DELAY_MS)

                val updated = telegramClient.resolveFile(ref.fileId)
                val updatedPath = updated.localPath

                if (updated.isDownloadingCompleted && !updatedPath.isNullOrBlank()) {
                    return@withTimeout createResult(updatedPath)
                }

                if (!updated.isDownloadingActive && updatedPath.isNullOrBlank()) {
                    throw IOException(
                        "Telegram thumbnail download failed for fileId=${ref.fileId}",
                    )
                }

                retries++
            }

            throw IOException(
                "Telegram thumbnail download timeout for fileId=${ref.fileId}",
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

    /**
     * Factory for creating CoilTelegramThumbFetcher instances.
     *
     * Implements the core:ui-imaging TelegramThumbFetcher.Factory interface.
     */
    class Factory(
        private val telegramClient: TelegramTransportClient,
    ) : TelegramThumbFetcher.Factory {
        override fun create(
            ref: ImageRef.TelegramThumb,
            options: Options,
        ): TelegramThumbFetcher = CoilTelegramThumbFetcher(telegramClient, ref, options)
    }
}
