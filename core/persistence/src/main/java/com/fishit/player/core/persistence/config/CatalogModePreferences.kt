package com.fishit.player.core.persistence.config

import android.content.Context
import android.content.SharedPreferences
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime mode switches for OBX PLATIN migration.
 *
 * Provides kill-switch capability for instant rollback to legacy behavior
 * without app reinstallation or data loss.
 *
 * ## Mode Progression (OBX PLATIN Refactor)
 * ```
 * Phase 0-3: LEGACY / LEGACY (default, safe)
 * Phase 4:   DUAL_READ / DUAL_WRITE (validation)
 * Phase 5:   NX_ONLY / NX_ONLY (target state)
 * Phase 6:   NX_ONLY / NX_ONLY (cleanup legacy)
 * ```
 *
 * ## Kill-Switch Usage
 * ```kotlin
 * // Emergency rollback
 * catalogModePreferences.rollbackToLegacy()
 *
 * // Check current mode
 * when (catalogModePreferences.readMode) {
 *     CatalogReadMode.LEGACY -> // read from Obx*
 *     CatalogReadMode.NX_ONLY -> // read from NX_*
 *     CatalogReadMode.DUAL_READ -> // prefer NX_, fallback Obx*
 * }
 * ```
 *
 * @see contracts/NX_SSOT_CONTRACT.md for full documentation
 * @see contracts/NX_SSOT_CONTRACT.md for SSOT invariants
 */
