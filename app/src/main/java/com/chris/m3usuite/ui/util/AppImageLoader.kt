package com.chris.m3usuite.ui.util

import android.content.Context
import android.os.Build
import android.os.StatFs
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.chris.m3usuite.core.http.HttpClientFactory
import com.chris.m3usuite.prefs.SettingsStore
import okio.Path.Companion.toPath

/**
 * Global ImageLoader for Coil 3 with tuned caches.
 *
 * - Disk cache: 256 MiB under app cache dir
 * - Memory cache: up to 25% of available memory
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
        val disk = DiskCache.Builder()
            .directory(imageCacheDir.absolutePath.toPath())
            .maxSizeBytes(computeCoilDiskCacheSizeBytes(app, imageCacheDir))
            .build()

        val mem = MemoryCache.Builder()
            .maxSizePercent(app, 0.25) // ~25% of available memory
            .build()

        val loader = ImageLoader.Builder(app)
            // Memory & disk caches
            .memoryCache { mem }
            .diskCache { disk }
            // Prefer hardware bitmaps globally unless a request opts out
            .apply { /* Request-level allowHardware(true) is set in Images.kt */ }
            .build()

        cached = loader
        return loader
    }
}

private fun computeCoilDiskCacheSizeBytes(context: Context, dir: java.io.File): Long {
    val MB = 1024L * 1024L
    val is64 = try { Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() } catch (_: Throwable) { false }
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
