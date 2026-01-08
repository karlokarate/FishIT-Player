package com.fishit.player.v2.debug.guardrails

/**
 * Repository Boundary Checker for FishIT Player v2 Release Builds (No-Op Stub).
 *
 * **Purpose:**
 * - No-op implementation for release builds
 * - Ensures zero overhead in production
 * - Allows optional debug code to reference RepoBoundaryChecker
 *
 * **Related Issues:**
 * - #609: Performance Baseline and Regression Guards
 *
 * **Status:** OPTIONAL - Only active in debug builds
 *
 * @see app-v2/src/debug/.../guardrails/RepoBoundaryChecker.kt for debug implementation
 */
object RepoBoundaryChecker {
    /**
     * Configuration (no-op in release).
     */
    data class Config(
        val nPlusOneThreshold: Int = 10,
        val crashOnViolation: Boolean = false,
        val allowedRepositoryPackages: List<String> = emptyList(),
        val logAllQueries: Boolean = false,
    )

    /**
     * No-op in release builds.
     */
    fun enable(config: Config = Config()) {
        // No-op
    }

    /**
     * No-op in release builds.
     */
    fun disable() {
        // No-op
    }

    /**
     * No-op in release builds.
     */
    fun recordQuery(queryDescription: String) {
        // No-op
    }

    /**
     * Always returns empty map in release builds.
     */
    fun getQueryStats(): Map<String, Int> = emptyMap()

    /**
     * No-op in release builds.
     */
    fun reset() {
        // No-op
    }
}
