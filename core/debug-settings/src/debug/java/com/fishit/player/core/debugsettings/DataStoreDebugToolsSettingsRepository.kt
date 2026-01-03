package com.fishit.player.core.debugsettings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed implementation of [DebugToolsSettingsRepository].
 *
 * **Contract:**
 * - All settings default to false (OFF)
 * - Persisted across app restarts
 * - Thread-safe via DataStore
 * - No global mutable singletons
 *
 * **Keys:**
 * - debug.networkInspectorEnabled (default: false)
 * - debug.leakCanaryEnabled (default: false)
 */
@Singleton
class DataStoreDebugToolsSettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DebugToolsSettingsRepository {
        private val Context.dataStore: DataStore<Preferences> by
            preferencesDataStore(name = "debug_tools_settings")

        override val networkInspectorEnabledFlow: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[KEY_NETWORK_INSPECTOR_ENABLED] ?: DEFAULT_NETWORK_INSPECTOR_ENABLED
            }

        override val leakCanaryEnabledFlow: Flow<Boolean> =
            context.dataStore.data.map { prefs ->
                prefs[KEY_LEAK_CANARY_ENABLED] ?: DEFAULT_LEAK_CANARY_ENABLED
            }

        override suspend fun setNetworkInspectorEnabled(enabled: Boolean) {
            context.dataStore.edit { prefs ->
                prefs[KEY_NETWORK_INSPECTOR_ENABLED] = enabled
            }
            UnifiedLog.i(TAG) { "Network inspector (Chucker) enabled=$enabled" }
        }

        override suspend fun setLeakCanaryEnabled(enabled: Boolean) {
            context.dataStore.edit { prefs ->
                prefs[KEY_LEAK_CANARY_ENABLED] = enabled
            }
            UnifiedLog.i(TAG) { "Leak detection (LeakCanary) enabled=$enabled" }
        }

        private companion object {
            private const val TAG = "DebugToolsSettings"

            // Keys
            private val KEY_NETWORK_INSPECTOR_ENABLED =
                booleanPreferencesKey("debug.networkInspectorEnabled")
            private val KEY_LEAK_CANARY_ENABLED =
                booleanPreferencesKey("debug.leakCanaryEnabled")

            // Defaults (MANDATORY: OFF by default)
            private const val DEFAULT_NETWORK_INSPECTOR_ENABLED = false
            private const val DEFAULT_LEAK_CANARY_ENABLED = false
        }
    }
