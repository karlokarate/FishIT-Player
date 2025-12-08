package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxProfile
import com.chris.m3usuite.data.obx.ObxProfilePermissions
import com.chris.m3usuite.data.obx.ObxProfilePermissions_
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
    val canEditWhitelist: Boolean,
)

class PermissionRepository(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val store get() = ObxStore.get(context)

    private fun defaultsFor(type: String): Permissions =
        when (type) {
            "adult" -> Permissions(true, true, true, true, true, true, true)
            "guest" -> Permissions(false, false, false, false, true, false, false)
            else -> Permissions(false, false, false, false, true, true, false) // kid
        }

    suspend fun current(): Permissions =
        withContext(Dispatchers.IO) {
            val id = settings.currentProfileId.first()
            if (id <= 0) return@withContext defaultsFor("adult")
            val prof = store.boxFor(ObxProfile::class.java).get(id) ?: return@withContext defaultsFor("adult")

            val permBox = store.boxFor(ObxProfilePermissions::class.java)
            val row = permBox.query(ObxProfilePermissions_.profileId.equal(id)).build().findFirst()
            if (row == null) {
                // Lazy-Seed (idempotent)
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
                        canEditWhitelist = d.canEditWhitelist,
                    ),
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
                    canEditWhitelist = row.canEditWhitelist,
                )
            }
        }
}
