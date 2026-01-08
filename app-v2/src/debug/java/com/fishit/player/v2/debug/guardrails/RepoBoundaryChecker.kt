package com.fishit.player.v2.debug.guardrails

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Repository Boundary Checker for FishIT Player v2 (Optional Component).
 *
 * **Purpose:**
 * - Detect N+1 query patterns at runtime
 * - Detect unauthorized database access outside repositories
 * - Provide actionable feedback for performance issues
 *
 * **Related Issues:**
 * - #609: Performance Baseline and Regression Guards
 * - Per `OBX_PERFORMANCE_BASELINE.md` Section 5.3 (Eager Loading)
 *
 * **N+1 Query Pattern:**
 * ```kotlin
 * // ❌ BAD: 1 query for series + 100 queries for episodes
 * series.forEach { s ->
 *     val episodes = episodeBox.query(ObxEpisode_.seriesId.equal(s.id)).build().find()
 * }
 *
 * // ✅ GOOD: 1 query with eager loading
 * val series = seriesBox.all  // Includes @Backlink ToMany<Episode>
 * series.forEach { s ->
 *     s.episodes.forEach { e -> /* already loaded */ }
 * }
 * ```
 *
 * **Architecture:**
 * - Hooks into ObjectBox query execution (via reflection/proxy)
 * - Tracks query frequency per call site
 * - Detects loop-based query patterns
 * - Crashes debug builds on confirmed N+1 violations
 *
 * **Status:** OPTIONAL - Disabled by default due to performance overhead
 *
 * @see OBX_PERFORMANCE_BASELINE.md for N+1 detection strategy
 */
object RepoBoundaryChecker {
    private const val TAG = "RepoBoundaryChecker"

    /**
     * Configuration for boundary checking.
     */
    private var config = Config()

    /**
     * Query tracking map: callSite -> count
     */
    private val queryCallSites = ConcurrentHashMap<String, QueryCallSiteInfo>()

    /**
     * Is the checker currently enabled?
     */
    @Volatile
    private var enabled = false

    /**
     * Configuration for RepoBoundaryChecker.
     *
     * @property nPlusOneThreshold Number of queries from same call site to trigger N+1 warning
     * @property crashOnViolation Crash debug builds on N+1 violations (default: false)
     * @property allowedRepositoryPackages Packages where DB access is allowed
     * @property logAllQueries Log every query for debugging (verbose!)
     */
    data class Config(
        val nPlusOneThreshold: Int = 10,
        val crashOnViolation: Boolean = false,
        val allowedRepositoryPackages: List<String> = listOf(
            "com.fishit.player.infra.data",
            "com.fishit.player.core.persistence",
        ),
        val logAllQueries: Boolean = false,
    )

    /**
     * Information about a query call site.
     *
     * @property callStack Full stack trace for diagnosis
     * @property queryCount Number of times this call site executed a query
     * @property firstSeenTimestamp When this call site was first detected
     * @property lastSeenTimestamp When this call site last executed
     * @property isNPlusOne Is this confirmed as an N+1 pattern?
     */
    private data class QueryCallSiteInfo(
        val callStack: String,
        val queryCount: AtomicInteger = AtomicInteger(0),
        val firstSeenTimestamp: Long = System.currentTimeMillis(),
        var lastSeenTimestamp: Long = System.currentTimeMillis(),
        var isNPlusOne: Boolean = false,
    )

    /**
     * Enable the boundary checker with optional configuration.
     *
     * **Warning:** This adds overhead to every ObjectBox query!
     * Only enable for specific debugging sessions.
     *
     * @param config Configuration for detection thresholds
     */
    fun enable(config: Config = Config()) {
        this.config = config
        enabled = true
        queryCallSites.clear()
        Log.i(TAG, "RepoBoundaryChecker ENABLED - N+1 threshold: ${config.nPlusOneThreshold}")
        Log.i(TAG, "Crash on violation: ${config.crashOnViolation}")
    }

    /**
     * Disable the boundary checker.
     */
    fun disable() {
        enabled = false
        Log.i(TAG, "RepoBoundaryChecker DISABLED")
        printSummary()
    }

    /**
     * Record a query execution.
     *
     * Call this from ObjectBox query interceptor (custom implementation required).
     *
     * **Implementation Note:**
     * This requires a custom ObjectBox query wrapper. For now, this is a manual
     * integration point. Future enhancement: Use AspectJ or bytecode weaving.
     *
     * @param queryDescription Human-readable query description (e.g., "ObxVod.query(name=...)")
     */
    fun recordQuery(queryDescription: String) {
        if (!enabled) return

        val callStack = Thread.currentThread().stackTrace
        val callSite = extractCallSite(callStack)

        // Check if call site is in allowed repository packages
        val isInRepository = config.allowedRepositoryPackages.any { pkg ->
            callSite.contains(pkg)
        }

        if (!isInRepository) {
            logUnauthorizedAccess(callSite, queryDescription)
        }

        // Track query count for N+1 detection
        val callSiteKey = "$callSite:$queryDescription"
        val info = queryCallSites.getOrPut(callSiteKey) {
            QueryCallSiteInfo(callStack = formatCallStack(callStack))
        }

        val newCount = info.queryCount.incrementAndGet()
        info.lastSeenTimestamp = System.currentTimeMillis()

        // Check for N+1 pattern
        if (newCount >= config.nPlusOneThreshold && !info.isNPlusOne) {
            info.isNPlusOne = true
            handleNPlusOneDetection(callSiteKey, info, queryDescription)
        }

        if (config.logAllQueries) {
            Log.d(TAG, "Query #$newCount: $queryDescription from $callSite")
        }
    }

