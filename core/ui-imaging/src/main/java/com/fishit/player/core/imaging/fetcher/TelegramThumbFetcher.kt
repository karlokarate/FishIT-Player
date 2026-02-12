package com.fishit.player.core.imaging.fetcher

import coil3.fetch.FetchResult
import coil3.request.Options
import com.fishit.player.core.model.ImageRef

/**
 * Fetcher interface for resolving [ImageRef.TelegramThumb] references.
 *
 * **Purpose:**
 * - Abstracts Telethon proxy file resolution from the imaging module
 * - Implemented in :infra:telegram-core with real Telethon proxy integration
 * - Allows :core:ui-imaging to remain free of Telethon proxy dependencies
 *
 * **Architecture:**
 * - :core:ui-imaging defines this interface
 * - :infra:telegram-core provides the implementation
 * - DI wires the implementation to GlobalImageLoader
 *
 * **Implementation Contract:**
 * 1. Resolve fileId via proxy file endpoint
 * 2. Wait for download completion (or use cached path)
 * 3. Return SourceFetchResult with the local file
 *
 * **Example Implementation (in :infra:telegram-core):**
 * ```kotlin
 * class TelegramThumbFetcherImpl(
 *     private val telegramClient: TelegramClient,
 * ) : TelegramThumbFetcher {
 *     override suspend fun fetch(ref: ImageRef.TelegramThumb, options: Options): FetchResult {
 *         val localPath = telegramClient.resolveFileLocation(ref.fileId)
 *             ?: telegramClient.requestFileDownload(ref.fileId)
 *             ?: throw IOException("Failed to download Telegram thumbnail")
 *
 *         return SourceFetchResult(
 *             source = ImageSource(File(localPath).source().buffer(), options.fileSystem),
 *             mimeType = "image/jpeg",
 *             dataSource = DataSource.DISK,
 *         )
 *     }
 * }
 * ```
 */
interface TelegramThumbFetcher {
    /**
     * Fetch the Telegram thumbnail for the given reference.
     *
     * @param ref TelegramThumb reference containing fileId and metadata
     * @param options Coil request options
     * @return FetchResult with the resolved image data
     * @throws IOException if the file cannot be resolved or downloaded
     */
    suspend fun fetch(
        ref: ImageRef.TelegramThumb,
        options: Options,
    ): FetchResult

    /**
     * Factory for creating [TelegramThumbFetcher] instances.
     *
     * Used by [ImageRefFetcher] to delegate TelegramThumb resolution.
     */
    interface Factory {
        /**
         * Create a fetcher for the given TelegramThumb reference.
         */
        fun create(
            ref: ImageRef.TelegramThumb,
            options: Options,
        ): TelegramThumbFetcher
    }
}

/**
 * Stub implementation that throws an error.
 *
 * Used when Telegram support is not configured.
 */
object NoOpTelegramThumbFetcher : TelegramThumbFetcher {
    override suspend fun fetch(
        ref: ImageRef.TelegramThumb,
        options: Options,
    ): FetchResult =
        throw UnsupportedOperationException(
            "Telegram thumbnail support not available. " +
                "Configure TelegramThumbFetcher.Factory in GlobalImageLoader to enable.",
        )

    /**
     * Factory that always returns [NoOpTelegramThumbFetcher].
     */
    object Factory : TelegramThumbFetcher.Factory {
        override fun create(
            ref: ImageRef.TelegramThumb,
            options: Options,
        ): TelegramThumbFetcher = NoOpTelegramThumbFetcher
    }
}