@Singleton
class CatalogModePreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "CatalogMode"

            // Preference file name
            const val PREFS_NAME = "catalog_mode_prefs"

            // Preference keys
            const val KEY_READ_MODE = "catalog_read_mode"
            const val KEY_WRITE_MODE = "catalog_write_mode"
            const val KEY_MIGRATION_STATE = "migration_state"
            const val KEY_LAST_MODE_CHANGE = "last_mode_change_timestamp"
            const val KEY_NX_UI_VISIBILITY = "nx_ui_visibility"
        }

        private val prefs: SharedPreferences =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )

        // StateFlows for reactive observation
        private val _readModeFlow = MutableStateFlow(readMode)
        private val _writeModeFlow = MutableStateFlow(writeMode)
        private val _migrationStateFlow = MutableStateFlow(migrationState)

        /**
         * Observable read mode changes.
         */
        val readModeFlow: Flow<CatalogReadMode> = _readModeFlow.asStateFlow()

        /**
         * Observable write mode changes.
         */
        val writeModeFlow: Flow<CatalogWriteMode> = _writeModeFlow.asStateFlow()

        /**
         * Observable migration state changes.
         */
        val migrationStateFlow: Flow<MigrationState> = _migrationStateFlow.asStateFlow()

        // =========================================================================
        // Read Mode
        // =========================================================================

        /**
         * Current catalog read mode.
         *
         * Determines where the app reads catalog data from:
         * - LEGACY: Read from Obx* entities only (safe default)
         * - NX_ONLY: Read from NX_* entities only (target state)
         * - DUAL_READ: Read from both, prefer NX_* (validation mode)
         */
        var readMode: CatalogReadMode
            get() {
                val stored = prefs.getString(KEY_READ_MODE, null)
                return if (stored != null) {
                    try {
                        CatalogReadMode.valueOf(stored)
                    } catch (e: IllegalArgumentException) {
                        UnifiedLog.w(TAG, "Invalid read mode '$stored', using default")
                        CatalogReadMode.LEGACY
                    }
                } else {
                    CatalogReadMode.LEGACY
                }
            }
            set(value) {
                val previous = readMode
                prefs
                    .edit()
                    .putString(KEY_READ_MODE, value.name)
                    .putLong(KEY_LAST_MODE_CHANGE, System.currentTimeMillis())
                    .apply()
                _readModeFlow.value = value
                logModeChange("READ", previous, value)
            }

        // =========================================================================
        // Write Mode
        // =========================================================================

        /**
         * Current catalog write mode.
         *
         * Determines where new catalog data is written:
         * - LEGACY: Write to Obx* entities only (safe default)
         * - NX_ONLY: Write to NX_* entities only (target state)
         * - DUAL_WRITE: Write to both for validation
         */
        var writeMode: CatalogWriteMode
            get() {
                val stored = prefs.getString(KEY_WRITE_MODE, null)
                return if (stored != null) {
                    try {
                        CatalogWriteMode.valueOf(stored)
                    } catch (e: IllegalArgumentException) {
                        UnifiedLog.w(TAG, "Invalid write mode '$stored', using default")
                        CatalogWriteMode.LEGACY
                    }
                } else {
                    CatalogWriteMode.LEGACY
                }
            }
            set(value) {
                val previous = writeMode
                prefs
                    .edit()
                    .putString(KEY_WRITE_MODE, value.name)
                    .putLong(KEY_LAST_MODE_CHANGE, System.currentTimeMillis())
                    .apply()
                _writeModeFlow.value = value
                logModeChange("WRITE", previous, value)
            }

        // =========================================================================
        // Migration State
        // =========================================================================

        /**
         * Current migration state.
         *
         * Tracks the progress of the OBX PLATIN migration:
         * - NOT_STARTED: Migration has not begun
         * - IN_PROGRESS: Migration worker is running
         * - COMPLETED: Migration finished successfully
         * - FAILED: Migration encountered errors
         * - ROLLED_BACK: User triggered rollback
         */
        var migrationState: MigrationState
            get() {
                val stored = prefs.getString(KEY_MIGRATION_STATE, null)
                return if (stored != null) {
                    try {
                        MigrationState.valueOf(stored)
                    } catch (e: IllegalArgumentException) {
                        UnifiedLog.w(TAG, "Invalid migration state '$stored', using default")
                        MigrationState.NOT_STARTED
                    }
                } else {
                    MigrationState.NOT_STARTED
                }
            }
            set(value) {
                val previous = migrationState
                prefs
                    .edit()
                    .putString(KEY_MIGRATION_STATE, value.name)
                    .apply()
                _migrationStateFlow.value = value
                UnifiedLog.i(TAG, "Migration state: $previous -> $value")
            }

        // =========================================================================
        // NX UI Visibility
        // =========================================================================

        /**
         * Whether NX data should be visible in debug UI.
         *
         * When true, debug screens show NX_* entity counts and health status.
         * Independent of read/write modes.
         */
        var nxUiVisibility: Boolean
            get() = prefs.getBoolean(KEY_NX_UI_VISIBILITY, false)
            set(value) {
                prefs
                    .edit()
                    .putBoolean(KEY_NX_UI_VISIBILITY, value)
                    .apply()
                UnifiedLog.i(TAG, "NX UI visibility: $value")
            }

        // =========================================================================
        // Timestamps
        // =========================================================================

        /**
         * Timestamp of last mode change.
         *
         * Useful for debugging and rollback tracking.
         */
        val lastModeChangeTimestamp: Long
            get() = prefs.getLong(KEY_LAST_MODE_CHANGE, 0L)

        // =========================================================================
        // Convenience Methods
        // =========================================================================

        /**
         * Check if app is in full legacy mode (both read and write).
         */
        val isFullLegacyMode: Boolean
            get() = readMode == CatalogReadMode.LEGACY && writeMode == CatalogWriteMode.LEGACY

        /**
         * Check if app is in full NX mode (both read and write).
         */
        val isFullNxMode: Boolean
            get() = readMode == CatalogReadMode.NX_ONLY && writeMode == CatalogWriteMode.NX_ONLY

        /**
         * Check if any NX reading is enabled.
         */
        val isNxReadEnabled: Boolean
            get() = readMode != CatalogReadMode.LEGACY

        /**
         * Check if any NX writing is enabled.
         */
        val isNxWriteEnabled: Boolean
            get() = writeMode != CatalogWriteMode.LEGACY

        // =========================================================================
        // Kill-Switch Operations
        // =========================================================================

        /**
         * Emergency rollback to legacy mode.
         *
         * Sets both read and write modes to LEGACY and updates migration state.
         * Use when NX mode causes issues.
         *
         * @see contracts/NX_SSOT_CONTRACT.md
         */
        fun rollbackToLegacy() {
            UnifiedLog.w(TAG, "\uD83D\uDEA8 ROLLBACK TO LEGACY TRIGGERED")

            val previousRead = readMode
            val previousWrite = writeMode

            prefs
                .edit()
                .putString(KEY_READ_MODE, CatalogReadMode.LEGACY.name)
                .putString(KEY_WRITE_MODE, CatalogWriteMode.LEGACY.name)
                .putString(KEY_MIGRATION_STATE, MigrationState.ROLLED_BACK.name)
                .putLong(KEY_LAST_MODE_CHANGE, System.currentTimeMillis())
                .apply()

            _readModeFlow.value = CatalogReadMode.LEGACY
            _writeModeFlow.value = CatalogWriteMode.LEGACY
            _migrationStateFlow.value = MigrationState.ROLLED_BACK

            UnifiedLog.w(TAG, "Rollback complete: READ $previousRead->LEGACY, WRITE $previousWrite->LEGACY")
        }

        /**
         * Activate NX-only mode (post-migration target).
         *
         * Sets both read and write modes to NX_ONLY.
         * Only call after migration is complete and validated.
         */
        fun activateNxOnlyMode() {
            UnifiedLog.i(TAG, "Activating NX_ONLY mode")
            readMode = CatalogReadMode.NX_ONLY
            writeMode = CatalogWriteMode.NX_ONLY
        }

        /**
         * Activate dual mode for validation.
         *
         * Sets read to DUAL_READ and write to DUAL_WRITE.
         * Use during Phase 4 for validation before full NX switch.
         */
        fun activateDualMode() {
            UnifiedLog.i(TAG, "Activating DUAL mode for validation")
            readMode = CatalogReadMode.DUAL_READ
            writeMode = CatalogWriteMode.DUAL_WRITE
        }

        /**
         * Reset all preferences to defaults.
         *
         * Use for testing or complete reset. Clears all stored preferences.
         */
        fun resetToDefaults() {
            UnifiedLog.w(TAG, "Resetting all catalog mode preferences")
            prefs.edit().clear().apply()
            _readModeFlow.value = CatalogReadMode.LEGACY
            _writeModeFlow.value = CatalogWriteMode.LEGACY
            _migrationStateFlow.value = MigrationState.NOT_STARTED
        }

        // =========================================================================
        // Logging
        // =========================================================================

        private fun logModeChange(
            type: String,
            previous: Any,
            current: Any,
        ) {
            if (previous != current) {
                UnifiedLog.i(TAG, "Mode change: $type $previous -> $current")
            }
        }

        /**
         * Log current configuration for debugging.
         */
        fun logCurrentConfig() {
            UnifiedLog.i(
                TAG,
                buildString {
                    appendLine("=== Catalog Mode Configuration ===")
                    appendLine("Read Mode: $readMode")
                    appendLine("Write Mode: $writeMode")
                    appendLine("Migration State: $migrationState")
                    appendLine("NX UI Visibility: $nxUiVisibility")
                    appendLine("Last Mode Change: $lastModeChangeTimestamp")
                    appendLine("Is Full Legacy: $isFullLegacyMode")
                    appendLine("Is Full NX: $isFullNxMode")
                    appendLine("==================================")
                },
            )
        }
    }

