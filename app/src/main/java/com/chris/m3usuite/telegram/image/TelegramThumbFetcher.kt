package com.chris.m3usuite.telegram.image

import android.content.Context
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.chris.m3usuite.core.logging.UnifiedLog
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File

/**
 * Coil 3 Fetcher for Telegram thumbnails using TelegramThumbKey (Requirement 4).
 *
 * **Features:**
 * - Lazy, on-demand thumbnail loading from TDLib
 * - Stable cache keys based on remoteId (not fileId)
 * - Concurrency limits to prevent TDLib overload during fast scrolling
 * - Zero-copy reading from TDLib's local file cache
 *
 * **Architecture:**
 * 1. Receives TelegramThumbKey with remoteId, kind, sizeBucket
 * 2. Resolves current fileId from repository mapping (remoteId → fileId)
 * 3. Downloads thumbnail from TDLib if not already cached locally
 * 4. Returns file path for Coil to cache in disk/memory
 *
 * **Cache Key Stability:**
 * - Cache key is based on remoteId (stable across sessions)
 * - fileId is only used internally for TDLib download
 * - Allows cache hits across app restarts
 *
 * **Concurrency:**
 * - Uses Semaphore(4) to limit concurrent TDLib downloads
 * - Prevents overload during fast scrolling through many thumbnails
 *
 * Register this fetcher in AppImageLoader via:
 * ```
 * ImageLoader.Builder(context)
 *     .components {
 *         add(TelegramThumbFetcher.Factory(context))
 *     }
 *     .build()
 * ```
 */
class TelegramThumbFetcher
    private constructor(
        private val key: TelegramThumbKey,
        private val context: Context,
        private val options: Options,
    ) : Fetcher {
        companion object {
            private const val TAG = "TelegramThumbFetcher"

            // Limit concurrent TDLib downloads to prevent overload
            private val downloadSemaphore = Semaphore(4)

            // Cached instances to avoid repeated initialization
            @Volatile
            private var cachedServiceClient: T_TelegramServiceClient? = null

            @Volatile
            private var cachedRepository: com.chris.m3usuite.data.repo.TelegramContentRepository? = null

            private fun getServiceClient(context: Context): T_TelegramServiceClient? {
                cachedServiceClient?.let { return it }
                return synchronized(this) {
                    cachedServiceClient ?: try {
                        T_TelegramServiceClient.getInstance(context).also {
                            if (it.isStarted) {
                                cachedServiceClient = it
                            }
                        }
                    } catch (e: Exception) {
                        UnifiedLog.warn(
                            TAG,
                            "Failed to get TelegramServiceClient",
                            mapOf("error" to (e.message ?: "unknown")),
                        )
                        null
                    }
                }
            }

            private fun getRepository(context: Context): com.chris.m3usuite.data.repo.TelegramContentRepository {
                cachedRepository?.let { return it }
                return synchronized(this) {
                    cachedRepository
                        ?: com.chris.m3usuite.data.repo
                            .TelegramContentRepository(
                                context,
                                com.chris.m3usuite.prefs
                                    .SettingsStore(context),
                            ).also { cachedRepository = it }
                }
            }
        }

        override suspend fun fetch(): FetchResult =
            withContext(Dispatchers.IO) {
                try {
                    // Get TelegramServiceClient
                    val serviceClient =
                        getServiceClient(context)
                            ?: throw IllegalStateException("TelegramServiceClient not available")

                    if (!serviceClient.isStarted) {
                        throw IllegalStateException("TelegramServiceClient not started")
                    }

                    // Resolve fileId from remoteId via repository
                    val repository = getRepository(context)

                    val fileId =
                        repository.resolveThumbFileId(
                            remoteId = key.remoteId,
                            kind = key.kind,
                            sizeBucket = key.sizeBucket,
                        ) ?: throw IllegalStateException("No fileId found for remoteId=${key.remoteId}")

                    // Acquire semaphore to limit concurrent downloads
                    downloadSemaphore.acquire()
                    try {
                        // Download thumbnail via T_TelegramFileDownloader
                        val downloader = serviceClient.downloader()
                        val localPath =
                            downloader.ensureFileReadyWithMp4Validation(
                                fileId = fileId,
                                remoteId = key.remoteId,
                                offset = 0L,
                            )

                        if (localPath.isBlank()) {
                            throw IllegalStateException("TDLib did not provide local path for fileId=$fileId")
                        }

                        val localFile = java.io.File(localPath)
                        if (!localFile.exists() || !localFile.canRead()) {
                            throw IllegalStateException("Local file not accessible: $localPath")
                        }

                        UnifiedLog.debug(
                            TAG,
                            "Thumbnail loaded: remoteId=${key.remoteId}, kind=${key.kind}, path=$localPath",
                        )

                        // Return file source for Coil
                        val okioPath = localFile.absolutePath.toPath()
                        val source =
                            ImageSource(
                                file = okioPath,
                                fileSystem = FileSystem.SYSTEM,
                                metadata = null,
                            )

                        SourceFetchResult(
                            source = source,
                            mimeType = guessMimeType(localPath),
                            dataSource = DataSource.DISK,
                        )
                    } finally {
                        downloadSemaphore.release()
                    }
                } catch (e: Exception) {
                    UnifiedLog.error(
                        source = TAG,
                        message = "Failed to fetch thumbnail: remoteId=${key.remoteId}, kind=${key.kind}",
                        exception = e,
                    )
                    throw e
                }
            }

        private fun guessMimeType(path: String): String? =
            when (path.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> null
            }

        /**
         * Factory for creating TelegramThumbFetcher instances.
         */
        class Factory(
            private val context: Context,
        ) : Fetcher.Factory<TelegramThumbKey> {
            override fun create(
                data: TelegramThumbKey,
                options: Options,
                imageLoader: ImageLoader,
            ): Fetcher = TelegramThumbFetcher(data, context, options)
        }
    }

/**
 * Keyer for TelegramThumbKey to generate stable cache keys.
 */
class TelegramThumbKeyer : Keyer<TelegramThumbKey> {
    override fun key(
        data: TelegramThumbKey,
        options: Options,
    ): String = data.toCacheKey()
}

/**
 * Legacy fetcher for Telegram local file paths.
 *
 * Handles file:// URIs and raw file paths that point to TDLib's cache directory.
 * This is for backward compatibility with existing code that uses file paths directly.
 */
class TelegramLocalFileFetcher(
    private val path: String,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val file = File(path)
        val okioPath = file.absolutePath.toPath()

        val source =
            ImageSource(
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

    private fun guessMimeType(path: String): String? =
        when (path.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> null
        }

    class Factory : Fetcher.Factory<String> {
        override fun create(
            data: String,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val isFilePath = data.startsWith("/") || data.startsWith("file://")
            if (!isFilePath) return null

            val path =
                if (data.startsWith("file://")) {
                    data.removePrefix("file://")
                } else {
                    data
                }

            val file = File(path)
            if (!file.exists() || !file.canRead()) return null

            return TelegramLocalFileFetcher(path, options)
        }
    }
}

/**
 * Extension to register Telegram fetchers in AppImageLoader.
 */
fun ImageLoader.Builder.supportTelegramThumbs(context: Context) =
    apply {
        components {
            // Register TelegramThumbKey fetcher (new, on-demand loading)
            add(TelegramThumbFetcher.Factory(context))
            // Register TelegramThumbKey keyer for stable cache keys
            add(TelegramThumbKeyer())
            // Register legacy local file fetcher for backward compatibility
            add(TelegramLocalFileFetcher.Factory())
        }
    }
