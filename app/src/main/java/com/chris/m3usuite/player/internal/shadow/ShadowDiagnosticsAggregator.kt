package com.chris.m3usuite.player.internal.shadow

/**
 * Aggregates shadow diagnostics events for Phase 3 Step 4 spec-driven verification.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 STEP 4: SPEC-BASED DIAGNOSTICS AGGREGATION
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This aggregator collects and forwards shadow diagnostic events for:
 * - Spec comparison results (ParityKind classification)
 * - Legacy parity comparison results
 * - Controls diagnostics
 *
 * **PURPOSE:**
 * - Store results from spec comparison for debugging and verification
 * - Forward events to downstream consumers (future Phase 9 debug UI)
 * - Include dimension information ("resume", "kids", "position", "controls")
 *
 * **USAGE:**
 * ```kotlin
 * val aggregator = ShadowDiagnosticsAggregator()
 *
 * // Register for spec comparison events
 * aggregator.onSpecComparison { result ->
 *     when (result.parityKind) {
 *         ParityKind.SpecPreferredSIP -> log("SIP fixes legacy bug: ${result.dimension}")
 *         ParityKind.SpecPreferredLegacy -> log("SIP violation: ${result.dimension}")
 *         else -> {}
 *     }
 * }
 *
 * // Emit events from shadow session
 * aggregator.emit(ShadowEvent(
 *     kind = ShadowEvent.Kind.SpecComparison,
 *     dimension = "resume",
 *     specResult = specComparisonResult
 * ))
 * ```
 *
 * **SAFETY GUARANTEES:**
 * - Never affects runtime behavior
 * - Thread-safe event collection
 * - Never throws exceptions to callers
 */
class ShadowDiagnosticsAggregator {

    // ════════════════════════════════════════════════════════════════════════════
    // Event Callbacks
    // ════════════════════════════════════════════════════════════════════════════

    private var specComparisonCallback: ((ShadowComparisonService.SpecComparisonResult) -> Unit)? = null
    private var legacyComparisonCallback: ((ShadowComparisonService.ComparisonResult) -> Unit)? = null
    private var controlsDiagnosticCallback: ((String) -> Unit)? = null
    private var eventCallback: ((ShadowEvent) -> Unit)? = null

    // ════════════════════════════════════════════════════════════════════════════
    // Event Storage (for debugging/verification)
    // ════════════════════════════════════════════════════════════════════════════

    private val _events = mutableListOf<ShadowEvent>()

    /**
     * All collected shadow events.
     * Use for debugging and verification.
     */
    val events: List<ShadowEvent>
        get() = _events.toList()

    /**
     * Spec comparison events only (filtered).
     */
    val specComparisonEvents: List<ShadowEvent>
        get() = _events.filter { it.kind == ShadowEvent.Kind.SpecComparison }

    /**
     * Legacy comparison events only (filtered).
     */
    val legacyComparisonEvents: List<ShadowEvent>
        get() = _events.filter { it.kind == ShadowEvent.Kind.LegacyComparison }

    /**
     * Controls diagnostic events only (filtered).
     */
    val controlsDiagnosticEvents: List<ShadowEvent>
        get() = _events.filter { it.kind == ShadowEvent.Kind.ControlsDiagnostic }

    // ════════════════════════════════════════════════════════════════════════════
    // Callback Registration
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Register a callback for spec comparison results.
     */
    fun onSpecComparison(callback: (ShadowComparisonService.SpecComparisonResult) -> Unit) {
        specComparisonCallback = callback
    }

    /**
     * Register a callback for legacy comparison results.
     */
    fun onLegacyComparison(callback: (ShadowComparisonService.ComparisonResult) -> Unit) {
        legacyComparisonCallback = callback
    }

    /**
     * Register a callback for controls diagnostic strings.
     */
    fun onControlsDiagnostic(callback: (String) -> Unit) {
        controlsDiagnosticCallback = callback
    }

