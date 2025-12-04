package com.chris.m3usuite.ui.util

import android.content.Context
import android.os.Build
import android.os.StatFs
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

/**
 * Global ImageLoader for Coil 3 with tuned caches.
 *
 * - Disk cache: 256 MiB under app cache dir
 * - Memory cache: up to 25% of available memory
 * - Telegram thumbnail support via TelegramThumbFetcher (Requirement 4)
 *
 * Network headers are attached per-request via buildImageRequest() to keep parity
 * with the app's OkHttp configuration; we avoid hard-coupling to specific
 * fetcher dependencies to keep compile stable across Coil updates.
 */
object AppImageLoader {
    @Volatile private var cached: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        cached?.let { return it }
        val app = context.applicationContext

        val imageCacheDir = java.io.File(app.cacheDir, "image_cache").apply { mkdirs() }
        val disk =
            DiskCache
                .Builder()
                .directory(imageCacheDir.absolutePath.toPath())
                .maxSizeBytes(computeCoilDiskCacheSizeBytes(app, imageCacheDir))
                .build()

        val mem =
            MemoryCache
                .Builder()
                .maxSizePercent(app, 0.25) // ~25% of available memory
                .build()

        val loader =
            ImageLoader
                .Builder(app)
                // Memory & disk caches
                .memoryCache { mem }
                .diskCache { disk }
                // Register Telegram thumbnail fetcher (Requirement 4)
                .components {
                    // Register TelegramThumbKey fetcher for on-demand loading
                    add(
                        com.chris.m3usuite.telegram.image.TelegramThumbFetcher
                            .Factory(app),
                    )
                    // Register TelegramThumbKey keyer for stable cache keys
                    add(
                        com.chris.m3usuite.telegram.image
                            .TelegramThumbKeyer(),
                    )
                    // Register legacy local file fetcher for backward compatibility
                    add(
                        com.chris.m3usuite.telegram.image.TelegramLocalFileFetcher
                            .Factory(),
                    )
                }
                // Prefer hardware bitmaps globally unless a request opts out
                .apply { /* Request-level allowHardware(true) is set in Images.kt */ }
                .build()

        cached = loader
        return loader
    }

    suspend fun preload(
        context: Context,
        urls: Collection<Any?>,
        headers: ImageHeaders? = null,
        limit: Int = 8,
    ) {
        if (urls.isEmpty()) return
        val app = context.applicationContext
        val loader = get(app)
        val candidates =
            urls
                .asSequence()
                .mapNotNull { candidate -> normalizePreloadCandidate(candidate) }
                .distinct()
                .take(limit)
                .toList()
        if (candidates.isEmpty()) return

        withContext(Dispatchers.IO) {
            for (data in candidates) {
                runCatching {
                    val request =
                        buildImageRequest(
                            ctx = app,
                            url = data,
                            crossfade = false,
                            headers = headers,
                            preferRgb565 = true,
                        )
                    loader.execute(request)
                }
            }
        }
    }

    private fun normalizePreloadCandidate(candidate: Any?): Any? {
        when (candidate) {
            null -> return null
            is String -> {
                val trimmed = candidate.trim()
                if (trimmed.isEmpty()) return null
                val lower = trimmed.lowercase()
                return when {
                    lower.startsWith("data:") -> null
                    lower.startsWith("http://") -> trimmed
                    lower.startsWith("https://") -> trimmed
                    lower.startsWith("file://") -> trimmed
                    lower.startsWith("content://") -> trimmed
                    else -> null
                }
            }
            is android.net.Uri -> {
                val scheme = candidate.scheme?.lowercase()
                return when (scheme) {
                    "http", "https", "file", "content" -> candidate
                    else -> null
                }
            }
            else -> return candidate
        }
    }
}

private fun computeCoilDiskCacheSizeBytes(
    context: Context,
    dir: java.io.File,
): Long {
    val MB = 1024L * 1024L
    val is64 =
        try {
            Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    // Base and caps tuned for image workloads
    val baseMiB = if (is64) 512 else 256
    val capMiB = if (is64) 768 else 384

    // 2% of available space on the cache filesystem
    val stat = runCatching { StatFs(dir.absolutePath) }.getOrNull()
    val availBytes = stat?.availableBytes ?: 0L
    val twoPercent = (availBytes / 50L).coerceAtLeast(0L)

    val minBytes = baseMiB * MB
    val maxBytes = capMiB * MB
    return minOf(maxBytes, maxOf(minBytes, twoPercent))
}
