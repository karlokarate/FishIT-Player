package com.chris.m3usuite.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaItem::class,
        Episode::class,
        Category::class,
        ResumeMark::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun resumeDao(): ResumeDao
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
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
}
