package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.KidContentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KidContentRepository(private val context: Context) {
    private val db get() = DbProvider.get(context)

    suspend fun allow(kidId: Long, type: String, contentId: Long) = withContext(Dispatchers.IO) {
        db.kidContentDao().insert(KidContentItem(kidProfileId = kidId, contentType = type, contentId = contentId))
    }

    suspend fun disallow(kidId: Long, type: String, contentId: Long) = withContext(Dispatchers.IO) {
        db.kidContentDao().disallow(kidId, type, contentId)
    }

    suspend fun isAllowed(kidId: Long, type: String, contentId: Long): Boolean = withContext(Dispatchers.IO) {
        db.kidContentDao().isAllowedCount(kidId, type, contentId) > 0
    }

    suspend fun allowBulk(kidId: Long, type: String, contentIds: Collection<Long>) = withContext(Dispatchers.IO) {
        val dao = db.kidContentDao()
        for (id in contentIds) dao.insert(KidContentItem(kidProfileId = kidId, contentType = type, contentId = id))
    }

    suspend fun disallowBulk(kidId: Long, type: String, contentIds: Collection<Long>) = withContext(Dispatchers.IO) {
        val dao = db.kidContentDao()
        for (id in contentIds) dao.disallow(kidId, type, id)
    }
}

