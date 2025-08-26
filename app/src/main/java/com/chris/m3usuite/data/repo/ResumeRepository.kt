package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.ResumeEpisodeView
import com.chris.m3usuite.data.db.ResumeMark
import com.chris.m3usuite.data.db.ResumeVodView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResumeRepository(private val context: Context) {
    private val db get() = DbProvider.get(context)

    suspend fun setVodResume(mediaId: Long, positionSecs: Int) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.resumeDao().upsert(
            ResumeMark(
                type = "vod",
                mediaId = mediaId,
                episodeId = null,
                positionSecs = positionSecs.coerceAtLeast(0),
                updatedAt = now
            )
        )
    }

    suspend fun setEpisodeResume(episodeId: Int, positionSecs: Int) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.resumeDao().upsert(
            ResumeMark(
                type = "series",
                mediaId = null,
                episodeId = episodeId,
                positionSecs = positionSecs.coerceAtLeast(0),
                updatedAt = now
            )
        )
    }

    suspend fun clearVod(mediaId: Long) = withContext(Dispatchers.IO) { db.resumeDao().clearVod(mediaId) }
    suspend fun clearEpisode(episodeId: Int) = withContext(Dispatchers.IO) { db.resumeDao().clearEpisode(episodeId) }

    suspend fun recentVod(limit: Int): List<ResumeVodView> = withContext(Dispatchers.IO) { db.resumeDao().recentVod(limit) }
    suspend fun recentEpisodes(limit: Int): List<ResumeEpisodeView> = withContext(Dispatchers.IO) { db.resumeDao().recentEpisodes(limit) }
}