/**
 * Catalog read mode for OBX PLATIN migration.
 *
 * @see CatalogModePreferences.readMode
 */
enum class CatalogReadMode {
    /**
     * Read from Obx* entities only.
     * Safe default, no NX dependency.
     */
    LEGACY,

    /**
     * Read from NX_* entities only.
     * Target state after migration.
     */
    NX_ONLY,

    /**
     * Read from both, prefer NX_*.
     * Validation mode during migration.
     */
    DUAL_READ,
}

/**
 * Catalog write mode for OBX PLATIN migration.
 *
 * @see CatalogModePreferences.writeMode
 */
enum class CatalogWriteMode {
    /**
     * Write to Obx* entities only.
     * Safe default, no NX writes.
     */
    LEGACY,

    /**
     * Write to NX_* entities only.
     * Target state after migration.
     */
    NX_ONLY,

    /**
     * Write to both for validation.
     * Use during migration for data verification.
     */
    DUAL_WRITE,
}

/**
 * Migration state for OBX PLATIN refactor.
 *
 * @see CatalogModePreferences.migrationState
 */
enum class MigrationState {
    /**
     * Migration has not begun.
     */
    NOT_STARTED,

    /**
     * Migration worker is running.
     */
    IN_PROGRESS,

    /**
     * Migration finished successfully.
     */
    COMPLETED,

    /**
     * Migration encountered errors.
     */
    FAILED,

    /**
     * User triggered rollback to legacy.
     */
    ROLLED_BACK,
}
