package com.chris.m3usuite.telegram.prefetch

import android.content.Context
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.playback.PlaybackSession
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
 * **Phase 4: Runtime Settings Integration:**
 * - Respects settings.thumbPrefetchEnabled (skip if false)
 * - Limits batch size via settings.thumbPrefetchBatchSize
 * - Enforces parallel download limit via settings.thumbMaxParallel
 * - Pauses during VOD buffering via settings.thumbPauseWhileVodBuffering
 *
 * Usage:
 * ```
 * val prefetcher = TelegramThumbPrefetcher(context, serviceClient, repository, settingsProvider)
 * prefetcher.start(scope)
 * ```
 */
class TelegramThumbPrefetcher(
    private val context: Context,
    private val serviceClient: T_TelegramServiceClient,
    private val repository: TelegramContentRepository, // Inject instead of create
    private val settingsProvider: com.chris.m3usuite.telegram.domain.TelegramStreamingSettingsProvider,
) {
    private val fileLoader = TelegramFileLoader(context, serviceClient, settingsProvider)
    private val store = SettingsStore(context)

    companion object {
        private const val TAG = "TelegramThumbPrefetcher"
        private const val PREFETCH_DELAY_MS = 1000L // Delay between prefetch batches

        @Deprecated("Phase 4: Use settings.thumbMaxParallel instead")
        private const val MAX_CONCURRENT_DOWNLOADS = 3 // Max parallel downloads (legacy)

        private const val THUMBNAIL_TIMEOUT_MS = 15_000L // 15 seconds per thumbnail
    }

    private var prefetchJob: Job? = null
    private val prefetchedRemoteIds = mutableSetOf<String>()

    // Phase 4: Semaphore for controlling parallel downloads (will be initialized with runtime setting)
    private var downloadSemaphore: kotlinx.coroutines.sync.Semaphore? = null

    private val vodBufferingFlow: Flow<Boolean> =
        combine(
            PlaybackSession.buffering,
            PlaybackSession.currentSourceState,
        ) { isBuffering, source ->
            val src = source ?: return@combine false
            isBuffering && src.isTelegram && src.isVodLike
        }.distinctUntilChanged()

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
        prefetchedRemoteIds.clear()
    }

    /**
     * Observe content changes and prefetch thumbnails for visible items.
     * Uses the new ObxTelegramItem-based API (Phase D).
     *
     * **IMPORTANT**: Uses TelegramImageRef (remoteId-first) instead of fileId directly.
     * fileIds are volatile and become stale after TDLib session changes.
     * remoteIds are stable across sessions and should be used for resolution.
     *
     * **Phase 4: Runtime Settings Integration:**
     * - Checks settings.thumbPrefetchEnabled and returns early if disabled
     * - Limits batch size using settings.thumbPrefetchBatchSize
     * - Enforces parallel downloads using settings.thumbMaxParallel via Semaphore
     * - Pauses while Telegram VOD is buffering when thumbPauseWhileVodBuffering is enabled
     */
    private suspend fun observeAndPrefetch() {
        // Phase 4: Get runtime settings
        val settings = settingsProvider.currentSettings

        // Phase 4: Check if prefetch is enabled
        if (!settings.thumbPrefetchEnabled) {
            TelegramLogRepository.info(
                source = TAG,
                message = "Thumbnail prefetch disabled by settings",
            )
            return
        }

        // Phase 4: Initialize semaphore with runtime setting for parallel downloads
        downloadSemaphore = kotlinx.coroutines.sync.Semaphore(settings.thumbMaxParallel)

        TelegramLogRepository.info(
            source = TAG,
            message = "Prefetch configured with runtime settings",
            details =
                mapOf(
                    "thumbPrefetchEnabled" to settings.thumbPrefetchEnabled.toString(),
                    "thumbPrefetchBatchSize" to settings.thumbPrefetchBatchSize.toString(),
                    "thumbMaxParallel" to settings.thumbMaxParallel.toString(),
                    "thumbPauseWhileVodBuffering" to settings.thumbPauseWhileVodBuffering.toString(),
                ),
        )

        // Phase D: Use new TelegramItem-based flow instead of legacy MediaItem flows
        combine(
            repository.observeVodItemsByChat(),
            vodBufferingFlow,
        ) { chatMap, vodBuffering -> chatMap to vodBuffering }
            .collectLatest { (chatMap, vodBuffering) ->
                // Phase 4: Re-check settings in case they changed
                val currentSettings = settingsProvider.currentSettings
                if (!currentSettings.thumbPrefetchEnabled) {
                    TelegramLogRepository.debug(
                        source = TAG,
                        message = "Prefetch disabled, skipping batch",
                    )
                    return@collectLatest
                }

                if (currentSettings.thumbPauseWhileVodBuffering && vodBuffering) {
                    TelegramLogRepository.debug(
                        source = TAG,
                        message = "Prefetch paused – Telegram VOD buffering",
                    )
                    return@collectLatest
                }

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
                // Phase 4: Limit to runtime batch size instead of hardcoded 100
                val posterRefs =
                    chatMap.values
                        .flatten()
                        .mapNotNull { item ->
                            // Get posterRef from TelegramItem - contains remoteId for stable resolution
                            item.posterRef
                        }.distinctBy { it.remoteId } // Distinct by remoteId, not fileId
                        .filter { it.remoteId !in prefetchedRemoteIds } // Skip already prefetched (by remoteId)
                        .take(currentSettings.thumbPrefetchBatchSize) // Phase 4: Use runtime batch size

                if (posterRefs.isEmpty()) {
                    TelegramLogRepository.debug(
                        source = TAG,
                        message = "No new thumbnails to prefetch",
                    )
                    return@collectLatest
                }

                // Log batch start (Requirement 3.2.1 + Phase 4: runtime settings)
                TelegramLogRepository.info(
                    source = TAG,
                    message = "Prefetch batch starting",
                    details =
                        mapOf(
                            "batchSize" to posterRefs.size.toString(),
                            "maxBatchSize" to currentSettings.thumbPrefetchBatchSize.toString(),
                            "totalChats" to chatMap.size.toString(),
                            "maxParallel" to currentSettings.thumbMaxParallel.toString(),
                        ),
                )

                // Track success/failure counts
                var successCount = 0
                var failureCount = 0
                var skippedCount = 0

                // Phase 4: Prefetch thumbnails using semaphore for concurrency control
                // Process all items in parallel but limited by semaphore
                val results =
                    coroutineScope {
                        posterRefs
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
     * **Phase 4: Uses semaphore to limit parallel downloads.**
     *
     * **Requirement 5: Prevents repeated downloads:**
     * - Checks if thumbnail is already fully downloaded before scheduling
     * - Skips download if file exists and size matches expected size
     * - Logs why each thumbnail is scheduled or skipped
     *
     * @param imageRef TelegramImageRef containing stable remoteId
     */
    private suspend fun prefetchThumbnail(imageRef: TelegramImageRef): Boolean {
        try {
            // First check if already fully downloaded (before acquiring semaphore)
            val fileId = imageRef.fileId
            if (fileId != null && fileId > 0) {
                val downloader = serviceClient.downloader()
                val fileInfo = downloader.getFileInfo(fileId)

                if (fileInfo != null) {
                    val isComplete = fileInfo.local?.isDownloadingCompleted ?: false
                    val localPath = fileInfo.local?.path
                    val expectedSize = fileInfo.expectedSize?.toLong() ?: 0L

                    if (isComplete && !localPath.isNullOrBlank()) {
                        val localFile = java.io.File(localPath)
                        if (localFile.exists() && (expectedSize <= 0L || localFile.length() >= expectedSize)) {
                            TelegramLogRepository.debug(
                                source = TAG,
                                message = "Prefetch skipped: thumbnail already fully downloaded",
                                details =
                                    mapOf(
                                        "remoteId" to imageRef.remoteId,
                                        "fileId" to fileId.toString(),
                                        "fileSize" to localFile.length().toString(),
                                        "expectedSize" to expectedSize.toString(),
                                    ),
                            )
                            prefetchedRemoteIds.add(imageRef.remoteId)
                            return true
                        }
                    }
                }
            }

            // Phase 4: Acquire semaphore permit before downloading
            downloadSemaphore?.acquire()

            try {
                TelegramLogRepository.info(
                    source = TAG,
                    message = "Prefetch scheduled: starting thumbnail download",
                    details =
                        mapOf(
                            "remoteId" to imageRef.remoteId,
                            "fileId" to (fileId?.toString() ?: "none"),
                        ),
                )

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
                    TelegramLogRepository.info(
                        source = TAG,
                        message = "Prefetch complete: thumbnail downloaded successfully",
                        details =
                            mapOf(
                                "remoteId" to imageRef.remoteId,
                                "path" to result,
                            ),
                    )
                    return true
                } else {
                    TelegramLogRepository.warn(
                        source = TAG,
                        message = "Prefetch failed: timeout or error",
                        details = mapOf("remoteId" to imageRef.remoteId),
                    )
                    return false
                }
            } finally {
                // Phase 4: Release semaphore permit after download completes
                downloadSemaphore?.release()
            }
        } catch (e: Exception) {
            TelegramLogRepository.error(
                source = TAG,
                message = "Prefetch error: exception during thumbnail download",
                exception = e,
                details = mapOf("remoteId" to imageRef.remoteId),
            )
            return false
        }
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
