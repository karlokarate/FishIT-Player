package com.chris.m3usuite.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// Removed separate DataStore instance to avoid "multiple DataStores active" crash.

object SettingsSnapshot {
    suspend fun dump(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        SettingsStore(context).dumpAll()
    }

    suspend fun restore(context: Context, values: Map<String, String>, replace: Boolean) = withContext(Dispatchers.IO) {
        SettingsStore(context).restoreAll(values, replace)
    }
}
