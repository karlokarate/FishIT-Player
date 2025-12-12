package com.fishit.player.infra.transport.telegram.imaging

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TelegramThumbFetcher
import com.fishit.player.infra.transport.telegram.TgThumbnailRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Telegram Thumbnail Fetcher Implementation (v2 Architecture).
 *
 * Fetches Telegram thumbnails for Coil image loading integration. Uses transport-layer file
 * downloads with remoteId-first fallback.
 *
 * **Key Behaviors (from legacy TelegramFileLoader):**
 * - Download thumbnails with medium priority
 * - RemoteId fallback for stale fileIds
 * - Bounded LRU set for failed remoteIds (prevents log spam)
 * - Bounded prefetch support for scroll-ahead
 *
 * **Thread Safety:**
 * - `failedRemoteIds` protected by Mutex for all access
 * - Prefetch limited to [MAX_PREFETCH_BATCH] items to prevent queue overflow
 *
 * **v2 Compliance:**
 * - Uses UnifiedLog for all logging
 * - No UI references
 * - Consumes TelegramFileClient interface
 *
 * @param fileClient The transport-layer file client (injected via DI)
 *
 * @see TelegramThumbFetcher interface this implements
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
class TelegramThumbFetcherImpl(
        private val fileClient: TelegramFileClient,
) : TelegramThumbFetcher {

    companion object {
        private const val TAG = "TelegramThumbFetcher"
        private const val DOWNLOAD_PRIORITY = 16 // Medium priority
        private const val PREFETCH_PRIORITY = 8 // Lower priority for prefetch
        private const val FETCH_TIMEOUT_SECONDS = 10L
        private const val MAX_FAILED_CACHE_SIZE = 500
        private const val MAX_PREFETCH_BATCH = 10 // Limit prefetch to prevent queue overflow
    }

    // Mutex for thread-safe access to failedRemoteIds
    private val failedMutex = Mutex()

    // Bounded LRU set of failed remoteIds to prevent repeated fetch attempts
    private val failedRemoteIds: MutableSet<String> =
            object : LinkedHashSet<String>() {
                override fun add(element: String): Boolean {
                    val added = super.add(element)
                    // Evict oldest entries if over capacity
                    while (size > MAX_FAILED_CACHE_SIZE) {
                        iterator().apply {
                            if (hasNext()) {
                                next()
                                remove()
                            }
                        }
                    }
                    return added
                }
            }

    // ========== TelegramThumbFetcher Implementation ==========

    override suspend fun fetchThumbnail(thumbRef: TgThumbnailRef): String? {
        // Skip if already known to fail (thread-safe check)
        if (isKnownFailed(thumbRef.remoteId)) {
            return null
        }

        // Check if already cached
        val cached = isCachedInternal(thumbRef.fileId)
        if (cached != null) {
            return cached
        }

        // Try download with current fileId
        var localPath = tryDownload(thumbRef.fileId)
        if (localPath != null) {
            return localPath
        }

        // FileId might be stale - try resolving via remoteId
        if (thumbRef.remoteId.isNotEmpty()) {
            val resolved = fileClient.resolveRemoteId(thumbRef.remoteId)
            if (resolved != null && resolved.id != thumbRef.fileId) {
                UnifiedLog.d(TAG, "Resolved stale fileId ${thumbRef.fileId} → ${resolved.id}")
                localPath = tryDownload(resolved.id)
                if (localPath != null) {
                    return localPath
                }
            }
        }

        // All attempts failed - add to failed cache (thread-safe)
        if (thumbRef.remoteId.isNotEmpty()) {
            markAsFailed(thumbRef.remoteId)
        }

        return null
    }

    override suspend fun isCached(thumbRef: TgThumbnailRef): Boolean {
        return isCachedInternal(thumbRef.fileId) != null
    }

    /**
     * Prefetch thumbnails with bounded queue.
     *
     * Only the first [MAX_PREFETCH_BATCH] items are prefetched to prevent overwhelming the download
     * queue and consuming excessive memory.
     */
    override suspend fun prefetch(thumbRefs: List<TgThumbnailRef>) {
        var prefetchCount = 0

        for (ref in thumbRefs) {
            if (prefetchCount >= MAX_PREFETCH_BATCH) {
                UnifiedLog.d(
                        TAG,
                        "Prefetch limit reached ($MAX_PREFETCH_BATCH), skipping remaining ${thumbRefs.size - prefetchCount}"
                )
                break
            }

            // Skip known failures (thread-safe check)
            if (isKnownFailed(ref.remoteId)) continue

            // Skip already cached
            if (isCachedInternal(ref.fileId) != null) continue

            // Start low-priority download (don't wait for completion)
            try {
                fileClient.startDownload(
                        fileId = ref.fileId,
                        priority = PREFETCH_PRIORITY,
                        offset = 0,
                        limit = 0,
                )
                prefetchCount++
            } catch (e: Exception) {
                // Ignore prefetch errors
            }
        }

        if (prefetchCount > 0) {
            UnifiedLog.d(TAG, "Prefetch started for $prefetchCount thumbnails")
        }
    }

    override suspend fun clearFailedCache() {
        failedMutex.withLock { failedRemoteIds.clear() }
        UnifiedLog.d(TAG, "Failed cache cleared")
    }

    // ========== Thread-Safe Failed Cache Access ==========

    private suspend fun isKnownFailed(remoteId: String): Boolean {
        if (remoteId.isEmpty()) return false
        return failedMutex.withLock { remoteId in failedRemoteIds }
    }

    private suspend fun markAsFailed(remoteId: String) {
        failedMutex.withLock { failedRemoteIds.add(remoteId) }
    }

    // ========== Internal Methods ==========

    private suspend fun isCachedInternal(fileId: Int): String? {
        val file = fileClient.getFile(fileId) ?: return null
        return if (file.isDownloadingCompleted && file.localPath != null) {
            file.localPath
        } else {
            null
        }
    }

    private suspend fun tryDownload(fileId: Int): String? {
        return try {
            // Start download
            fileClient.startDownload(
                    fileId = fileId,
                    priority = DOWNLOAD_PRIORITY,
                    offset = 0,
                    limit = 0,
            )

            // Wait for completion
            withTimeoutOrNull(FETCH_TIMEOUT_SECONDS * 1000) { waitForCompletion(fileId) }
        } catch (e: Exception) {
            UnifiedLog.w(TAG, "Download failed for fileId=$fileId: ${e.message}")
            null
        }
    }

    private suspend fun waitForCompletion(fileId: Int): String? {
        // Poll for completion
        repeat(100) { // Max 10 seconds with 100ms intervals
            val file = fileClient.getFile(fileId)
            if (file?.isDownloadingCompleted == true && file.localPath != null) {
                return file.localPath
            }
            delay(100)
        }
        return null
    }
}
