package com.fishit.player.pipeline.xtream.catalog.phase

import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogConfig
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogEvent
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicInteger

/**
 * Base interface for catalog scan phases.
 *
 * Each phase is responsible for scanning a specific type of content
 * (Live, VOD, Series, Episodes) and emitting catalog events.
 *
 * **Architecture:**
 * - Stateless (no internal state beyond counters)
 * - Cancellation-aware (checks currentCoroutineContext().isActive)
 * - Thread-safe (uses AtomicInteger for counters)
 * - Progress-aware (emits ScanProgress events)
 *
 * **CC Reduction:**
 * By extracting each phase into a separate class, we reduce the
 * cyclomatic complexity of the main scanCatalog() function from ~38 to <15.
 */
internal interface ScanPhase {
    /**
     * Executes the scan phase.
     *
     * @param config Scan configuration
     * @param channel Channel for emitting catalog events
     * @param counters Shared atomic counters for all content types
     * @param progressMutex Mutex to synchronize progress event emissions
     * @param headers HTTP headers for image URLs
     */
    suspend fun execute(
        config: XtreamCatalogConfig,
        channel: SendChannel<XtreamCatalogEvent>,
        counters: PhaseCounters,
        progressMutex: Mutex,
        headers: Map<String, String>,
    )
}

/**
 * Shared atomic counters for all content types.
 * Passed to all phases for coordinated progress reporting.
 */
internal data class PhaseCounters(
    val vodCounter: AtomicInteger = AtomicInteger(0),
    val seriesCounter: AtomicInteger = AtomicInteger(0),
    val episodeCounter: AtomicInteger = AtomicInteger(0),
    val liveCounter: AtomicInteger = AtomicInteger(0),
)
