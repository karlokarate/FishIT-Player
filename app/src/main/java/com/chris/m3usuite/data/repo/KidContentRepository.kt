package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KidContentRepository(private val context: Context) {
    private val box get() = ObxStore.get(context)

    suspend fun allow(kidId: Long, type: String, contentId: Long) = withContext(Dispatchers.IO) {
        box.boxFor(ObxKidContentAllow::class.java).put(ObxKidContentAllow(kidProfileId = kidId, contentType = type, contentId = contentId))
    }

    suspend fun disallow(kidId: Long, type: String, contentId: Long) = withContext(Dispatchers.IO) {
        val b = box.boxFor(ObxKidContentAllow::class.java)
        val rows = b.query(ObxKidContentAllow_.kidProfileId.equal(kidId).and(ObxKidContentAllow_.contentType.equal(type)).and(ObxKidContentAllow_.contentId.equal(contentId))).build().find()
        if (rows.isNotEmpty()) b.remove(rows)
    }

    suspend fun isAllowed(kidId: Long, type: String, contentId: Long): Boolean = withContext(Dispatchers.IO) {
        val b = box.boxFor(ObxKidContentAllow::class.java)
        b.query(ObxKidContentAllow_.kidProfileId.equal(kidId).and(ObxKidContentAllow_.contentType.equal(type)).and(ObxKidContentAllow_.contentId.equal(contentId))).build().count() > 0
    }

    suspend fun allowBulk(kidId: Long, type: String, contentIds: Collection<Long>) = withContext(Dispatchers.IO) {
        val b = box.boxFor(ObxKidContentAllow::class.java)
        val rows = contentIds.map { id -> ObxKidContentAllow(kidProfileId = kidId, contentType = type, contentId = id) }
        if (rows.isNotEmpty()) b.put(rows)
    }

    suspend fun disallowBulk(kidId: Long, type: String, contentIds: Collection<Long>) = withContext(Dispatchers.IO) {
        val b = box.boxFor(ObxKidContentAllow::class.java)
        val q = b.query(ObxKidContentAllow_.kidProfileId.equal(kidId).and(ObxKidContentAllow_.contentType.equal(type))).build()
        val all = q.find()
        val toRemove = all.filter { it.contentId in contentIds }
        if (toRemove.isNotEmpty()) b.remove(toRemove)
    }
}
