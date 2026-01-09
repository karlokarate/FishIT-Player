package com.fishit.player.core.debugsettings.nx

/**
 * Runtime modes for NX_ persistence migration (OBX PLATIN Refactor).
 *
 * These modes control how the app reads, writes, and migrates data
 * between legacy Obx* entities and new NX_* entities.
 *
 * **Reference:** docs/v2/NX_SSOT_CONTRACT.md, docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
 *
 * @see NxMigrationSettingsRepository
 */

/**
 * Controls which persistence layer UI/Domain reads from.
 *
 * **Phase Progression:**
 * - Phase 0-3: LEGACY_ONLY (default)
 * - Phase 4: SHADOW (read both, compare, log differences)
 * - Phase 5+: NX_ONLY (legacy reads disabled)
 */
enum class CatalogReadMode {
    /**
     * Read exclusively from legacy Obx* entities.
     * Default for Phase 0-3.
     */
    LEGACY_ONLY,

    /**
     * Read from NX_* entities only.
     * Target state after Phase 5.
     */
    NX_ONLY,

    /**
     * Shadow mode: Read from both, compare results, log differences.
     * Used during Phase 4 validation.
     *
     * UI receives NX_* data, but mismatches are logged for debugging.
     */
    SHADOW
}

/**
 * Controls which persistence layer receives writes.
 *
 * **Phase Progression:**
 * - Phase 0-2: LEGACY_ONLY (default)
 * - Phase 3: DUAL (write to both layers)
 * - Phase 5+: NX_ONLY (legacy writes disabled)
 */
enum class CatalogWriteMode {
    /**
     * Write exclusively to legacy Obx* entities.
     * Default for Phase 0-2.
     */
    LEGACY_ONLY,

    /**
     * Write exclusively to NX_* entities.
     * Target state after Phase 5.
     */
    NX_ONLY,

    /**
     * Dual-write: Write to both legacy and NX_* layers.
     * Used during Phase 3-4 for gradual migration.
     *
     * If NX_* write fails, operation is logged but does not fail overall.
     */
    DUAL
}

/**
 * Controls migration worker behavior.
 *
 * **Usage:**
 * - OFF: No automatic migration (manual testing only)
 * - INCREMENTAL: Migrate items on access (lazy)
 * - FULL_REBUILD: Background worker migrates all legacy data
 */
enum class MigrationMode {
    /**
     * Migration disabled. Manual testing only.
     * Default for development/debugging.
     */
    OFF,

    /**
     * Lazy migration: Items are migrated when accessed.
     * Suitable for gradual rollout.
     */
    INCREMENTAL,

    /**
     * Full rebuild: Background worker migrates all legacy data.
     * Used for complete migration in Phase 5.
     */
    FULL_REBUILD
}

/**
 * Controls NX_* data visibility in UI.
 *
 * **Usage:**
 * - HIDDEN: NX_* data not shown anywhere (Phase 0-2)
 * - DEBUG_ONLY: NX_* data visible in debug screens only (Phase 3-4)
 * - FULL: NX_* data visible in all UI (Phase 5+)
 */
enum class NxUiVisibility {
    /**
     * NX_* entities not visible in any UI.
     * Default for Phase 0-2.
     */
    HIDDEN,

    /**
     * NX_* entities visible only in debug/settings screens.
     * Used for validation during Phase 3-4.
     */
    DEBUG_ONLY,

    /**
     * NX_* entities visible throughout the app.
     * Target state after Phase 5.
     */
    FULL
}
