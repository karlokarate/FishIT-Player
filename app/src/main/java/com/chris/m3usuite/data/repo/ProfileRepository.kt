package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.Profile
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ProfileRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    private val db get() = DbProvider.get(context)

    suspend fun currentProfile(): Profile? = withContext(Dispatchers.IO) {
        val id = settings.currentProfileId.first()
        if (id <= 0) null else db.profileDao().byId(id)
    }

    suspend fun currentProfileIdOrAdult(): Long = withContext(Dispatchers.IO) {
        val id = settings.currentProfileId.first()
        if (id > 0) return@withContext id
        val adult = db.profileDao().all().firstOrNull { it.type == "adult" }
        adult?.id ?: -1L
    }

    suspend fun isKidProfile(): Boolean = withContext(Dispatchers.IO) {
        val prof = currentProfile()
        prof?.type == "kid"
    }
}

