package com.fishit.player.core.debugsettings.nx

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed implementation of [NxMigrationSettingsRepository].
 *
 * **Contract:**
 * - All settings default to conservative values (LEGACY_ONLY, OFF, HIDDEN)
 * - Persisted across app restarts
 * - Thread-safe via DataStore
 * - No global mutable singletons
 *
 * **Keys:**
 * - nx.catalogReadMode (default: LEGACY_ONLY)
 * - nx.catalogWriteMode (default: LEGACY_ONLY)
 * - nx.migrationMode (default: OFF)
 * - nx.uiVisibility (default: HIDDEN)
 *
 * **Reference:** docs/v2/NX_SSOT_CONTRACT.md, docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
 */
@Singleton
class DataStoreNxMigrationSettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NxMigrationSettingsRepository {
        private val Context.dataStore: DataStore<Preferences> by
            preferencesDataStore(name = "nx_migration_settings")

        // ═══════════════════════════════════════════════════════════════════════
        // Read Mode
        // ═══════════════════════════════════════════════════════════════════════

        override val catalogReadModeFlow: Flow<CatalogReadMode> =
            context.dataStore.data.map { prefs ->
                prefs[KEY_CATALOG_READ_MODE]
                    ?.let { enumValueOfOrNull<CatalogReadMode>(it) }
                    ?: DEFAULT_CATALOG_READ_MODE
            }

        override suspend fun setCatalogReadMode(mode: CatalogReadMode) {
            context.dataStore.edit { prefs ->
                prefs[KEY_CATALOG_READ_MODE] = mode.name
            }
            UnifiedLog.i(TAG) { "Catalog read mode set to: $mode" }
        }

        override suspend fun getCatalogReadMode(): CatalogReadMode = catalogReadModeFlow.first()

        // ═══════════════════════════════════════════════════════════════════════
        // Write Mode
        // ═══════════════════════════════════════════════════════════════════════

        override val catalogWriteModeFlow: Flow<CatalogWriteMode> =
            context.dataStore.data.map { prefs ->
                prefs[KEY_CATALOG_WRITE_MODE]
                    ?.let { enumValueOfOrNull<CatalogWriteMode>(it) }
                    ?: DEFAULT_CATALOG_WRITE_MODE
            }

        override suspend fun setCatalogWriteMode(mode: CatalogWriteMode) {
            context.dataStore.edit { prefs ->
                prefs[KEY_CATALOG_WRITE_MODE] = mode.name
            }
            UnifiedLog.i(TAG) { "Catalog write mode set to: $mode" }
        }

        override suspend fun getCatalogWriteMode(): CatalogWriteMode = catalogWriteModeFlow.first()

        // ═══════════════════════════════════════════════════════════════════════
        // Migration Mode
        // ═══════════════════════════════════════════════════════════════════════

        override val migrationModeFlow: Flow<MigrationMode> =
            context.dataStore.data.map { prefs ->
                prefs[KEY_MIGRATION_MODE]
                    ?.let { enumValueOfOrNull<MigrationMode>(it) }
                    ?: DEFAULT_MIGRATION_MODE
            }

        override suspend fun setMigrationMode(mode: MigrationMode) {
            context.dataStore.edit { prefs ->
                prefs[KEY_MIGRATION_MODE] = mode.name
            }
            UnifiedLog.i(TAG) { "Migration mode set to: $mode" }
        }

        override suspend fun getMigrationMode(): MigrationMode = migrationModeFlow.first()

        // ═══════════════════════════════════════════════════════════════════════
        // UI Visibility
        // ═══════════════════════════════════════════════════════════════════════

        override val nxUiVisibilityFlow: Flow<NxUiVisibility> =
            context.dataStore.data.map { prefs ->
                prefs[KEY_NX_UI_VISIBILITY]
                    ?.let { enumValueOfOrNull<NxUiVisibility>(it) }
                    ?: DEFAULT_NX_UI_VISIBILITY
            }

        override suspend fun setNxUiVisibility(visibility: NxUiVisibility) {
            context.dataStore.edit { prefs ->
                prefs[KEY_NX_UI_VISIBILITY] = visibility.name
            }
            UnifiedLog.i(TAG) { "NX UI visibility set to: $visibility" }
        }

        override suspend fun getNxUiVisibility(): NxUiVisibility = nxUiVisibilityFlow.first()

        // ═══════════════════════════════════════════════════════════════════════
        // Helpers
        // ═══════════════════════════════════════════════════════════════════════

        private inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String): T? =
            try {
                enumValueOf<T>(name)
            } catch (_: IllegalArgumentException) {
                UnifiedLog.w(TAG) { "Unknown enum value '$name' for ${T::class.simpleName}, using default" }
                null
            }

        private companion object {
            private const val TAG = "NxMigrationSettings"

            // Keys
            private val KEY_CATALOG_READ_MODE =
                stringPreferencesKey("nx.catalogReadMode")
            private val KEY_CATALOG_WRITE_MODE =
                stringPreferencesKey("nx.catalogWriteMode")
            private val KEY_MIGRATION_MODE =
                stringPreferencesKey("nx.migrationMode")
            private val KEY_NX_UI_VISIBILITY =
                stringPreferencesKey("nx.uiVisibility")

            // Defaults (MANDATORY: Conservative values for Phase 0)
            private val DEFAULT_CATALOG_READ_MODE = CatalogReadMode.LEGACY_ONLY
            private val DEFAULT_CATALOG_WRITE_MODE = CatalogWriteMode.LEGACY_ONLY
            private val DEFAULT_MIGRATION_MODE = MigrationMode.OFF
            private val DEFAULT_NX_UI_VISIBILITY = NxUiVisibility.HIDDEN
        }
    }
