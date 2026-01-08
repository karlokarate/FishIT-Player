package com.fishit.player.v2.debug.guardrails

/**
 * StrictMode Configuration for FishIT Player v2 Release Builds (No-Op Stub).
 *
 * **Purpose:**
 * - No-op implementation for release builds
 * - Ensures zero overhead in production
 * - Allows debug code to reference StrictModeConfig without compile errors
 *
 * **Related Issues:**
 * - #609: Performance Baseline and Regression Guards
 *
 * **Contract:**
 * All methods are no-ops in release builds. StrictMode is ONLY active in debug builds.
 *
 * @see app-v2/src/debug/.../guardrails/StrictModeConfig.kt for debug implementation
 */
object StrictModeConfig {
    /**
     * No-op in release builds.
     */
    fun enable() {
        // No-op: StrictMode disabled in release
    }

    /**
     * No-op in release builds.
     */
    fun disable() {
        // No-op: StrictMode never enabled in release
    }

    /**
     * Always returns false in release builds.
     */
    fun isEnabled(): Boolean = false
}