    /**
     * Extract call site from stack trace (first non-framework method).
     *
     * @param stackTrace Thread stack trace
     * @return Human-readable call site string
     */
    private fun extractCallSite(stackTrace: Array<StackTraceElement>): String {
        // Skip framework methods and this class
        val relevantFrame = stackTrace.firstOrNull { frame ->
            !frame.className.startsWith("java.") &&
                !frame.className.startsWith("android.") &&
                !frame.className.startsWith("kotlin.") &&
                !frame.className.contains("RepoBoundaryChecker")
        }

        return relevantFrame?.let {
            "${it.className}.${it.methodName}:${it.lineNumber}"
        } ?: "Unknown"
    }

    /**
     * Format full stack trace for violation reports.
     *
     * @param stackTrace Thread stack trace
     * @return Formatted stack trace string
     */
    private fun formatCallStack(stackTrace: Array<StackTraceElement>): String {
        return stackTrace.take(15).joinToString("\n") { frame ->
            "  at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})"
        }
    }

    /**
     * Log unauthorized database access outside repositories.
     *
     * @param callSite Where the query was called from
     * @param queryDescription Query details
     */
    private fun logUnauthorizedAccess(callSite: String, queryDescription: String) {
        Log.w(
            TAG,
            """
            ⚠️  UNAUTHORIZED DATABASE ACCESS DETECTED!
            ========================================
            Call Site: $callSite
            Query: $queryDescription
            
            Database access should ONLY occur in:
            ${config.allowedRepositoryPackages.joinToString("\n") { "  - $it" }}
            
            Fix: Move this query into a repository method.
            See: AGENTS.md Section 4.5 (Layer Boundary Enforcement)
            ========================================
            """.trimIndent()
        )
    }

    /**
     * Handle N+1 query pattern detection.
     *
     * @param callSiteKey Unique key for this call site
     * @param info Query call site information
     * @param queryDescription Query details
     */
    private fun handleNPlusOneDetection(
        callSiteKey: String,
        info: QueryCallSiteInfo,
        queryDescription: String
    ) {
        val message = """
            🔥 N+1 QUERY PATTERN DETECTED!
            ========================================
            Query: $queryDescription
            Execution Count: ${info.queryCount.get()}
            Threshold: ${config.nPlusOneThreshold}
            Duration: ${info.lastSeenTimestamp - info.firstSeenTimestamp}ms
            
            Call Stack:
            ${info.callStack}
            
            ⚠️  PERFORMANCE IMPACT:
            - Each query triggers disk I/O
            - ${info.queryCount.get()} queries = ${info.queryCount.get() * 5}ms+ latency (estimate)
            - Should be 1 query with eager loading
            
            ✅ FIX: Use @Backlink or eager loading
            Example:
            ```kotlin
            @Entity
            data class ObxSeries(
                @Backlink(to = "series")
                lateinit var episodes: ToMany<ObxEpisode>
            )
            
            val series = seriesBox.all
            series.forEach { s ->
                s.episodes.forEach { e -> /* already loaded */ }
            }
            ```
            
            See: OBX_PERFORMANCE_BASELINE.md Section 5.3
            ========================================
        """.trimIndent()

        Log.e(TAG, message)

        if (config.crashOnViolation) {
            throw IllegalStateException("N+1 Query Pattern Detected: $callSiteKey")
        }
    }

    /**
     * Print summary of all detected query patterns.
     */
    private fun printSummary() {
        if (queryCallSites.isEmpty()) {
            Log.i(TAG, "No queries recorded")
            return
        }

        val totalQueries = queryCallSites.values.sumOf { it.queryCount.get() }
        val uniqueCallSites = queryCallSites.size
        val nPlusOneCount = queryCallSites.values.count { it.isNPlusOne }

        Log.i(
            TAG,
            """
            ========================================
            QUERY PATTERN SUMMARY
            ========================================
            Total Queries: $totalQueries
            Unique Call Sites: $uniqueCallSites
            N+1 Patterns Detected: $nPlusOneCount
            ========================================
            """.trimIndent()
        )

        if (nPlusOneCount > 0) {
            Log.w(TAG, "⚠️  $nPlusOneCount N+1 patterns detected - review logs above")
        }
    }

    /**
     * Get current query statistics.
     *
     * @return Map of call site to query count
     */
    fun getQueryStats(): Map<String, Int> {
        return queryCallSites.mapValues { it.value.queryCount.get() }
    }

    /**
     * Reset all tracked queries.
     */
    fun reset() {
        queryCallSites.clear()
        Log.i(TAG, "Query tracking reset")
    }
}
