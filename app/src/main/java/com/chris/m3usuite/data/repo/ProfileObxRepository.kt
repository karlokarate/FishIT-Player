package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxProfile
import com.chris.m3usuite.data.obx.ObxProfilePermissions
import com.chris.m3usuite.data.obx.ObxProfilePermissions_
import com.chris.m3usuite.data.obx.ObxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileObxRepository(
    private val context: Context,
) {
    private val store get() = ObxStore.get(context)

    suspend fun all(): List<ObxProfile> =
        withContext(Dispatchers.IO) {
            store.boxFor(ObxProfile::class.java).all
        }

    suspend fun byId(id: Long): ObxProfile? =
        withContext(Dispatchers.IO) {
            store.boxFor(ObxProfile::class.java).get(id)
        }

    suspend fun insert(p: ObxProfile): Long =
        withContext(Dispatchers.IO) {
            store.boxFor(ObxProfile::class.java).put(p)
        }

    suspend fun update(p: ObxProfile) =
        withContext(Dispatchers.IO) {
            store.boxFor(ObxProfile::class.java).put(p)
        }

    suspend fun delete(p: ObxProfile) =
        withContext(Dispatchers.IO) {
            val boxProfile = store.boxFor(ObxProfile::class.java)
            val boxPerm = store.boxFor(ObxProfilePermissions::class.java)
            // Atomar entfernen
            store.runInTx {
                // Permissions passend zum Profil entfernen
                val perm = boxPerm.query(ObxProfilePermissions_.profileId.equal(p.id)).build().find()
                if (perm.isNotEmpty()) boxPerm.remove(perm)
                boxProfile.remove(p)
            }
        }
}
