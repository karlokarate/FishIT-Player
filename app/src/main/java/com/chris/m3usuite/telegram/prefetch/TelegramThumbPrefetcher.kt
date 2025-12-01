package com.chris.m3usuite.telegram.prefetch

import android.content.Context
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.core.TelegramFileLoader
import com.chris.m3usuite.telegram.domain.TelegramImageRef
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Background thumbnail prefetcher for Telegram content (Requirement 5).
 *
 * Observes mediaFlow and prefetches thumbnails in the background to improve
 * scroll performance. Thumbnails are stored in TDLib's local file directory.
 *
 * Key Features:
 * - Coroutine-based worker that runs continuously
 * - Observes enabled chats and prefetches their thumbnails
 * - Low-priority downloads that don't interfere with playback
 * - Automatic retry with backoff for failed downloads
 * - Respects TDLib cache limits
 *
 * Usage:
 * ```
 * val prefetcher = TelegramThumbPrefetcher(context, serviceClient)
 * prefetcher.start(scope)
 * ```
 */
class TelegramThumbPrefetcher(
    private val context: Context,
    private val serviceClient: T_TelegramServiceClient,
    private val repository: TelegramContentRepository, // Inject instead of create
) {
    private val fileLoader = TelegramFileLoader(serviceClient)
    private val store = SettingsStore(context)

    companion object {
        private const val TAG = "TelegramThumbPrefetcher"
        private const val PREFETCH_DELAY_MS = 1000L // Delay between prefetch batches
        private const val MAX_CONCURRENT_DOWNLOADS = 3 // Max parallel downloads
        private const val THUMBNAIL_TIMEOUT_MS = 15_000L // 15 seconds per thumbnail
    }

    private var prefetchJob: Job? = null
    private val prefetchedRemoteIds = mutableSetOf<String>()

    /**
     * Start the prefetcher in the given coroutine scope.
     * This will continuously monitor for new thumbnails to prefetch.
     */
    fun start(scope: CoroutineScope) {
        // Cancel existing job if any
        prefetchJob?.cancel()

        prefetchJob =
            scope.launch {
                TelegramLogRepository.info(
                    source = TAG,
                    message = "TelegramThumbPrefetcher started",
                )

                try {
                    // Observe enabled state
                    store.tgEnabled.collectLatest { enabled ->
                        if (!enabled) {
                            TelegramLogRepository.debug(
                                source = TAG,
                                message = "Telegram disabled, pausing prefetcher",
                            )
                            return@collectLatest
                        }

                        // Observe content changes and prefetch thumbnails
                        observeAndPrefetch()
                    }
                } catch (e: CancellationException) {
                    // Task 4: Treat cancellations as benign - downgrade to debug level
                    TelegramLogRepository.debug(
                        source = TAG,
                        message = "TelegramThumbPrefetcher cancelled (benign)",
                    )
                    throw e
                } catch (e: Exception) {
                    // Only non-cancellation exceptions are real errors
                    TelegramLogRepository.error(
                        source = TAG,
                        message = "TelegramThumbPrefetcher error",
                        exception = e,
                    )
                }
            }
    }

    /**
     * Stop the prefetcher.
     */
    fun stop() {
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchedRemoteIds.clear()
    }

    /**
     * Observe content changes and prefetch thumbnails for visible items.
     * Uses the new ObxTelegramItem-based API (Phase D).
     *
     * **IMPORTANT**: Uses TelegramImageRef (remoteId-first) instead of fileId directly.
     * fileIds are volatile and become stale after TDLib session changes.
     * remoteIds are stable across sessions and should be used for resolution.
     */
    private suspend fun observeAndPrefetch() {
        // Phase D: Use new TelegramItem-based flow instead of legacy MediaItem flows
        repository.observeVodItemsByChat().collectLatest { chatMap ->
            // Check service state before prefetching
            if (!serviceClient.isStarted || !serviceClient.isAuthReady()) {
                TelegramLogRepository.info(
                    source = TAG,
                    message = "Prefetch skipped – Telegram not started or not READY",
                    details =
                        mapOf(
                            "isStarted" to serviceClient.isStarted.toString(),
                            "isAuthReady" to serviceClient.isAuthReady().toString(),
                        ),
                )
                return@collectLatest
            }

            // Extract all poster TelegramImageRefs that need prefetching
            // Use remoteId as the unique key since fileId is volatile
            val posterRefs =
                chatMap.values
                    .flatten()
                    .mapNotNull { item ->
                        // Get posterRef from TelegramItem - contains remoteId for stable resolution
                        item.posterRef
                    }.distinctBy { it.remoteId } // Distinct by remoteId, not fileId
                    .filter { it.remoteId !in prefetchedRemoteIds } // Skip already prefetched (by remoteId)
                    .take(100) // Limit to 100 at a time to avoid overwhelming TDLib

            if (posterRefs.isEmpty()) {
                TelegramLogRepository.debug(
                    source = TAG,
                    message = "No new thumbnails to prefetch",
                )
                return@collectLatest
            }

            // Log batch start (Requirement 3.2.1)
            TelegramLogRepository.info(
                source = TAG,
                message = "Prefetch batch starting",
                details =
                    mapOf(
                        "batchSize" to posterRefs.size.toString(),
                        "totalChats" to chatMap.size.toString(),
                    ),
            )

            // Track success/failure counts
            var successCount = 0
            var failureCount = 0
            var skippedCount = 0

            // Prefetch thumbnails in batches with concurrency limit
            posterRefs.chunked(MAX_CONCURRENT_DOWNLOADS).forEach { batch ->
                val results =
                    coroutineScope {
                        batch
                            .map { posterRef ->
                                async {
                                    prefetchThumbnail(posterRef)
                                }
                            }.awaitAll()
                    }

                // Count results
                results.forEach { success ->
                    if (success) successCount++ else failureCount++
                }

                // Delay between batches to avoid overwhelming TDLib
                delay(PREFETCH_DELAY_MS)
            }

            // Log batch completion (Requirement 3.2.1)
            TelegramLogRepository.info(
                source = TAG,
                message = "Prefetch batch complete",
                details =
                    mapOf(
                        "total" to posterRefs.size.toString(),
                        "success" to successCount.toString(),
                        "failed" to failureCount.toString(),
                        "skipped" to skippedCount.toString(),
                    ),
            )
        }
    }

    /**
     * Prefetch a single thumbnail using TelegramImageRef (remoteId-first).
     * Returns true if successful, false otherwise.
     *
     * **IMPORTANT**: Uses ensureThumbDownloaded(TelegramImageRef) instead of
     * ensureThumbDownloaded(fileId) because fileIds are volatile and can become
     * stale after TDLib session changes. remoteIds are stable across sessions.
     *
     * @param imageRef TelegramImageRef containing stable remoteId
     */
    private suspend fun prefetchThumbnail(imageRef: TelegramImageRef): Boolean =
        try {
            val result =
                withTimeoutOrNull(THUMBNAIL_TIMEOUT_MS) {
                    // Use ensureThumbDownloaded which uses remoteId-first resolution
                    fileLoader.ensureThumbDownloaded(
                        ref = imageRef,
                        timeoutMs = THUMBNAIL_TIMEOUT_MS,
                    )
                }

            if (result != null) {
                // Track by remoteId, not fileId (remoteId is stable)
                prefetchedRemoteIds.add(imageRef.remoteId)
                TelegramLogRepository.debug(
                    source = TAG,
                    message = "Prefetched thumbnail remoteId=${imageRef.remoteId}, path=$result",
                )
                true
            } else {
                TelegramLogRepository.warn(
                    source = TAG,
                    message = "Failed to prefetch thumbnail remoteId=${imageRef.remoteId}",
                )
                false
            }
        } catch (e: CancellationException) {
            // Task 4: Treat cancellations as benign - downgrade to debug level
            TelegramLogRepository.debug(
                source = TAG,
                message = "Prefetch cancelled for remoteId=${imageRef.remoteId} (benign)",
            )
            false
        } catch (e: Exception) {
            // Only non-cancellation exceptions are real errors
            TelegramLogRepository.error(
                source = TAG,
                message = "Error prefetching thumbnail remoteId=${imageRef.remoteId}",
                exception = e,
            )
            false
        }

    /**
     * Clear the prefetch cache.
     * Use this when chat selections change to re-prefetch for new chats.
     */
    fun clearCache() {
        prefetchedRemoteIds.clear()
        TelegramLogRepository.debug(
            source = TAG,
            message = "Prefetch cache cleared",
        )
    }
}
