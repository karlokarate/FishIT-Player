package com.fishit.player.core.model

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks health status of media variants for dead variant detection.
 *
 * **Purpose:** When a variant fails repeatedly (404, file not found, etc.), it should be marked as
 * permanently dead and removed from the catalog.
 *
 * **Policy:**
 * - Increment failure count on confirmed hard errors (404, file deleted, etc.)
 * - Mark as permanently dead if:
 * - ≥3 hard failures AND
 * - Last failure was ≥24 hours ago (allows recovery)
 *
 * **NOT counted as hard failures:**
 * - Network timeouts (temporary)
 * - Server 500 errors (temporary)
 * - Rate limiting (temporary)
 *
 * **Usage:**
 * 1. Call `recordHardFailure()` when playback fails with 404/gone error
 * 2. Call `isPermanentlyDead()` before attempting playback
 * 3. Call `clearHealth()` if variant becomes playable again
 */
object VariantHealthStore {
    /** Minimum failures before considering permanent death. */
    private const val HARD_FAILURE_THRESHOLD = 3

    /** Time window before death is confirmed (24 hours in millis). */
    private const val DEATH_CONFIRMATION_WINDOW_MS = 24 * 60 * 60 * 1000L

    /** Health data indexed by SourceKey serialized string. */
    private val healthBySourceKey = ConcurrentHashMap<String, VariantHealth>()

    /**
     * Record a hard failure for a variant.
     *
     * @param sourceKey The variant's source key
     * @param nowMillis Current timestamp in milliseconds
     */
    fun recordHardFailure(
        sourceKey: SourceKey,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val key = sourceKey.toSerializedString()
        healthBySourceKey.compute(key) { _, existing ->
            val health = existing ?: VariantHealth()
            health.hardFailureCount++
            health.lastHardFailureMillis = nowMillis
            health
        }
    }

    /**
     * Check if a variant is permanently dead.
     *
     * @param sourceKey The variant's source key
     * @param nowMillis Current timestamp for comparison
     * @return true if variant should be considered dead
     */
    fun isPermanentlyDead(
        sourceKey: SourceKey,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val key = sourceKey.toSerializedString()
        val health = healthBySourceKey[key] ?: return false

        if (health.hardFailureCount < HARD_FAILURE_THRESHOLD) {
            return false
        }

        val lastFailure = health.lastHardFailureMillis ?: return false
        val timeSinceFailure = nowMillis - lastFailure

        // Must have aged at least 24 hours to confirm death
        return timeSinceFailure >= DEATH_CONFIRMATION_WINDOW_MS
    }

    /**
     * Get current failure count for a variant.
     *
     * @param sourceKey The variant's source key
     * @return Number of recorded hard failures
     */
    fun getFailureCount(sourceKey: SourceKey): Int {
        val key = sourceKey.toSerializedString()
        return healthBySourceKey[key]?.hardFailureCount ?: 0
    }

    /**
     * Clear health data for a variant (e.g., when it becomes playable).
     *
     * @param sourceKey The variant's source key
     */
    fun clearHealth(sourceKey: SourceKey) {
        val key = sourceKey.toSerializedString()
        healthBySourceKey.remove(key)
    }

    /**
     * Get all variants that are permanently dead.
     *
     * @param nowMillis Current timestamp
     * @return Set of dead variant SourceKeys
     */
    fun getDeadVariants(nowMillis: Long = System.currentTimeMillis()): Set<SourceKey> =
        healthBySourceKey
            .entries
            .filter { (key, health) ->
                health.hardFailureCount >= HARD_FAILURE_THRESHOLD &&
                    health.lastHardFailureMillis != null &&
                    (nowMillis - health.lastHardFailureMillis!!) >=
                    DEATH_CONFIRMATION_WINDOW_MS
            }.mapNotNull { SourceKey.fromSerializedString(it.key) }
            .toSet()

    /** Reset all health data (for testing). */
    fun clear() {
        healthBySourceKey.clear()
    }
}

/** Health tracking data for a single variant. */
data class VariantHealth(
    var hardFailureCount: Int = 0,
    var lastHardFailureMillis: Long? = null,
)
