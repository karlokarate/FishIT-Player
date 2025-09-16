package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxProfile
import com.chris.m3usuite.data.obx.ObxProfilePermissions
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class Permissions(
    val canOpenSettings: Boolean,
    val canChangeSources: Boolean,
    val canUseExternalPlayer: Boolean,
    val canEditFavorites: Boolean,
    val canSearch: Boolean,
    val canSeeResume: Boolean,
    val canEditWhitelist: Boolean
)

class PermissionRepository(private val context: Context, private val settings: SettingsStore) {
    private val box get() = ObxStore.get(context)

    private fun defaultsFor(type: String): Permissions = when (type) {
        "adult" -> Permissions(
            canOpenSettings = true,
            canChangeSources = true,
            canUseExternalPlayer = true,
            canEditFavorites = true,
            canSearch = true,
            canSeeResume = true,
            canEditWhitelist = true
        )
        "guest" -> Permissions(
            canOpenSettings = false,
            canChangeSources = false,
            canUseExternalPlayer = false,
            canEditFavorites = false,
            canSearch = true,
            canSeeResume = false,
            canEditWhitelist = false
        )
        else -> /* kid */ Permissions(
            canOpenSettings = false,
            canChangeSources = false,
            canUseExternalPlayer = false,
            canEditFavorites = false,
            canSearch = true,
            canSeeResume = true,
            canEditWhitelist = false
        )
    }

    suspend fun current(): Permissions = withContext(Dispatchers.IO) {
        val id = settings.currentProfileId.first()
        if (id <= 0) return@withContext defaultsFor("adult")
        val prof = box.boxFor(ObxProfile::class.java).get(id) ?: return@withContext defaultsFor("adult")
        val permBox = box.boxFor(ObxProfilePermissions::class.java)
        val row = permBox.query(com.chris.m3usuite.data.obx.ObxProfilePermissions_.profileId.equal(id)).build().findFirst()
        if (row == null) {
            // Seed defaults lazily
            val d = defaultsFor(prof.type)
            permBox.put(
                ObxProfilePermissions(
                    profileId = id,
                    canOpenSettings = d.canOpenSettings,
                    canChangeSources = d.canChangeSources,
                    canUseExternalPlayer = d.canUseExternalPlayer,
                    canEditFavorites = d.canEditFavorites,
                    canSearch = d.canSearch,
                    canSeeResume = d.canSeeResume,
                    canEditWhitelist = d.canEditWhitelist
                )
            )
            d
        } else {
            Permissions(
                canOpenSettings = row.canOpenSettings,
                canChangeSources = row.canChangeSources,
                canUseExternalPlayer = row.canUseExternalPlayer,
                canEditFavorites = row.canEditFavorites,
                canSearch = row.canSearch,
                canSeeResume = row.canSeeResume,
                canEditWhitelist = row.canEditWhitelist
            )
        }
    }
}
