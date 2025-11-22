package com.chris.m3usuite.telegram.image

import android.content.Context
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File

/**
 * Coil 3 Fetcher for Telegram local file paths (Requirement 4).
 *
 * Enables zero-copy thumbnail loading by reading directly from TDLib's
 * local file cache without intermediate copies.
 *
 * Handles file:// URIs and raw file paths that point to TDLib's cache directory.
 * Uses Coil's built-in FileSystem for efficient streaming.
 *
 * Register this fetcher in the ImageLoader via:
 * ```
 * ImageLoader.Builder(context)
 *     .components {
 *         add(TelegramThumbFetcher.Factory())
 *     }
 *     .build()
 * ```
 */
class TelegramThumbFetcher(
    private val path: String,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        // Convert path to Okio Path
        val file = File(path)
        val okioPath = file.absolutePath.toPath()
        
        // Create ImageSource from file
        val source = ImageSource(
            file = okioPath,
            fileSystem = FileSystem.SYSTEM,
            metadata = null,
        )
        
        return SourceFetchResult(
            source = source,
            mimeType = guessMimeType(path),
            dataSource = DataSource.DISK,
        )
    }

    private fun guessMimeType(path: String): String? {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> null
        }
    }

    /**
     * Factory for creating TelegramThumbFetcher instances.
     * Coil will use this to check if a request can be handled by this fetcher.
     */
    class Factory : Fetcher.Factory<String> {
        override fun create(
            data: String,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            // Check if this is a file path (starts with / or file://)
            val isFilePath = data.startsWith("/") || data.startsWith("file://")
            if (!isFilePath) return null
            
            // Extract actual path
            val path = if (data.startsWith("file://")) {
                data.removePrefix("file://")
            } else {
                data
            }
            
            // Verify file exists
            val file = File(path)
            if (!file.exists() || !file.canRead()) return null
            
            return TelegramThumbFetcher(path, options)
        }
    }
}

/**
 * Extension to register TelegramThumbFetcher in AppImageLoader.
 * Call this when building the ImageLoader to enable Telegram thumbnail support.
 */
fun ImageLoader.Builder.supportTelegramThumbs() = apply {
    components {
        add(TelegramThumbFetcher.Factory())
    }
}
