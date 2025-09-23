package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxResumeMark
import com.chris.m3usuite.data.obx.ObxResumeMark_
import com.chris.m3usuite.data.obx.ObxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResumeRepository(private val context: Context) {
    private val store get() = ObxStore.get(context)

    suspend fun setVodResume(mediaId: Long, positionSecs: Int) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val b = store.boxFor(ObxResumeMark::class.java)
        store.runInTx {
            val row = b.query(
                ObxResumeMark_.type.equal("vod").and(ObxResumeMark_.mediaEncodedId.equal(mediaId))
            ).build().findFirst() ?: ObxResumeMark(type = "vod", mediaEncodedId = mediaId)
            row.positionSecs = positionSecs.coerceAtLeast(0)
            row.updatedAt = now
            b.put(row)
        }
    }

    suspend fun setSeriesResume(seriesId: Int, season: Int, episodeNum: Int, positionSecs: Int) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val b = store.boxFor(ObxResumeMark::class.java)
            store.runInTx {
                val row = b.query(
                    ObxResumeMark_.type.equal("series")
                        .and(ObxResumeMark_.seriesId.equal(seriesId.toLong()))
                        .and(ObxResumeMark_.season.equal(season.toLong()))
                        .and(ObxResumeMark_.episodeNum.equal(episodeNum.toLong()))
                ).build().findFirst() ?: ObxResumeMark(
                    type = "series",
                    seriesId = seriesId,
                    season = season,
                    episodeNum = episodeNum
                )
                row.positionSecs = positionSecs.coerceAtLeast(0)
                row.updatedAt = now
                b.put(row)
            }
        }

    suspend fun setEpisodeResume(episodeId: Int, positionSecs: Int) = withContext(Dispatchers.IO) {
        // OBX: bewusst no-op (keine stabile episodeId in diesem Pfad)
    }

    suspend fun clearVod(mediaId: Long) = withContext(Dispatchers.IO) {
        val b = store.boxFor(ObxResumeMark::class.java)
        store.runInTx {
            b.query(ObxResumeMark_.type.equal("vod").and(ObxResumeMark_.mediaEncodedId.equal(mediaId)))
                .build().findFirst()?.let { b.remove(it) }
        }
    }

    suspend fun clearEpisode(episodeId: Int) = withContext(Dispatchers.IO) {
        // no-op für OBX
    }

    suspend fun clearSeriesResume(seriesId: Int, season: Int, episodeNum: Int) =
        withContext(Dispatchers.IO) {
            val b = store.boxFor(ObxResumeMark::class.java)
            store.runInTx {
                b.query(
                    ObxResumeMark_.type.equal("series")
                        .and(ObxResumeMark_.seriesId.equal(seriesId.toLong()))
                        .and(ObxResumeMark_.season.equal(season.toLong()))
                        .and(ObxResumeMark_.episodeNum.equal(episodeNum.toLong()))
                ).build().findFirst()?.let { b.remove(it) }
            }
        }

    data class ResumeVodView(val mediaId: Long, val positionSecs: Int, val updatedAt: Long)
    data class ResumeEpisodeView(val seriesId: Int, val season: Int, val episodeNum: Int, val positionSecs: Int, val updatedAt: Long)

    suspend fun recentVod(limit: Int): List<ResumeVodView> = withContext(Dispatchers.IO) {
        val b = store.boxFor(ObxResumeMark::class.java)
        b.query(ObxResumeMark_.type.equal("vod"))
            .orderDesc(ObxResumeMark_.updatedAt)
            .build().find(0, limit.toLong()).mapNotNull {
                val id = it.mediaEncodedId ?: return@mapNotNull null
                ResumeVodView(mediaId = id, positionSecs = it.positionSecs, updatedAt = it.updatedAt)
            }
    }

    suspend fun recentEpisodes(limit: Int): List<ResumeEpisodeView> = withContext(Dispatchers.IO) {
        val b = store.boxFor(ObxResumeMark::class.java)
        b.query(ObxResumeMark_.type.equal("series"))
            .orderDesc(ObxResumeMark_.updatedAt)
            .build().find(0, limit.toLong()).mapNotNull { r ->
                val sid = r.seriesId ?: return@mapNotNull null
                val s   = r.season ?: return@mapNotNull null
                val e   = r.episodeNum ?: return@mapNotNull null
                ResumeEpisodeView(seriesId = sid, season = s, episodeNum = e, positionSecs = r.positionSecs, updatedAt = r.updatedAt)
            }
    }

    suspend fun getSeriesResume(seriesId: Int, season: Int, episodeNum: Int): Int? =
        withContext(Dispatchers.IO) {
            val b = store.boxFor(ObxResumeMark::class.java)
            b.query(
                ObxResumeMark_.type.equal("series")
                    .and(ObxResumeMark_.seriesId.equal(seriesId.toLong()))
                    .and(ObxResumeMark_.season.equal(season.toLong()))
                    .and(ObxResumeMark_.episodeNum.equal(episodeNum.toLong()))
            ).build().findFirst()?.positionSecs
        }

    suspend fun getVodResume(mediaId: Long): Int? = withContext(Dispatchers.IO) {
        val b = store.boxFor(ObxResumeMark::class.java)
        b.query(ObxResumeMark_.type.equal("vod").and(ObxResumeMark_.mediaEncodedId.equal(mediaId)))
            .build().findFirst()?.positionSecs
    }
}
