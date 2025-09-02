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
import kotlinx.coroutines.flow.first

// Phase 1: DB-Version 3, idempotente Migrationen (CREATE IF NOT EXISTS), Callback-Seeding Adult-Profil
@Database(
    entities = [
        MediaItem::class,
        Episode::class,
        Category::class,
        ResumeMark::class,
        Profile::class,
        KidContentItem::class,
        KidCategoryAllow::class,
        KidContentBlock::class,
        ProfilePermissions::class,
        ScreenTimeEntry::class,
        EpgNowNext::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun resumeDao(): ResumeDao
    abstract fun profileDao(): ProfileDao
    abstract fun kidContentDao(): KidContentDao
    abstract fun kidCategoryAllowDao(): KidCategoryAllowDao
    abstract fun kidContentBlockDao(): KidContentBlockDao
    abstract fun screenTimeDao(): ScreenTimeDao
    abstract fun profilePermissionsDao(): ProfilePermissionsDao
    abstract fun epgDao(): EpgDao
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        trySeedAdultProfile(db)
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Idempotent: Wenn keine Profile existieren (z. B. nach leerer Migration), Adult anlegen
                        trySeedAdultProfile(db)
                    }
                })
                .build()
                .also { INSTANCE = it }
        }

    // --- Migrationen (idempotent; keine destructive migrations) ---
    // Hinweis: CREATE TABLE/INDEX IF NOT EXISTS, damit mehrfaches Ausführen sicher ist.
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
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `epg_now_next` (
                    `channelId` TEXT NOT NULL PRIMARY KEY,
                    `nowTitle` TEXT,
                    `nowStartMs` INTEGER,
                    `nowEndMs` INTEGER,
                    `nextTitle` TEXT,
                    `nextStartMs` INTEGER,
                    `nextEndMs` INTEGER,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `kid_category_allow` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `kidProfileId` INTEGER NOT NULL,
                    `contentType` TEXT NOT NULL,
                    `categoryId` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kid_category_allow_kidProfileId` ON `kid_category_allow`(`kidProfileId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kid_category_allow_contentType` ON `kid_category_allow`(`contentType`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_kid_category_allow_unique` ON `kid_category_allow`(`kidProfileId`, `contentType`, `categoryId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `kid_content_block` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `kidProfileId` INTEGER NOT NULL,
                    `contentType` TEXT NOT NULL,
                    `contentId` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kid_content_block_kidProfileId` ON `kid_content_block`(`kidProfileId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kid_content_block_contentType` ON `kid_content_block`(`contentType`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_kid_content_block_unique` ON `kid_content_block`(`kidProfileId`, `contentType`, `contentId`)")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `profile_permissions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `profileId` INTEGER NOT NULL,
                    `canOpenSettings` INTEGER NOT NULL,
                    `canChangeSources` INTEGER NOT NULL,
                    `canUseExternalPlayer` INTEGER NOT NULL,
                    `canEditFavorites` INTEGER NOT NULL,
                    `canSearch` INTEGER NOT NULL,
                    `canSeeResume` INTEGER NOT NULL,
                    `canEditWhitelist` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_profile_permissions_profileId` ON `profile_permissions`(`profileId`)")
        }
    }

    // --- Seeding: Adult-Profil + optional current_profile_id setzen (best effort) ---
    private fun trySeedAdultProfile(db: SupportSQLiteDatabase) {
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
            }
        } catch (t: Throwable) {
            Log.w("DbProvider", "Seeding skipped/failed: ${t.message}")
        }
    }
}
