package com.chris.m3usuite.data.db

import androidx.room.*
import androidx.paging.PagingSource
import androidx.room.Fts4
import androidx.room.FtsOptions

// -----------------------------------------------------
// Entities
// (Phase 1: profiles, kid_content, screen_time with required UNIQUE constraints; Resume unique if present)
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
    val extraJson: String?,
    // Source + Telegram references (default off unless imported)
    val source: String = "M3U",      // "M3U" | "TG" | ...
    val tgChatId: Long? = null,
    val tgMessageId: Long? = null,
    val tgFileId: Int? = null
)

/** Telegram message index for playback and metadata bridge */
@Entity(
    tableName = "telegram_messages",
    primaryKeys = ["chatId", "messageId"],
    indices = [Index("fileId"), Index("fileUniqueId")]
)
data class TelegramMessage(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int?,
    val fileUniqueId: String?,
    val supportsStreaming: Boolean?,
    val caption: String?,
    val date: Long?,
    val localPath: String?,
    val thumbFileId: Int?
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
    val poster: String?,
    // Telegram references (optional per-episode mapping)
    val tgChatId: Long? = null,
    val tgMessageId: Long? = null,
    val tgFileId: Int? = null
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

/**
 * Profile: Adult/Kid
 */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,          // "adult" | "kid"
    val avatarPath: String?,   // lokaler Pfad oder null
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Freigaben für Kids: welche Inhalte sind erlaubt?
 * UNIQUE(kidProfileId, contentType, contentId)
 */
@Entity(
    tableName = "kid_content",
    indices = [
        Index(value = ["kidProfileId"]),
        Index(value = ["contentType"]),
        Index(value = ["kidProfileId", "contentType", "contentId"], unique = true)
    ]
)
data class KidContentItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kidProfileId: Long,
    val contentType: String, // "live" | "vod" | "series"
    val contentId: Long
)

/**
 * FTS4-Volltextindex für globale Suche über name und sortTitle.
 * - unicode61 Tokenizer mit remove_diacritics=2 entfernt Akzente.
 * - contentEntity sorgt für automatische Trigger bei Neuaufbau; Migration ergänzt IF NOT EXISTS-Trigger für Bestandsdatenbanken.
 */
@Fts4(
    contentEntity = MediaItem::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=2"]
)
@Entity(tableName = "mediaitem_fts")
data class MediaItemFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val name: String,
    val sortTitle: String
)

/** Kategorie-Freigaben pro Kind und Typ */
@Entity(
    tableName = "kid_category_allow",
    indices = [
        Index(value = ["kidProfileId"]),
        Index(value = ["contentType"]),
        Index(value = ["kidProfileId", "contentType", "categoryId"], unique = true)
    ]
)
data class KidCategoryAllow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kidProfileId: Long,
    val contentType: String, // "live" | "vod" | "series"
    val categoryId: String
)

/** Item-Blocklist (Ausnahmen) pro Kind und Typ */
@Entity(
    tableName = "kid_content_block",
    indices = [
        Index(value = ["kidProfileId"]),
        Index(value = ["contentType"]),
        Index(value = ["kidProfileId", "contentType", "contentId"], unique = true)
    ]
)
data class KidContentBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kidProfileId: Long,
    val contentType: String,
    val contentId: Long
)

/**
 * Screen-Time Tracking je Kid und Tag
 * UNIQUE(kidProfileId, dayYyyymmdd)
 */
@Entity(
    tableName = "screen_time",
    indices = [
        Index(value = ["kidProfileId"]),
        Index(value = ["kidProfileId", "dayYyyymmdd"], unique = true)
    ]
)
data class ScreenTimeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kidProfileId: Long,
    val dayYyyymmdd: String,  // z. B. "20250825"
    val usedMinutes: Int,
    val limitMinutes: Int
)

/**
 * Persistenter EPG-Cache (Now/Next) je tvg-id (XMLTV channel id)
 */
