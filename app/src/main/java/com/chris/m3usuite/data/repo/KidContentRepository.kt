package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.objectbox.Box
import kotlin.math.min

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
        if (contentIds.isEmpty()) return@withContext
        val b = box.boxFor(ObxKidContentAllow::class.java)
        val rows = contentIds.map { id -> ObxKidContentAllow(kidProfileId = kidId, contentType = type, contentId = id) }
        b.putChunked(rows, 2000)
    }

    suspend fun disallowBulk(kidId: Long, type: String, contentIds: Collection<Long>) = withContext(Dispatchers.IO) {
        if (contentIds.isEmpty()) return@withContext
        val b = box.boxFor(ObxKidContentAllow::class.java)
        // Eingrenzen auf kind+type, dann in-memory Filter – und chunked remove
        val all = b.query(ObxKidContentAllow_.kidProfileId.equal(kidId).and(ObxKidContentAllow_.contentType.equal(type))).build().find()
        if (all.isEmpty()) return@withContext
        val toRemove = all.filter { it.contentId in contentIds }
        if (toRemove.isEmpty()) return@withContext
        var i = 0
        while (i < toRemove.size) {
            val to = min(i + 2000, toRemove.size)
            b.remove(toRemove.subList(i, to))
            i = to
        }
    }
}

private fun <T> Box<T>.putChunked(items: List<T>, chunkSize: Int = 2000) {
    var i = 0
    val n = items.size
    while (i < n) {
        val to = min(i + chunkSize, n)
        this.put(items.subList(i, to))
        i = to
    }
}
