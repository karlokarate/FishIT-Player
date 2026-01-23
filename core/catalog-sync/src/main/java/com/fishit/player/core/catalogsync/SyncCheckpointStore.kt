package com.fishit.player.core.catalogsync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// DataStore instance
private val Context.syncCheckpointDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_checkpoints",
)

/**
 * Persistent storage for sync checkpoints and timestamps.
 *
 * Stores checkpoints for each source type (Xtream, Telegram) so that
 * sync operations can resume from where they left off.
 *
 * Also stores the last successful sync timestamp for each source, which is used
 * by incremental sync to only fetch items added after that timestamp.
 *
 * **Contract:**
 * - Checkpoints MUST be non-null strings (use encoded format)
 * - Checkpoints MUST be updated BEFORE returning from a worker
 * - On successful completion, checkpoint should be reset or set to COMPLETED
 * - lastSyncTimestamp should be updated on successful full sync completion
 */
interface SyncCheckpointStore {
    /**
     * Get the current checkpoint for Xtream sync.
     *
     * @return Encoded checkpoint string, or null if no checkpoint exists
     */
    suspend fun getXtreamCheckpoint(): String?

    /**
     * Save checkpoint for Xtream sync.
     *
     * @param checkpoint Encoded checkpoint string (must not be empty)
     */
    suspend fun saveXtreamCheckpoint(checkpoint: String)

    /**
     * Clear the Xtream checkpoint (after successful completion or force rescan).
     */
    suspend fun clearXtreamCheckpoint()

    /**
     * Get the current checkpoint for Telegram sync.
     *
     * @return Encoded checkpoint string, or null if no checkpoint exists
     */
    suspend fun getTelegramCheckpoint(): String?

    /**
     * Save checkpoint for Telegram sync.
     *
     * @param checkpoint Encoded checkpoint string (must not be empty)
     */
    suspend fun saveTelegramCheckpoint(checkpoint: String)

    /**
     * Clear the Telegram checkpoint.
     */
    suspend fun clearTelegramCheckpoint()

    /**
     * Clear all checkpoints (for force rescan).
     */
    suspend fun clearAll()
    
    // =========================================================================
    // Last Sync Timestamp (for Incremental Sync)
    // =========================================================================
    
    /**
     * Get the timestamp of the last successful Xtream sync (epoch millis).
     * 
     * Used by incremental sync to only fetch items where `added > lastSyncTimestamp`.
     * 
     * @return Epoch millis of last sync, or null if never synced
     */
    suspend fun getXtreamLastSyncTimestamp(): Long?
    
    /**
     * Save the timestamp of the last successful Xtream sync.
     * 
     * Should be called after a successful full sync or incremental sync.
     * 
     * @param timestamp Epoch millis when sync completed
     */
    suspend fun saveXtreamLastSyncTimestamp(timestamp: Long)
    
    /**
     * Get the timestamp of the last successful Telegram sync (epoch millis).
     * 
     * @return Epoch millis of last sync, or null if never synced
     */
    suspend fun getTelegramLastSyncTimestamp(): Long?
    
    /**
     * Save the timestamp of the last successful Telegram sync.
     * 
     * @param timestamp Epoch millis when sync completed
     */
    suspend fun saveTelegramLastSyncTimestamp(timestamp: Long)
    
    /**
     * Get the last known item counts for Xtream.
     * 
     * Used by incremental sync for quick count comparison:
     * - If counts match → skip sync entirely
     * - If counts differ → fetch only new items
     * 
     * @return Triple of (vodCount, seriesCount, liveCount), or null if never synced
     */
    suspend fun getXtreamLastCounts(): Triple<Int, Int, Int>?
    
    /**
     * Save the item counts after successful Xtream sync.
     * 
     * @param vodCount Number of VOD items
     * @param seriesCount Number of series
     * @param liveCount Number of live channels
     */
    suspend fun saveXtreamLastCounts(vodCount: Int, seriesCount: Int, liveCount: Int)
}

/**
 * DataStore-backed implementation of [SyncCheckpointStore].
 */
