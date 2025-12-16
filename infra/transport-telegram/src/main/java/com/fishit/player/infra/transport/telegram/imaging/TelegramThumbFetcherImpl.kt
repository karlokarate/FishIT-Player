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
 * Telegram Thumbnail Fetcher Implementation (v2 remoteId-First Architecture).
 *
 * Fetches Telegram thumbnails for Coil image loading integration.
 *
 * ## remoteId-First Design
 *
 * This implementation follows the **remoteId-first architecture** defined in
 * `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`.
 *
 * ### Key Behaviors:
 * - **Always resolve remoteId → fileId** via `getRemoteFile(remoteId)` first
 * - No fileId stored in persistence (volatile, session-local)
 * - Bounded LRU set for failed remoteIds (prevents log spam)
 * - Bounded prefetch support for scroll-ahead
 *
 * ### Resolution Flow:
 * 1. Check if remoteId is in failed cache → skip
 * 2. Resolve `remoteId` → `fileId` via `getRemoteFile(remoteId)`
 * 3. Check if file already downloaded (TDLib cache)
 * 4. If not, download with medium priority
 * 5. Return local path
 *
 * **Thread Safety:**
 * - `failedRemoteIds` protected by Mutex for all access
 * - Prefetch limited to [MAX_PREFETCH_BATCH] items to prevent queue overflow
 *
 * @param fileClient The transport-layer file client (injected via DI)
 *
 * @see TelegramThumbFetcher interface this implements
 * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
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
        val remoteId = thumbRef.remoteId
        
        // Skip empty remoteId
        if (remoteId.isBlank()) {
            UnifiedLog.w(TAG, "Empty remoteId in thumbnail reference")
            return null
        }
        
        // Skip if already known to fail (thread-safe check)
        if (isKnownFailed(remoteId)) {
            return null
        }

        // Step 1: Resolve remoteId → fileId via getRemoteFile()
        val resolved = fileClient.resolveRemoteId(remoteId)
        if (resolved == null) {
            UnifiedLog.w(TAG, "Failed to resolve remoteId: $remoteId")
            markAsFailed(remoteId)
            return null
        }
        
        val fileId = resolved.id
        UnifiedLog.d(TAG, "Resolved remoteId → fileId: $remoteId → $fileId")

        // Step 2: Check if already cached in TDLib
        val cached = isCachedInternal(fileId)
        if (cached != null) {
            return cached
        }

        // Step 3: Download with medium priority
        val localPath = tryDownload(fileId)
        if (localPath != null) {
            return localPath
        }

        // All attempts failed - add to failed cache (thread-safe)
        markAsFailed(remoteId)
        return null
    }

    override suspend fun isCached(thumbRef: TgThumbnailRef): Boolean {
        val remoteId = thumbRef.remoteId
        if (remoteId.isBlank()) return false
        
        // Resolve remoteId → fileId first
        val resolved = fileClient.resolveRemoteId(remoteId) ?: return false
        return isCachedInternal(resolved.id) != null
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

            val remoteId = ref.remoteId
            if (remoteId.isBlank()) continue

            // Skip known failures (thread-safe check)
            if (isKnownFailed(remoteId)) continue

            // Resolve remoteId → fileId
            val resolved = fileClient.resolveRemoteId(remoteId) ?: continue
            val fileId = resolved.id

            // Skip already cached
            if (isCachedInternal(fileId) != null) continue

            // Start low-priority download (don't wait for completion)
            try {
                fileClient.startDownload(
                        fileId = fileId,
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
