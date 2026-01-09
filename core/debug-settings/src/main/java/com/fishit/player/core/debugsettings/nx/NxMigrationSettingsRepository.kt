package com.fishit.player.core.debugsettings.nx

import kotlinx.coroutines.flow.Flow

/**
 * Repository for NX_ migration runtime settings.
 *
 * **Purpose:**
 * Provides runtime control over the OBX PLATIN migration without app reinstall.
 * Allows gradual rollout and debugging during migration phases.
 *
 * **Contract:**
 * - DEBUG builds only (release uses hardcoded Phase 6+ defaults)
 * - Backed by DataStore Preferences
 * - All defaults are conservative (LEGACY_ONLY, OFF, HIDDEN)
 * - No global mutable singletons (uses Flow for reactive state)
 *
 * **Defaults (MANDATORY):**
 * - catalogReadMode = LEGACY_ONLY
 * - catalogWriteMode = LEGACY_ONLY
 * - migrationMode = OFF
 * - nxUiVisibility = HIDDEN
 *
 * **Reference:** docs/v2/NX_SSOT_CONTRACT.md, docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
 *
 * **Usage:**
 * ```kotlin
 * // Observe read mode
 * settingsRepo.catalogReadModeFlow.collect { mode ->
 *     when (mode) {
 *         CatalogReadMode.LEGACY_ONLY -> readFromLegacy()
 *         CatalogReadMode.NX_ONLY -> readFromNx()
 *         CatalogReadMode.SHADOW -> readBothAndCompare()
 *     }
 * }
 *
 * // Switch to dual-write (Phase 3)
 * settingsRepo.setCatalogWriteMode(CatalogWriteMode.DUAL)
 * ```
 */
interface NxMigrationSettingsRepository {

    // ═══════════════════════════════════════════════════════════════════════
    // Read Mode
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Flow of current catalog read mode.
     * Default: [CatalogReadMode.LEGACY_ONLY]
     */
    val catalogReadModeFlow: Flow<CatalogReadMode>

    /**
     * Set the catalog read mode.
     * Takes effect immediately via Flow.
     */
    suspend fun setCatalogReadMode(mode: CatalogReadMode)

    // ═══════════════════════════════════════════════════════════════════════
    // Write Mode
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Flow of current catalog write mode.
     * Default: [CatalogWriteMode.LEGACY_ONLY]
     */
    val catalogWriteModeFlow: Flow<CatalogWriteMode>

    /**
     * Set the catalog write mode.
     * Takes effect immediately via Flow.
     */
    suspend fun setCatalogWriteMode(mode: CatalogWriteMode)

    // ═══════════════════════════════════════════════════════════════════════
    // Migration Mode
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Flow of current migration mode.
     * Default: [MigrationMode.OFF]
     */
    val migrationModeFlow: Flow<MigrationMode>

    /**
     * Set the migration mode.
     * Takes effect immediately via Flow.
     */
    suspend fun setMigrationMode(mode: MigrationMode)

    // ═══════════════════════════════════════════════════════════════════════
    // UI Visibility
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Flow of current NX_ UI visibility.
     * Default: [NxUiVisibility.HIDDEN]
     */
    val nxUiVisibilityFlow: Flow<NxUiVisibility>

    /**
     * Set the NX_ UI visibility.
     * Takes effect immediately via Flow.
     */
    suspend fun setNxUiVisibility(visibility: NxUiVisibility)

    // ═══════════════════════════════════════════════════════════════════════
    // Convenience: Current State (non-Flow)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get current catalog read mode synchronously.
     * Prefer [catalogReadModeFlow] for reactive usage.
     */
    suspend fun getCatalogReadMode(): CatalogReadMode

    /**
     * Get current catalog write mode synchronously.
     * Prefer [catalogWriteModeFlow] for reactive usage.
     */
    suspend fun getCatalogWriteMode(): CatalogWriteMode

    /**
     * Get current migration mode synchronously.
     * Prefer [migrationModeFlow] for reactive usage.
     */
    suspend fun getMigrationMode(): MigrationMode

    /**
     * Get current NX_ UI visibility synchronously.
     * Prefer [nxUiVisibilityFlow] for reactive usage.
     */
    suspend fun getNxUiVisibility(): NxUiVisibility
}
