package com.fishit.player.core.catalogsync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
 * Persistent storage for sync checkpoints.
 *
 * Stores checkpoints for each source type (Xtream, Telegram) so that
 * sync operations can resume from where they left off.
 *
 * **Contract:**
 * - Checkpoints MUST be non-null strings (use encoded format)
 * - Checkpoints MUST be updated BEFORE returning from a worker
 * - On successful completion, checkpoint should be reset or set to COMPLETED
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
    }