@Entity(tableName = "epg_now_next")
data class EpgNowNext(
    @PrimaryKey val channelId: String,
    val nowTitle: String?,
    val nowStartMs: Long?,
    val nowEndMs: Long?,
    val nextTitle: String?,
    val nextStartMs: Long?,
    val nextEndMs: Long?,
    val updatedAt: Long
)
/** Rechte pro Profil */
@Entity(
    tableName = "profile_permissions",
    indices = [Index(value = ["profileId"], unique = true)]
)
data class ProfilePermissions(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val canOpenSettings: Boolean,
    val canChangeSources: Boolean,
    val canUseExternalPlayer: Boolean,
    val canEditFavorites: Boolean,
    val canSearch: Boolean,
    val canSeeResume: Boolean,
    val canEditWhitelist: Boolean
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

/** Lightweight projection for URL + extraJson to avoid hydrating full rows during imports */
data class MediaUrlExtra(
    val url: String?,
    val extraJson: String?
)

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

    @Query("SELECT * FROM MediaItem WHERE type=:type ORDER BY sortTitle COLLATE NOCASE LIMIT :limit OFFSET :offset")
    suspend fun listByType(type: String, limit: Int, offset: Int): List<MediaItem>

    @Query("SELECT DISTINCT categoryName FROM MediaItem WHERE type=:type ORDER BY categoryName")
    suspend fun categoriesByType(type: String): List<String?>

    @Query("SELECT * FROM MediaItem WHERE id=:id")
    suspend fun byId(id: Long): MediaItem?

        @Query("SELECT * FROM MediaItem WHERE type='series' AND streamId=:sid LIMIT 1")
        suspend fun seriesByStreamId(sid: Int): MediaItem?

        @Query("SELECT * FROM MediaItem WHERE type='live' AND streamId=:sid LIMIT 1")
        suspend fun liveByStreamId(sid: Int): MediaItem?

    @Query("SELECT * FROM MediaItem WHERE type=:type AND (:cat IS NULL OR categoryName=:cat) ORDER BY sortTitle COLLATE NOCASE")
    suspend fun byTypeAndCategory(type: String, cat: String?): List<MediaItem>

    @Query("SELECT id FROM MediaItem WHERE type=:type AND categoryName IN (:cats)")
    suspend fun idsByTypeAndCategories(type: String, cats: List<String>): List<Long>

    @Query("SELECT * FROM MediaItem WHERE name LIKE '%' || :query || '%' ORDER BY sortTitle COLLATE NOCASE LIMIT :limit OFFSET :offset")
    suspend fun globalSearch(query: String, limit: Int, offset: Int): List<MediaItem>

    @Query(
        """
        SELECT m.* FROM MediaItem m
        JOIN mediaitem_fts f ON f.rowid = m.id
        WHERE mediaitem_fts MATCH :ftsQuery
        ORDER BY m.sortTitle COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun globalSearchFts(ftsQuery: String, limit: Int, offset: Int): List<MediaItem>

    // Paging variants
    @Query("SELECT * FROM MediaItem WHERE type=:type ORDER BY sortTitle COLLATE NOCASE")
    fun pagingByType(type: String): PagingSource<Int, MediaItem>

    @Query("SELECT * FROM MediaItem WHERE type=:type AND (:cat IS NULL OR categoryName=:cat) ORDER BY sortTitle COLLATE NOCASE")
    fun pagingByTypeAndCategory(type: String, cat: String?): PagingSource<Int, MediaItem>

    @Query(
        """
        SELECT m.* FROM MediaItem m
        JOIN mediaitem_fts f ON f.rowid = m.id
        WHERE mediaitem_fts MATCH :ftsQuery
        ORDER BY m.sortTitle COLLATE NOCASE
        """
    )
    fun pagingSearchFts(ftsQuery: String): PagingSource<Int, MediaItem>

    @Query("SELECT * FROM MediaItem WHERE id IN (:ids) ORDER BY sortTitle COLLATE NOCASE")
    suspend fun byIds(ids: List<Long>): List<MediaItem>

    @Query("SELECT url, extraJson FROM MediaItem WHERE type=:type LIMIT :limit OFFSET :offset")
    suspend fun urlsWithExtraByType(type: String, limit: Int, offset: Int): List<MediaUrlExtra>

    @Query("SELECT * FROM MediaItem WHERE tgChatId=:chatId AND tgMessageId=:messageId LIMIT 1")
    suspend fun byTelegram(chatId: Long, messageId: Long): MediaItem?
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

    @Query("SELECT * FROM Episode WHERE episodeId=:episodeId LIMIT 1")
    suspend fun byEpisodeId(episodeId: Int): Episode?

    @Query("SELECT * FROM Episode WHERE seriesStreamId=:seriesId AND (season>:season OR (season=:season AND episodeNum>:episodeNum)) ORDER BY season, episodeNum LIMIT 1")
    suspend fun nextEpisode(seriesId: Int, season: Int, episodeNum: Int): Episode?
}

/** DAO: EPG Now/Next Cache */
@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_now_next WHERE channelId=:channelId LIMIT 1")
    suspend fun byChannel(channelId: String): EpgNowNext?

    @Query("SELECT * FROM epg_now_next WHERE channelId=:channelId LIMIT 1")
    fun observeByChannel(channelId: String): kotlinx.coroutines.flow.Flow<EpgNowNext?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EpgNowNext>)

    @Query("DELETE FROM epg_now_next WHERE updatedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

/** DAO: Telegram messages */
@Dao
interface TelegramDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TelegramMessage>)

    @Query("SELECT * FROM telegram_messages WHERE chatId=:chatId AND messageId=:messageId LIMIT 1")
    suspend fun byKey(chatId: Long, messageId: Long): TelegramMessage?

    @Query("UPDATE telegram_messages SET localPath=:path WHERE chatId=:chatId AND messageId=:messageId")
    suspend fun updateLocalPath(chatId: Long, messageId: Long, path: String)
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

/** DAO: Profile */
@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    suspend fun all(): List<Profile>

    @Query("SELECT * FROM profiles WHERE id=:id LIMIT 1")
    suspend fun byId(id: Long): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: Profile): Long

    @Update
    suspend fun update(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Long
}

/** DAO: Kid Content (Freigaben) */
@Dao
interface KidContentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: KidContentItem): Long

    @Delete
    suspend fun delete(item: KidContentItem)

    @Query("DELETE FROM kid_content WHERE kidProfileId=:kidId AND contentType=:type AND contentId=:contentId")
    suspend fun disallow(kidId: Long, type: String, contentId: Long)

    @Query("SELECT COUNT(*) FROM kid_content WHERE kidProfileId=:kidId AND contentType=:type AND contentId=:contentId")
    suspend fun isAllowedCount(kidId: Long, type: String, contentId: Long): Int

    @Query("SELECT * FROM kid_content WHERE kidProfileId=:kidId AND contentType=:type")
    suspend fun listForKidAndType(kidId: Long, type: String): List<KidContentItem>
}

/** DAO: Kid Category Allow */
@Dao
interface KidCategoryAllowDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: KidCategoryAllow): Long

    @Query("DELETE FROM kid_category_allow WHERE kidProfileId=:kidId AND contentType=:type AND categoryId=:categoryId")
    suspend fun disallow(kidId: Long, type: String, categoryId: String)

    @Query("SELECT * FROM kid_category_allow WHERE kidProfileId=:kidId AND contentType=:type")
    suspend fun listForKidAndType(kidId: Long, type: String): List<KidCategoryAllow>
}

/** DAO: Kid Content Block (Ausnahmen) */
@Dao
interface KidContentBlockDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: KidContentBlock): Long

    @Query("DELETE FROM kid_content_block WHERE kidProfileId=:kidId AND contentType=:type AND contentId=:contentId")
    suspend fun unblock(kidId: Long, type: String, contentId: Long)

    @Query("SELECT COUNT(*) FROM kid_content_block WHERE kidProfileId=:kidId AND contentType=:type AND contentId=:contentId")
    suspend fun isBlockedCount(kidId: Long, type: String, contentId: Long): Int

    @Query("SELECT * FROM kid_content_block WHERE kidProfileId=:kidId AND contentType=:type")
    suspend fun listForKidAndType(kidId: Long, type: String): List<KidContentBlock>
}

/** DAO: Screen Time */
@Dao
interface ScreenTimeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ScreenTimeEntry): Long

    @Query("SELECT * FROM screen_time WHERE kidProfileId=:kidId AND dayYyyymmdd=:day LIMIT 1")
    suspend fun getForDay(kidId: Long, day: String): ScreenTimeEntry?

    @Query("UPDATE screen_time SET usedMinutes=:used WHERE kidProfileId=:kidId AND dayYyyymmdd=:day")
    suspend fun updateUsed(kidId: Long, day: String, used: Int)

    @Query("UPDATE screen_time SET limitMinutes=:limit WHERE kidProfileId=:kidId AND dayYyyymmdd=:day")
    suspend fun updateLimit(kidId: Long, day: String, limit: Int)
}

/** DAO: Profile Permissions */
@Dao
interface ProfilePermissionsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pp: ProfilePermissions): Long

    @Query("SELECT * FROM profile_permissions WHERE profileId=:profileId LIMIT 1")
    suspend fun byProfile(profileId: Long): ProfilePermissions?

    @Query("DELETE FROM profile_permissions WHERE profileId=:profileId")
    suspend fun deleteByProfile(profileId: Long)
}
