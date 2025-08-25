package com.chris.m3usuite.data.db

import androidx.room.*

// -----------------------------------------------------
// Entities
// -----------------------------------------------------

/**
 * Entity für Live / VOD / Serien Einträge
 */
@Entity(
    indices = [
        Index(value = ["type", "streamId"]),
        Index("categoryId"),
        Index("sortTitle")
    ]
)
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                 // "live" | "vod" | "series"
    val streamId: Int?,               // live/vod: stream_id, series: series_id
    val name: String,
    val sortTitle: String,
    val categoryId: String?,
    val categoryName: String?,
    val logo: String?,
    val poster: String?,
    val backdrop: String?,
    val epgChannelId: String?,        // live
    val year: Int?,
    val rating: Double?,
    val durationSecs: Int?,           // vod
    val plot: String?,
    val url: String?,                 // Play-URL (bei Serien null → Episoden separat)
    val extraJson: String?
)

/**
 * Episoden einer Serie
 */
@Entity(
    indices = [
        Index("seriesStreamId"),
        Index("season"),
        Index("episodeNum")
    ]
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesStreamId: Int,
    val episodeId: Int,               // Xtream episode id
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val plot: String?,
    val durationSecs: Int?,
    val containerExt: String?,
    val poster: String?
)

/**
 * Kategorien
 */
@Entity(indices = [Index("kind")])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,                 // "live" | "vod" | "series"
    val categoryId: String,
    val categoryName: String
)

/**
 * Resume-Markierung (Weiter schauen)
 * - VOD: type='vod', mediaId gesetzt, episodeId = NULL
 * - Serie: type='series', episodeId gesetzt
 */
@Entity(
    indices = [
        Index(value = ["type", "mediaId", "episodeId"], unique = true)
    ]
)
data class ResumeMark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                   // "vod" | "series"
    val mediaId: Long?,                 // VOD MediaItem.id
    val episodeId: Int?,                // Serien: Xtream Episode ID
    val positionSecs: Int,
    val updatedAt: Long
)

// -----------------------------------------------------
// View-Models (nur für Queries, keine Tabellen!)
// -----------------------------------------------------

/** View-Model für VOD-Resume in Resume-Carousel */
data class ResumeVodView(
    val mediaId: Long,
    val name: String,
    val poster: String?,
    val url: String?,
    val positionSecs: Int,
    val updatedAt: Long
)

/** View-Model für Episoden-Resume in Resume-Carousel */
data class ResumeEpisodeView(
    val episodeId: Int,
    val title: String,
    val season: Int,
    val episodeNum: Int,
    val poster: String?,
    val containerExt: String?,
    val seriesStreamId: Int,
    val positionSecs: Int,
    val updatedAt: Long
)

// -----------------------------------------------------
// DAOs
// -----------------------------------------------------

/** DAO: Media */
@Dao
interface MediaDao {
    @Query("DELETE FROM MediaItem WHERE type=:type")
    suspend fun clearType(type: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItem>)

    // Stabile Alias-Methode, damit der bestehende Repo-Code mit upsertALL(...) weiter baut.
    @Suppress("FunctionName")
    suspend fun upsertALL(items: List<MediaItem>) = upsertAll(items)

    @Query("SELECT * FROM MediaItem WHERE type=:type ORDER BY sortTitle LIMIT :limit OFFSET :offset")
    suspend fun listByType(type: String, limit: Int, offset: Int): List<MediaItem>

    @Query("SELECT DISTINCT categoryName FROM MediaItem WHERE type=:type ORDER BY categoryName")
    suspend fun categoriesByType(type: String): List<String?>

    @Query("SELECT * FROM MediaItem WHERE id=:id")
    suspend fun byId(id: Long): MediaItem?

    @Query("SELECT * FROM MediaItem WHERE type=:type AND (:cat IS NULL OR categoryName=:cat) ORDER BY sortTitle")
    suspend fun byTypeAndCategory(type: String, cat: String?): List<MediaItem>

    @Query("SELECT * FROM MediaItem WHERE name LIKE '%' || :query || '%' ORDER BY sortTitle LIMIT :limit OFFSET :offset")
    suspend fun globalSearch(query: String, limit: Int, offset: Int): List<MediaItem>
}

/** DAO: Episoden */
@Dao
interface EpisodeDao {
    @Query("DELETE FROM Episode WHERE seriesStreamId=:seriesId")
    suspend fun clearForSeries(seriesId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Episode>)

    @Query("SELECT DISTINCT season FROM Episode WHERE seriesStreamId=:seriesId ORDER BY season")
    suspend fun seasons(seriesId: Int): List<Int>

    @Query("SELECT * FROM Episode WHERE seriesStreamId=:seriesId AND season=:season ORDER BY episodeNum")
    suspend fun episodes(seriesId: Int, season: Int): List<Episode>
}

/** DAO: Kategorien */
@Dao
interface CategoryDao {
    @Query("DELETE FROM Category WHERE kind=:kind")
    suspend fun clear(kind: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Category>)
}

/** DAO: Resume / Weiter schauen */
@Dao
interface ResumeDao {
    // Einzelne Abfragen
    @Query("SELECT * FROM ResumeMark WHERE type='vod' AND mediaId=:mediaId LIMIT 1")
    suspend fun getVod(mediaId: Long): ResumeMark?

    @Query("SELECT * FROM ResumeMark WHERE type='series' AND episodeId=:episodeId LIMIT 1")
    suspend fun getEpisode(episodeId: Int): ResumeMark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mark: ResumeMark)

    @Query("DELETE FROM ResumeMark WHERE type='vod' AND mediaId=:mediaId")
    suspend fun clearVod(mediaId: Long)

    @Query("DELETE FROM ResumeMark WHERE type='series' AND episodeId=:episodeId")
    suspend fun clearEpisode(episodeId: Int)

    // „Weiter schauen“-Listen
    @Query("""
        SELECT 
            m.id AS mediaId,
            m.name AS name,
            m.poster AS poster,
            m.url AS url,
            r.positionSecs AS positionSecs,
            r.updatedAt AS updatedAt
        FROM ResumeMark r
        JOIN MediaItem m ON r.type='vod' AND r.mediaId = m.id
        ORDER BY r.updatedAt DESC
        LIMIT :limit
    """)
    suspend fun recentVod(limit: Int): List<ResumeVodView>

    @Query("""
        SELECT 
            e.episodeId AS episodeId,
            e.title AS title,
            e.season AS season,
            e.episodeNum AS episodeNum,
            e.poster AS poster,
            e.containerExt AS containerExt,
            e.seriesStreamId AS seriesStreamId,
            r.positionSecs AS positionSecs,
            r.updatedAt AS updatedAt
        FROM ResumeMark r
        JOIN Episode e ON r.type='series' AND r.episodeId = e.episodeId
        ORDER BY r.updatedAt DESC
        LIMIT :limit
    """)
    suspend fun recentEpisodes(limit: Int): List<ResumeEpisodeView>
}
