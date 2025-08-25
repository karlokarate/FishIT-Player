package com.chris.m3usuite.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.runBlocking

@Database(
    entities = [
        MediaItem::class,
        Episode::class,
        Category::class,
        ResumeMark::class,
        Profile::class,
        KidContentItem::class,
        ScreenTimeEntry::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun resumeDao(): ResumeDao
    abstract fun profileDao(): ProfileDao
    abstract fun kidContentDao(): KidContentDao
    abstract fun screenTimeDao(): ScreenTimeDao
}

/**
 * Einziger, zentraler DB-Provider.
 * Achtung: Es darf KEINE weitere Datei "DbProvider.kt" im gleichen Paket geben.
 */
object DbProvider {
    @Volatile private var INSTANCE: AppDatabase? = null
    private const val DB_NAME = "meta.sqlite" // stabiler, einheitlicher Name

    fun get(ctx: Context): AppDatabase =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                ctx.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        trySeedAdultProfile(db, ctx)
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Idempotent: Wenn keine Profile existieren (z. B. nach leerer Migration), Adult anlegen
                        trySeedAdultProfile(db, ctx)
                    }
                })
                .build()
                .also { INSTANCE = it }
        }

    // --- Migrationen ---
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Historische Migration – hier no-op, da Schema-Details von v1 nicht relevant
        }
    }
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Neue Tabellen (idempotent)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `profiles` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `avatarPath` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `kid_content` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `kidProfileId` INTEGER NOT NULL,
                    `contentType` TEXT NOT NULL,
                    `contentId` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_kid_content_kidProfileId`
                ON `kid_content`(`kidProfileId`)
            """.trimIndent())
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_kid_content_contentType`
                ON `kid_content`(`contentType`)
            """.trimIndent())
            db.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS `index_kid_content_unique_triplet`
                ON `kid_content`(`kidProfileId`, `contentType`, `contentId`)
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `screen_time` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `kidProfileId` INTEGER NOT NULL,
                    `dayYyyymmdd` TEXT NOT NULL,
                    `usedMinutes` INTEGER NOT NULL,
                    `limitMinutes` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_screen_time_kidProfileId`
                ON `screen_time`(`kidProfileId`)
            """.trimIndent())
            db.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS `index_screen_time_unique_day`
                ON `screen_time`(`kidProfileId`, `dayYyyymmdd`)
            """.trimIndent())
        }
    }

    // --- Seeding: Adult-Profil + optional current_profile_id setzen ---
    private fun trySeedAdultProfile(db: SupportSQLiteDatabase, ctx: Context) {
        try {
            val c = db.query("SELECT COUNT(*) FROM profiles")
            var count = 0L
            if (c.moveToFirst()) {
                count = c.getLong(0)
            }
            c.close()
            if (count == 0L) {
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO profiles(name, type, avatarPath, createdAt, updatedAt) VALUES(?,?,?,?,?)",
                    arrayOf("Adult", "adult", null, now, now)
                )
                val idCursor = db.query("SELECT id FROM profiles WHERE type='adult' ORDER BY id ASC LIMIT 1")
                var adultId = -1L
                if (idCursor.moveToFirst()) {
                    adultId = idCursor.getLong(0)
                }
                idCursor.close()
                if (adultId > 0) {
                    // Optional: current_profile_id setzen, falls noch nicht gesetzt
                    runBlocking {
                        val store = SettingsStore(ctx)
                        val current = store.currentProfileId.first()
                        if (current <= 0L) {
                            store.setCurrentProfileId(adultId)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w("DbProvider", "Seeding skipped/failed: ${t.message}")
        }
    }
}
