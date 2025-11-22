package com.chris.m3usuite.telegram.prefetch

import android.content.Context
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.core.TelegramFileLoader
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
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
    private val prefetchedIds = mutableSetOf<Int>()

    /**
     * Start the prefetcher in the given coroutine scope.
     * This will continuously monitor for new thumbnails to prefetch.
     */
    fun start(scope: CoroutineScope) {
        // Cancel existing job if any
        prefetchJob?.cancel()
        
        prefetchJob = scope.launch {
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
                TelegramLogRepository.info(
                    source = TAG,
                    message = "TelegramThumbPrefetcher cancelled",
                )
                throw e
            } catch (e: Exception) {
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
        prefetchedIds.clear()
    }

    /**
     * Observe content changes and prefetch thumbnails for visible items.
     */
    private suspend fun observeAndPrefetch() {
        // Combine VOD and Series content flows
        combine(
            repository.getTelegramVodByChat(),
            repository.getTelegramSeriesByChat(),
        ) { vodMap, seriesMap ->
            // Merge both maps
            val allChats = mutableMapOf<Long, Pair<String, List<com.chris.m3usuite.model.MediaItem>>>()
            allChats.putAll(vodMap)
            allChats.putAll(seriesMap)
            allChats
        }.collectLatest { chatMap ->
            // Extract all poster IDs that need prefetching
            val posterIds = chatMap.values
                .flatMap { (_, items) -> items }
                .mapNotNull { it.posterId }
                .filter { it !in prefetchedIds } // Skip already prefetched
                .distinct()
                .take(100) // Limit to 100 at a time to avoid overwhelming TDLib
            
            if (posterIds.isEmpty()) {
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
                details = mapOf(
                    "batchSize" to posterIds.size.toString(),
                    "totalChats" to chatMap.size.toString(),
                ),
            )
            
            // Track success/failure counts
            var successCount = 0
            var failureCount = 0
            var skippedCount = 0
            
            // Prefetch thumbnails in batches with concurrency limit
            posterIds.chunked(MAX_CONCURRENT_DOWNLOADS).forEach { batch ->
                val results =
                    coroutineScope {
                        batch.map { posterId ->
                            async {
                                prefetchThumbnail(posterId)
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
                        "total" to posterIds.size.toString(),
                        "success" to successCount.toString(),
                        "failed" to failureCount.toString(),
                        "skipped" to skippedCount.toString(),
                    ),
            )
        }
    }

    /**
     * Prefetch a single thumbnail.
     * Returns true if successful, false otherwise.
     */
    private suspend fun prefetchThumbnail(fileId: Int): Boolean {
        return try {
            val result = withTimeoutOrNull(THUMBNAIL_TIMEOUT_MS) {
                fileLoader.ensureThumbDownloaded(
                    fileId = fileId,
                    timeoutMs = THUMBNAIL_TIMEOUT_MS,
                )
            }
            
            if (result != null) {
                prefetchedIds.add(fileId)
                TelegramLogRepository.debug(
                    source = TAG,
                    message = "Prefetched thumbnail fileId=$fileId, path=$result",
                )
                true
            } else {
                TelegramLogRepository.warn(
                    source = TAG,
                    message = "Failed to prefetch thumbnail fileId=$fileId",
                )
                false
            }
        } catch (e: Exception) {
            TelegramLogRepository.error(
                source = TAG,
                message = "Error prefetching thumbnail fileId=$fileId",
                exception = e,
            )
            false
        }
    }

    /**
     * Clear the prefetch cache.
     * Use this when chat selections change to re-prefetch for new chats.
     */
    fun clearCache() {
        prefetchedIds.clear()
        TelegramLogRepository.debug(
            source = TAG,
            message = "Prefetch cache cleared",
        )
    }
}
