package com.fishit.player.core.model

/**
 * Shared policy constants for TMDB resolution/enrichment.
 *
 * Kept in core:model so both persistence and workers can converge on the same rules
 * without introducing cross-layer dependencies.
 */
object TmdbResolvePolicy {
    /**
     * Max attempts before an item is marked as [TmdbResolveState.UNRESOLVABLE_PERMANENT].
     */
    const val MAX_ATTEMPTS: Int = 3
}