@Singleton
class DataStoreSyncCheckpointStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SyncCheckpointStore {
        companion object {
            private val KEY_XTREAM_CHECKPOINT = stringPreferencesKey("xtream_checkpoint")
            private val KEY_TELEGRAM_CHECKPOINT = stringPreferencesKey("telegram_checkpoint")
            
            // Incremental sync keys
            private val KEY_XTREAM_LAST_SYNC_TS = longPreferencesKey("xtream_last_sync_timestamp")
            private val KEY_TELEGRAM_LAST_SYNC_TS = longPreferencesKey("telegram_last_sync_timestamp")
            private val KEY_XTREAM_VOD_COUNT = longPreferencesKey("xtream_vod_count")
            private val KEY_XTREAM_SERIES_COUNT = longPreferencesKey("xtream_series_count")
            private val KEY_XTREAM_LIVE_COUNT = longPreferencesKey("xtream_live_count")
        }

        override suspend fun getXtreamCheckpoint(): String? =
            context.syncCheckpointDataStore.data
                .map { prefs -> prefs[KEY_XTREAM_CHECKPOINT] }
                .first()

        override suspend fun saveXtreamCheckpoint(checkpoint: String) {
            require(checkpoint.isNotBlank()) { "Checkpoint must not be blank" }
            context.syncCheckpointDataStore.edit { prefs ->
                prefs[KEY_XTREAM_CHECKPOINT] = checkpoint
            }
        }

        override suspend fun clearXtreamCheckpoint() {
            context.syncCheckpointDataStore.edit { prefs ->
                prefs.remove(KEY_XTREAM_CHECKPOINT)
            }
        }

        override suspend fun getTelegramCheckpoint(): String? =
            context.syncCheckpointDataStore.data
                .map { prefs -> prefs[KEY_TELEGRAM_CHECKPOINT] }
                .first()

        override suspend fun saveTelegramCheckpoint(checkpoint: String) {
            require(checkpoint.isNotBlank()) { "Checkpoint must not be blank" }
            context.syncCheckpointDataStore.edit { prefs ->
                prefs[KEY_TELEGRAM_CHECKPOINT] = checkpoint
            }
        }

        override suspend fun clearTelegramCheckpoint() {
            context.syncCheckpointDataStore.edit { prefs ->
                prefs.remove(KEY_TELEGRAM_CHECKPOINT)
            }
        }

        override suspend fun clearAll() {
            context.syncCheckpointDataStore.edit { prefs ->
                prefs.remove(KEY_XTREAM_CHECKPOINT)
                prefs.remove(KEY_TELEGRAM_CHECKPOINT)
            }
        }
        
        // =========================================================================
        // Last Sync Timestamp (for Incremental Sync)
        // =========================================================================
        
        override suspend fun getXtreamLastSyncTimestamp(): Long? =
            context.syncCheckpointDataStore.data
                .map { prefs -> prefs[KEY_XTREAM_LAST_SYNC_TS] }
                .first()
        
        override suspend fun saveXtreamLastSyncTimestamp(timestamp: Long) {
            context.syncCheckpointDataStore.edit { prefs ->
                prefs[KEY_XTREAM_LAST_SYNC_TS] = timestamp
            }
        }
        
        override suspend fun getTelegramLastSyncTimestamp(): Long? =
            context.syncCheckpointDataStore.data
                .map { prefs -> prefs[KEY_TELEGRAM_LAST_SYNC_TS] }
                .first()
        
        override suspend fun saveTelegramLastSyncTimestamp(timestamp: Long) {
            context.syncCheckpointDataStore.edit { prefs ->
                prefs[KEY_TELEGRAM_LAST_SYNC_TS] = timestamp
            }
        }
        
        override suspend fun getXtreamLastCounts(): Triple<Int, Int, Int>? {
            val prefs = context.syncCheckpointDataStore.data.first()
            val vodCount = prefs[KEY_XTREAM_VOD_COUNT]?.toInt()
            val seriesCount = prefs[KEY_XTREAM_SERIES_COUNT]?.toInt()
            val liveCount = prefs[KEY_XTREAM_LIVE_COUNT]?.toInt()
            
            // Return null if any count is missing (never synced)
            return if (vodCount != null && seriesCount != null && liveCount != null) {
                Triple(vodCount, seriesCount, liveCount)
            } else {
                null
            }
        }
        
        override suspend fun saveXtreamLastCounts(vodCount: Int, seriesCount: Int, liveCount: Int) {
            context.syncCheckpointDataStore.edit { prefs ->
                prefs[KEY_XTREAM_VOD_COUNT] = vodCount.toLong()
                prefs[KEY_XTREAM_SERIES_COUNT] = seriesCount.toLong()
                prefs[KEY_XTREAM_LIVE_COUNT] = liveCount.toLong()
            }
        }
    }
