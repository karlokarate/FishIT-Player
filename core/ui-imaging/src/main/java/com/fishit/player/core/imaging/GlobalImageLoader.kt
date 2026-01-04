package com.fishit.player.core.imaging

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.fishit.player.core.imaging.fetcher.ImageRefFetcher
import com.fishit.player.core.imaging.fetcher.ImageRefKeyer
import com.fishit.player.core.imaging.fetcher.TelegramThumbFetcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Global ImageLoader configuration for FishIT-Player v2.
 *
 * **Purpose:**
 * - Centralized Coil 3 ImageLoader with all custom fetchers
 * - Shared OkHttpClient for efficient HTTP connections
 * - Optimized cache configuration for TV/mobile
 *
 * **Architecture:**
 * - Only module in the app that depends on Coil
 * - Provides ImageLoader via dependency injection
 * - Custom fetchers for each ImageRef variant
 *
 * **Usage:**
 * ```kotlin
 * // In Application.onCreate() or DI setup:
 * val imageLoader = GlobalImageLoader.create(
 *     context = applicationContext,
 *     okHttpClient = sharedOkHttpClient,
 *     telegramThumbFetcher = telegramThumbFetcher,
 * )
 *
 * // In Compose:
 * CompositionLocalProvider(LocalImageLoader provides imageLoader) {
 *     // App content
 * }
 * ```
 */
object GlobalImageLoader {
    /** Default cache sizes optimized for TV/mobile hybrid. */
    object CacheConfig {
        /** Memory cache: 25% of available heap (Coil default) */
        const val MEMORY_CACHE_PERCENT = 0.25

        /** Disk cache size: 512 MB (matching v1 behavior) */
        const val DISK_CACHE_SIZE_BYTES = 512L * 1024 * 1024

        /** Disk cache directory name */
        const val DISK_CACHE_DIR = "image_cache"
    }

    /** OkHttp configuration for image fetching. */
    object HttpConfig {
        /** Connection timeout (seconds) */
        const val CONNECT_TIMEOUT_SECONDS = 15L

        /** Read timeout (seconds) */
        const val READ_TIMEOUT_SECONDS = 30L

        /** Write timeout (seconds) */
        const val WRITE_TIMEOUT_SECONDS = 30L

        /** Max concurrent requests per host */
        const val MAX_REQUESTS_PER_HOST = 4

        /** Max total concurrent requests */
        const val MAX_REQUESTS = 16
    }

