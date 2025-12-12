package com.fishit.player.infra.transport.telegram.imaging

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TelegramThumbFetcher
import com.fishit.player.infra.transport.telegram.TgFileUpdate
import com.fishit.player.infra.transport.telegram.TgThumbnailRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

/**
 * Telegram Thumbnail Fetcher Implementation (v2 Architecture).
 *
 * Fetches Telegram thumbnails for Coil image loading integration.
 * Uses transport-layer file downloads with remoteId-first fallback.
 *
 * **Key Behaviors (from legacy TelegramFileLoader):**
 * - Download thumbnails with medium priority
 * - RemoteId fallback for stale fileIds
 * - Bounded LRU set for failed remoteIds (prevents log spam)
 * - Prefetch support for scroll-ahead
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
    private val fileClient: TelegramFileClient
) : TelegramThumbFetcher {

    companion object {
        private const val TAG = "TelegramThumbFetcher"
        private const val DOWNLOAD_PRIORITY = 16 // Medium priority
        private const val FETCH_TIMEOUT_SECONDS = 10L
        private const val MAX_FAILED_CACHE_SIZE = 500
    }

    // Bounded LRU set of failed remoteIds to prevent repeated fetch attempts
    private val failedRemoteIds: MutableSet<String> = Collections.newSetFromMap(
        object : LinkedHashMap<String, Boolean>(MAX_FAILED_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > MAX_FAILED_CACHE_SIZE
            }
        }
    )

    // ========== TelegramThumbFetcher Implementation ==========

    override suspend fun fetchThumbnail(thumbRef: TgThumbnailRef): String? {
        // Skip if already known to fail
        if (thumbRef.remoteId in failedRemoteIds) {
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

        // All attempts failed - add to failed cache
        if (thumbRef.remoteId.isNotEmpty()) {
            synchronized(failedRemoteIds) {
                failedRemoteIds.add(thumbRef.remoteId)
            }
        }

        return null
    }

    override suspend fun isCached(thumbRef: TgThumbnailRef): Boolean {
        return isCachedInternal(thumbRef.fileId) != null
    }

    override suspend fun prefetch(thumbRefs: List<TgThumbnailRef>) {
        for (ref in thumbRefs) {
            // Skip known failures
            if (ref.remoteId in failedRemoteIds) continue

            // Skip already cached
            if (isCachedInternal(ref.fileId) != null) continue

            // Start low-priority download (don't wait for completion)
            try {
                fileClient.startDownload(
                    fileId = ref.fileId,
                    priority = DOWNLOAD_PRIORITY / 2, // Lower priority for prefetch
                    offset = 0,
                    limit = 0
                )
            } catch (e: Exception) {
                // Ignore prefetch errors
            }
        }
    }

    override fun clearFailedCache() {
        synchronized(failedRemoteIds) {
            failedRemoteIds.clear()
        }
        UnifiedLog.d(TAG, "Failed cache cleared")
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
                limit = 0
            )

            // Wait for completion
            withTimeoutOrNull(FETCH_TIMEOUT_SECONDS * 1000) {
                waitForCompletion(fileId)
            }
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