    /**
     * Register a callback for all shadow events.
     */
    fun onEvent(callback: (ShadowEvent) -> Unit) {
        eventCallback = callback
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Event Emission
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Emit a shadow event and forward to registered callbacks.
     *
     * @param event The shadow event to emit
     */
    fun emit(event: ShadowEvent) {
        try {
            // Store the event
            synchronized(_events) {
                _events.add(event)
            }

            // Forward to generic event callback
            eventCallback?.invoke(event)

            // Forward to specific callbacks based on event kind
            when (event.kind) {
                ShadowEvent.Kind.SpecComparison -> {
                    event.specResult?.let { specComparisonCallback?.invoke(it) }
                }
                ShadowEvent.Kind.LegacyComparison -> {
                    event.legacyResult?.let { legacyComparisonCallback?.invoke(it) }
                }
                ShadowEvent.Kind.ControlsDiagnostic -> {
                    event.diagnosticMessage?.let { controlsDiagnosticCallback?.invoke(it) }
                }
            }
        } catch (_: Throwable) {
            // Fail-safe: Never throw, silently absorb errors
        }
    }

    /**
     * Emit a spec comparison result as a shadow event.
     */
    fun emitSpecComparison(result: ShadowComparisonService.SpecComparisonResult) {
        emit(
            ShadowEvent(
                kind = ShadowEvent.Kind.SpecComparison,
                dimension = result.dimension,
                specResult = result,
            ),
        )
    }

    /**
     * Emit a legacy comparison result as a shadow event.
     */
    fun emitLegacyComparison(result: ShadowComparisonService.ComparisonResult) {
        emit(
            ShadowEvent(
                kind = ShadowEvent.Kind.LegacyComparison,
                dimension = "parity",
                legacyResult = result,
            ),
        )
    }

    /**
     * Emit a controls diagnostic string as a shadow event.
     */
    fun emitControlsDiagnostic(message: String) {
        emit(
            ShadowEvent(
                kind = ShadowEvent.Kind.ControlsDiagnostic,
                dimension = "controls",
                diagnosticMessage = message,
            ),
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Utility Methods
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Clear all stored events.
     */
    fun clear() {
        synchronized(_events) {
            _events.clear()
        }
    }

    /**
     * Get event count.
     */
    val eventCount: Int
        get() = synchronized(_events) { _events.size }

    /**
     * Get events by dimension.
     */
    fun eventsByDimension(dimension: String): List<ShadowEvent> =
        _events.filter { it.dimension == dimension }

    /**
     * Get events by ParityKind (for spec comparison events only).
     */
    fun eventsByParityKind(kind: ShadowComparisonService.ParityKind): List<ShadowEvent> =
        specComparisonEvents.filter { it.specResult?.parityKind == kind }
}

/**
 * Shadow diagnostic event.
 *
 * Represents a single diagnostic event from the shadow pipeline.
 *
 * @property kind The type of diagnostic event
 * @property dimension The aspect being diagnosed ("resume", "kids", "position", "controls")
 * @property specResult Spec comparison result (for SpecComparison events)
 * @property legacyResult Legacy comparison result (for LegacyComparison events)
 * @property diagnosticMessage Diagnostic string (for ControlsDiagnostic events)
 * @property timestampMs When this event was created
 */
data class ShadowEvent(
    val kind: Kind,
    val dimension: String,
    val specResult: ShadowComparisonService.SpecComparisonResult? = null,
    val legacyResult: ShadowComparisonService.ComparisonResult? = null,
    val diagnosticMessage: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    /**
     * Event kind for shadow diagnostics.
     */
    enum class Kind {
        /**
         * Spec-driven comparison result (Phase 3 Step 4).
         * Uses [ShadowComparisonService.SpecComparisonResult].
         */
        SpecComparison,

        /**
         * Legacy parity comparison result.
         * Uses [ShadowComparisonService.ComparisonResult].
         */
        LegacyComparison,

        /**
         * Controls diagnostic string.
         * Uses [diagnosticMessage].
         */
        ControlsDiagnostic,
    }
}