    /**
     * Create a configured ImageLoader instance.
     *
     * @param context Application context
     * @param okHttpClient Optional shared OkHttpClient (creates default if null)
     * @param telegramThumbFetcher Factory for resolving TelegramThumb ImageRefs
     * @param enableCrossfade Enable crossfade animations (default true, disable on TV for
     * performance)
     * @param crossfadeDurationMs Crossfade duration (default 200ms)
     */
    fun create(
        context: Context,
        okHttpClient: OkHttpClient? = null,
        telegramThumbFetcher: TelegramThumbFetcher.Factory? = null,
        enableCrossfade: Boolean = true,
        crossfadeDurationMs: Int = 200,
    ): ImageLoader {
        val client = okHttpClient ?: createDefaultOkHttpClient()

        return ImageLoader
            .Builder(context)
            // Memory cache (25% of heap)
            .memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, CacheConfig.MEMORY_CACHE_PERCENT)
                    .build()
            }
            // Disk cache (512 MB)
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve(CacheConfig.DISK_CACHE_DIR))
                    .maxSizeBytes(CacheConfig.DISK_CACHE_SIZE_BYTES)
                    .build()
            }
            // Default policies
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            // Crossfade (disabled on TV for perf)
            .apply {
                if (enableCrossfade) {
                    crossfade(crossfadeDurationMs)
                }
            }
            // Custom fetchers and keyer
            .components {
                // Keyer for stable cache keys (uses uniqueId for TelegramThumb)
                add(ImageRefKeyer())
                // ImageRef fetcher (handles all ImageRef variants)
                add(ImageRefFetcher.Factory(client, telegramThumbFetcher))
            }.build()
    }

    /** Create a default OkHttpClient optimized for image loading. */
    fun createDefaultOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(HttpConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(HttpConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(HttpConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Configure dispatcher
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = HttpConfig.MAX_REQUESTS
                    maxRequestsPerHost = HttpConfig.MAX_REQUESTS_PER_HOST
                },
            ).build()

    /** Builder for easier configuration with Kotlin DSL. */
    class Builder(
        private val context: Context,
    ) {
        private var okHttpClient: OkHttpClient? = null
        private var telegramThumbFetcher: TelegramThumbFetcher.Factory? = null
        private var enableCrossfade: Boolean = true
        private var crossfadeDurationMs: Int = 200

        fun okHttpClient(client: OkHttpClient) = apply { okHttpClient = client }

        fun telegramThumbFetcher(factory: TelegramThumbFetcher.Factory) =
            apply {
                telegramThumbFetcher = factory
            }

        fun enableCrossfade(enable: Boolean) = apply { enableCrossfade = enable }

        fun crossfadeDurationMs(duration: Int) = apply { crossfadeDurationMs = duration }

        /** Disable crossfade (recommended for TV). */
        fun disableCrossfadeForTv() = apply { enableCrossfade = false }

        fun build(): ImageLoader =
            create(
                context = context,
                okHttpClient = okHttpClient,
                telegramThumbFetcher = telegramThumbFetcher,
                enableCrossfade = enableCrossfade,
                crossfadeDurationMs = crossfadeDurationMs,
            )
    }

    /**
     * Compute optimal disk cache size based on device capabilities.
     *
     * Ported from v1 AppImageLoader - considers:
     * - 64-bit vs 32-bit device (larger base for 64-bit)
     * - Available storage space (2% of available)
     * - Min/max bounds to prevent extreme values
     *
     * @param cacheDir Directory for the cache
     * @return Optimal cache size in bytes
     */
    fun computeDynamicDiskCacheSize(cacheDir: java.io.File): Long {
        val MB = 1024L * 1024L
        val is64Bit =
            try {
                android.os.Build.SUPPORTED_64_BIT_ABIS
                    .isNotEmpty()
            } catch (_: Throwable) {
                false
            }

        // Base and caps tuned for image workloads
        val baseMiB = if (is64Bit) 512 else 256
        val capMiB = if (is64Bit) 768 else 384

        // 2% of available space on the cache filesystem
        val stat = runCatching { android.os.StatFs(cacheDir.absolutePath) }.getOrNull()
        val availBytes = stat?.availableBytes ?: 0L
        val twoPercent = (availBytes / 50L).coerceAtLeast(0L)

        val minBytes = baseMiB * MB
        val maxBytes = capMiB * MB
        return minOf(maxBytes, maxOf(minBytes, twoPercent))
    }

    /**
     * Create ImageLoader with dynamic cache sizing.
     *
     * Uses [computeDynamicDiskCacheSize] to determine optimal disk cache size based on device
     * capabilities instead of a fixed 512 MB.
     */
    fun createWithDynamicCache(
        context: Context,
        okHttpClient: OkHttpClient? = null,
        telegramThumbFetcher: TelegramThumbFetcher.Factory? = null,
        enableCrossfade: Boolean = true,
        crossfadeDurationMs: Int = 200,
    ): ImageLoader {
        val client = okHttpClient ?: createDefaultOkHttpClient()
        val cacheDir = context.cacheDir.resolve(CacheConfig.DISK_CACHE_DIR).apply { mkdirs() }
        val dynamicCacheSize = computeDynamicDiskCacheSize(cacheDir)

        return ImageLoader
            .Builder(context)
            .memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, CacheConfig.MEMORY_CACHE_PERCENT)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir)
                    .maxSizeBytes(dynamicCacheSize)
                    .build()
            }.memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .apply {
                if (enableCrossfade) {
                    crossfade(crossfadeDurationMs)
                }
            }.components {
                add(ImageRefKeyer())
                add(ImageRefFetcher.Factory(client, telegramThumbFetcher))
            }.build()
    }
}
