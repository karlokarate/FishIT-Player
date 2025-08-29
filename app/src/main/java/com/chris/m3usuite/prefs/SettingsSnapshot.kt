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

val Context.settingsBackupDataStore by preferencesDataStore(name = "settings")

object SettingsSnapshot {
    suspend fun dump(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        val prefs = context.settingsBackupDataStore.data.first()
        prefs.asMap().mapKeys { it.key.name }.mapValues { (_, v) -> v?.toString() ?: "" }
    }

    suspend fun restore(context: Context, values: Map<String, String>, replace: Boolean) = withContext(Dispatchers.IO) {
        context.settingsBackupDataStore.edit { prefs ->
            if (replace) prefs.clear()
            for ((name, s) in values) {
                when {
                    s.equals("true", true) || s.equals("false", true) -> prefs[booleanPreferencesKey(name)] = s.equals("true", true)
                    s.toLongOrNull() != null -> {
                        val lv = s.toLong()
                        if (lv in Int.MIN_VALUE..Int.MAX_VALUE) prefs[intPreferencesKey(name)] = lv.toInt() else prefs[longPreferencesKey(name)] = lv
                    }
                    s.toFloatOrNull() != null -> prefs[floatPreferencesKey(name)] = s.toFloat()
                    else -> prefs[stringPreferencesKey(name)] = s
                }
            }
        }
    }
}

