/**
 * Shared mapping utility functions for NX entity ↔ domain conversions.
 *
 * SSOT for common mapping patterns to eliminate duplication across mapper files.
 * All mappers should use these utilities instead of implementing their own variants.
 *
 * **NX_CONSOLIDATION_PLAN Phase 5**
 *
 * @see docs/v2/NX_CONSOLIDATION_PLAN.md Section 8 (Phase 5)
 */
package com.fishit.player.infra.data.nx.mapper.base

/**
 * Shared mapping utilities used by all NX entity mappers.
 *
 * Provides:
 * - [safeEnumFromString]: Safe Enum↔String conversion with fallback
 * - [enrichOnly]: Write-once guard (only sets if existing is null)
 * - [alwaysUpdate]: Always-overwrite (new value takes precedence)
 * - [monotonicUp]: Monotonic upgrade (never downgrades enum ordinal)
 */
object MappingUtils {
    /**
     * Safe enum-from-string conversion with explicit default.
     *
     * Case-insensitive, returns [default] if the value is null or doesn't match any entry.
     *
     * Usage:
     * ```kotlin
     * val state = MappingUtils.safeEnumFromString(entity.recognitionState, RecognitionState.HEURISTIC)
     * ```
     */
    inline fun <reified T : Enum<T>> safeEnumFromString(
        value: String?,
        default: T,
    ): T =
        value?.let {
            enumValues<T>().find { e -> e.name.equals(it, ignoreCase = true) }
        } ?: default

    /**
     * Write-once guard: returns existing value if non-null, otherwise new value.
     *
     * Used for ENRICH_ONLY fields in `enrichIfAbsent()`:
     * - poster, backdrop, plot, genres, director, cast, etc.
     *
     * @param existing Current entity value
     * @param new Incoming enrichment value
     * @return Existing if non-null, otherwise new
     */
    fun <T> enrichOnly(
        existing: T?,
        new: T?,
    ): T? = existing ?: new

    /**
     * Always-update: new value takes precedence if non-null, preserves existing otherwise.
     *
     * Used for ALWAYS_UPDATE fields:
     * - tmdbId, imdbId, tvdbId, authorityKey
     *
     * @param existing Current entity value
     * @param new Incoming value
     * @return New if non-null, otherwise existing
     */
    fun <T> alwaysUpdate(
        existing: T?,
        new: T?,
    ): T? = new ?: existing

    /**
     * Monotonic upgrade for enum ordinals — value can only move to a LOWER ordinal.
     *
     * Used for RecognitionState where the enum is ordered by confidence:
     * `CONFIRMED(0) < HEURISTIC(1) < NEEDS_REVIEW(2) < UNPLAYABLE(3)`
     *
     * Once CONFIRMED, it can never go back to HEURISTIC.
     *
     * @param existing Current enum value
     * @param new Incoming enum value
     * @return The value with the lower ordinal (higher confidence)
     */
    fun <T : Enum<T>> monotonicUp(
        existing: T?,
        new: T?,
    ): T? =
        when {
            existing == null -> new
            new == null -> existing
            new.ordinal < existing.ordinal -> new // Lower ordinal = higher confidence
            else -> existing
        }
}
