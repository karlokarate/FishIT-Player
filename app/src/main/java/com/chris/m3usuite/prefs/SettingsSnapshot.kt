package com.chris.m3usuite.prefs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Removed separate DataStore instance to avoid "multiple DataStores active" crash.

object SettingsSnapshot {
    suspend fun dump(context: Context): Map<String, String> =
        withContext(Dispatchers.IO) {
            SettingsStore(context).dumpAll()
        }

    suspend fun restore(
        context: Context,
        values: Map<String, String>,
        replace: Boolean,
    ) = withContext(Dispatchers.IO) {
        SettingsStore(context).restoreAll(values, replace)
    }
}
