package com.chris.m3usuite.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile

/**
 * Singleton provider for the settings DataStore.
 *
 * This ensures only ONE DataStore instance exists for settings.preferences_pb,
 * preventing the "multiple DataStores active for the same file" error.
 *
 * Usage:
 * ```kotlin
 * val dataStore = SettingsDataStoreProvider.getInstance(context)
 * ```
 */
object SettingsDataStoreProvider {
    @Volatile
    private var INSTANCE: DataStore<Preferences>? = null

    /**
     * Get the singleton DataStore instance for settings.
     *
     * Thread-safe initialization using double-checked locking.
     */
    fun getInstance(context: Context): DataStore<Preferences> =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: createDataStore(context.applicationContext).also {
                INSTANCE = it
            }
        }

    private fun createDataStore(context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = {
                context.preferencesDataStoreFile("settings")
            },
        )
}
