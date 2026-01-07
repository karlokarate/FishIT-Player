package com.fishit.player.core.imaging.fetcher

import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.fishit.player.core.imaging.fetcher.TelegramThumbFetcher
import com.fishit.player.core.model.ImageRef
import com.fishit.player.infra.transport.telegram.TgThumbnailRef
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import com.fishit.player.infra.transport.telegram.TelegramThumbFetcher as TransportThumbFetcher

/**
 * Coil TelegramThumbFetcher implementation using typed transport interface.
 *
 * **Purpose:**
 * - Bridges Coil's [TelegramThumbFetcher] interface with transport layer's [TransportThumbFetcher]
 * - Converts [ImageRef.TelegramThumb] to [TgThumbnailRef] for transport layer
 * - Returns local file path for Coil to decode
 *
 * **Architecture:**
 * - core:ui-imaging defines the [TelegramThumbFetcher] interface (Coil-facing)
 * - infra:transport-telegram provides [TransportThumbFetcher] (TDLib-facing)
 * - This implementation bridges the two
 *
 * **v2 Migration:**
 * - No longer uses deprecated TelegramTransportClient
 * - Uses typed TransportThumbFetcher which internally uses TelegramFileClient
 *
 * **Note:**
 * This is different from [TelegramThumbFetcherImpl] in the same package, which is the
 * transport-layer implementation. This class is specifically for Coil integration.
 */
class CoilTelegramThumbFetcherImpl(
    private val transportFetcher: TransportThumbFetcher,
    private val ref: ImageRef.TelegramThumb,
    private val options: Options,
) : TelegramThumbFetcher {
    override suspend fun fetch(
        ref: ImageRef.TelegramThumb,
        options: Options,
    ): FetchResult {
        // Convert ImageRef.TelegramThumb to TgThumbnailRef for transport layer
        val thumbRef =
            TgThumbnailRef(
                remoteId = ref.remoteId,
                width = ref.preferredWidth ?: 320,
                height = ref.preferredHeight ?: 320,
            )

        // Use transport layer to fetch thumbnail
        val localPath =
            transportFetcher.fetchThumbnail(thumbRef)
                ?: throw IOException(
                    "Failed to fetch Telegram thumbnail for remoteId=${ref.remoteId}",
                )

        return createResult(localPath)
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

    /** Factory for creating [CoilTelegramThumbFetcherImpl] instances. */
    class Factory(
        private val transportFetcher: TransportThumbFetcher,
    ) : TelegramThumbFetcher.Factory {
        override fun create(
            ref: ImageRef.TelegramThumb,
            options: Options,
        ): TelegramThumbFetcher = CoilTelegramThumbFetcherImpl(transportFetcher, ref, options)
    }
}
