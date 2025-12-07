package com.fishit.player.core.imaging.fetcher

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.fishit.player.core.model.ImageRef
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source

/**
 * Coil 3 Fetcher for [ImageRef] sealed interface.
 *
 * Routes each ImageRef variant to the appropriate resolution strategy:
 * - [ImageRef.Http] → OkHttp request
 * - [ImageRef.TelegramThumb] → Delegates to [TelegramThumbFetcher]
 * - [ImageRef.LocalFile] → File read
 *
 * **Architecture:**
 * - This is the main entry point for all ImageRef fetching
 * - Registered with Coil ImageLoader via [Factory]
 * - Handles caching keys and size hints
 *
 * **Caching:**
 * - Uses [ImageRefKeyer] to generate stable cache keys
 * - TelegramThumb uses `uniqueId` for cross-session stability
 * - Http uses URL as cache key
 * - LocalFile uses absolute path as cache key
 */
class ImageRefFetcher(
        private val imageRef: ImageRef,
        private val options: Options,
        private val okHttpClient: OkHttpClient,
        private val telegramThumbFetcher: TelegramThumbFetcher.Factory?,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        return when (imageRef) {
            is ImageRef.Http -> fetchHttp(imageRef)
            is ImageRef.TelegramThumb -> fetchTelegramThumb(imageRef)
            is ImageRef.LocalFile -> fetchLocalFile(imageRef)
            is ImageRef.InlineBytes -> fetchInlineBytes(imageRef)
        }
    }

    /** Fetch HTTP/HTTPS image via OkHttp. */
    private suspend fun fetchHttp(ref: ImageRef.Http): FetchResult {
        val requestBuilder = Request.Builder().url(ref.url)

        // Add custom headers if present
        ref.headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        val request = requestBuilder.build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body

        return SourceFetchResult(
                source =
                        ImageSource(
                                source = body.source(),
                                fileSystem = options.fileSystem,
                        ),
                mimeType = response.header("Content-Type"),
                dataSource = DataSource.NETWORK,
        )
    }

    /** Fetch Telegram thumbnail via [TelegramThumbFetcher]. */
    private suspend fun fetchTelegramThumb(ref: ImageRef.TelegramThumb): FetchResult {
        val factory =
                telegramThumbFetcher
                        ?: throw IllegalStateException(
                                "TelegramThumbFetcher.Factory not configured. " +
                                        "Pass it to GlobalImageLoader.create() to enable Telegram thumbnail support."
                        )

        val fetcher = factory.create(ref, options)
        return fetcher.fetch(ref, options)
    }

    /** Fetch local file from disk. */
    private suspend fun fetchLocalFile(ref: ImageRef.LocalFile): FetchResult {
        val file = File(ref.path)

        if (!file.exists()) {
            throw IOException("File not found: ${ref.path}")
        }

        if (!file.canRead()) {
            throw IOException("Cannot read file: ${ref.path}")
        }

        return SourceFetchResult(
                source =
                        ImageSource(
                                source = file.inputStream().source().buffer(),
                                fileSystem = options.fileSystem,
                        ),
                mimeType = guessMimeType(ref.path),
                dataSource = DataSource.DISK,
        )
    }

    /**
     * Fetch inline bytes directly from memory.
     *
     * Used for TDLib minithumbnails (~40px inline JPEGs) that are embedded in the message payload.
     * These are decoded instantly without network - perfect for blur placeholders.
     */
    private suspend fun fetchInlineBytes(ref: ImageRef.InlineBytes): FetchResult {
        if (ref.bytes.isEmpty()) {
            throw IOException("Empty inline bytes")
        }

        return SourceFetchResult(
                source =
                        ImageSource(
                                source = okio.Buffer().write(ref.bytes),
                                fileSystem = options.fileSystem,
                        ),
                mimeType = ref.mimeType,
                dataSource = DataSource.MEMORY, // Already in memory - instant
        )
    }

    /** Guess MIME type from file extension. */
    private fun guessMimeType(path: String): String? {
        val extension = path.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "avif" -> "image/avif"
            else -> null
        }
    }

    /**
     * Factory for [ImageRefFetcher].
     *
     * Registered with Coil ImageLoader to handle [ImageRef] types.
     */
    class Factory(
            private val okHttpClient: OkHttpClient,
            private val telegramThumbFetcher: TelegramThumbFetcher.Factory?,
    ) : Fetcher.Factory<ImageRef> {

        override fun create(
                data: ImageRef,
                options: Options,
                imageLoader: ImageLoader,
        ): Fetcher {
            return ImageRefFetcher(
                    imageRef = data,
                    options = options,
                    okHttpClient = okHttpClient,
                    telegramThumbFetcher = telegramThumbFetcher,
            )
        }
    }
}

/**
 * Keyer for [ImageRef] to generate stable cache keys.
 *
 * **Key Strategy:**
 * - [ImageRef.Http]: Uses URL (consistent with standard Coil behavior)
 * - [ImageRef.TelegramThumb]: Uses `uniqueId` for cross-session stability (fileId may change
 * between sessions, uniqueId is stable)
 * - [ImageRef.LocalFile]: Uses absolute path
 * - [ImageRef.InlineBytes]: Uses content hash for deduplication
 *
 * Register with ImageLoader:
 * ```kotlin
 * ImageLoader.Builder(context)
 *     .components {
 *         add(ImageRefKeyer())
 *         add(ImageRefFetcher.Factory(...))
 *     }
 *     .build()
 * ```
 */
class ImageRefKeyer : Keyer<ImageRef> {
    override fun key(data: ImageRef, options: Options): String {
        return when (data) {
            is ImageRef.Http -> data.url
            is ImageRef.TelegramThumb -> "tg:${data.uniqueId}"
            is ImageRef.LocalFile -> "file:${data.path}"
            is ImageRef.InlineBytes -> "inline:${data.bytes.contentHashCode()}"
        }
    }
}
