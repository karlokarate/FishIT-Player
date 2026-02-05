package com.fishit.player.core.synccommon.checkpoint

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fishit.player.core.model.sync.SyncPhase
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-based checkpoint persistence for resumable sync.
 *
 * **Purpose:**
 * Enables incremental sync by persisting progress checkpoints.
 * If sync is interrupted, the next run can resume from the last checkpoint.
 *
 * **Checkpoint Strategy:**
 * - Per-source checkpoints (Xtream, Telegram, etc.)
 * - Per-phase checkpoints within each source
 * - Cursor-based for paginated APIs (page number, offset)
 * - Timestamp-based for time-sensitive sources
 *
 * **Data Stored Per Checkpoint:**
 * - `lastCompletedPhase`: Last fully completed phase
 * - `lastCursor`: API cursor/offset for partial phase progress
 * - `lastSyncTimestamp`: When checkpoint was saved
 * - `totalItemsSynced`: Running count for this sync
 * - `isPartialSync`: Whether sync was interrupted
 *
 * **Usage:**
 * ```kotlin
 * val checkpointStore = SyncCheckpointStore(context)
 *
 * // At start of sync
 * val checkpoint = checkpointStore.getCheckpoint("xtream_account_123")
 * val startPhase = checkpoint?.lastCompletedPhase?.nextPhase() ?: SyncPhase.LIVE_CHANNELS
 *
 * // After completing each phase
 * checkpointStore.saveCheckpoint(
 *     key = "xtream_account_123",
 *     checkpoint = SyncCheckpoint(
 *         lastCompletedPhase = SyncPhase.VOD_MOVIES,
 *         totalItemsSynced = 5000
 *     )
 * )
 *
 * // On successful sync completion
 * checkpointStore.clearCheckpoint("xtream_account_123")
 * ```
 *
 * @param context Application context for DataStore
 */
@Singleton
class SyncCheckpointStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "sync_checkpoints",
    )

    companion object {
        private const val TAG = "SyncCheckpointStore"

        // Key prefixes
        private const val PREFIX_PHASE = "phase_"
        private const val PREFIX_CURSOR = "cursor_"
        private const val PREFIX_TIMESTAMP = "timestamp_"
        private const val PREFIX_ITEMS = "items_"
        private const val PREFIX_PARTIAL = "partial_"
        private const val PREFIX_VERSION = "version_"
    }

    /**
     * Save checkpoint for a sync source.
     *
     * @param key Unique key identifying the source (e.g., "xtream_{accountKey}")
     * @param checkpoint Checkpoint data to save
     */
    suspend fun saveCheckpoint(
        key: String,
        checkpoint: SyncCheckpoint,
    ) {
        try {
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("${PREFIX_PHASE}$key")] =
                    checkpoint.lastCompletedPhase?.name ?: ""
                prefs[stringPreferencesKey("${PREFIX_CURSOR}$key")] =
                    checkpoint.lastCursor ?: ""
                prefs[longPreferencesKey("${PREFIX_TIMESTAMP}$key")] =
                    checkpoint.lastSyncTimestamp
                prefs[longPreferencesKey("${PREFIX_ITEMS}$key")] =
                    checkpoint.totalItemsSynced
                prefs[booleanPreferencesKey("${PREFIX_PARTIAL}$key")] =
                    checkpoint.isPartialSync
                prefs[longPreferencesKey("${PREFIX_VERSION}$key")] =
                    checkpoint.schemaVersion
            }
            UnifiedLog.d(TAG) { "Checkpoint saved for $key: phase=${checkpoint.lastCompletedPhase}, items=${checkpoint.totalItemsSynced}" }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to save checkpoint for $key" }
        }
    }

    /**
     * Get checkpoint for a sync source.
     *
     * @param key Unique key identifying the source
     * @return Checkpoint if exists and valid, null otherwise
     */
    suspend fun getCheckpoint(key: String): SyncCheckpoint? {
        return try {
            val prefs = context.dataStore.data.first()
            val phaseName = prefs[stringPreferencesKey("${PREFIX_PHASE}$key")]
            val cursor = prefs[stringPreferencesKey("${PREFIX_CURSOR}$key")]
            val timestamp = prefs[longPreferencesKey("${PREFIX_TIMESTAMP}$key")] ?: 0L
            val items = prefs[longPreferencesKey("${PREFIX_ITEMS}$key")] ?: 0L
            val isPartial = prefs[booleanPreferencesKey("${PREFIX_PARTIAL}$key")] ?: false
            val version = prefs[longPreferencesKey("${PREFIX_VERSION}$key")] ?: 1L

            // No checkpoint if timestamp is 0
            if (timestamp == 0L) return null

            val phase = phaseName?.takeIf { it.isNotEmpty() }?.let {
                try {
                    SyncPhase.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }

            SyncCheckpoint(
                lastCompletedPhase = phase,
                lastCursor = cursor?.takeIf { it.isNotEmpty() },
                lastSyncTimestamp = timestamp,
                totalItemsSynced = items,
                isPartialSync = isPartial,
                schemaVersion = version,
            ).also {
                UnifiedLog.d(TAG) { "Checkpoint loaded for $key: phase=${it.lastCompletedPhase}, items=${it.totalItemsSynced}" }
            }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to get checkpoint for $key" }
            null
        }
    }

    /**
     * Clear checkpoint for a sync source (after successful full sync).
     *
     * @param key Unique key identifying the source
     */
    suspend fun clearCheckpoint(key: String) {
        try {
            context.dataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("${PREFIX_PHASE}$key"))
                prefs.remove(stringPreferencesKey("${PREFIX_CURSOR}$key"))
                prefs.remove(longPreferencesKey("${PREFIX_TIMESTAMP}$key"))
                prefs.remove(longPreferencesKey("${PREFIX_ITEMS}$key"))
                prefs.remove(booleanPreferencesKey("${PREFIX_PARTIAL}$key"))
                prefs.remove(longPreferencesKey("${PREFIX_VERSION}$key"))
            }
            UnifiedLog.d(TAG) { "Checkpoint cleared for $key" }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to clear checkpoint for $key" }
        }
    }

    /**
     * Check if a checkpoint exists and is valid for resumption.
     *
     * @param key Unique key identifying the source
     * @param maxAgeMs Maximum age in milliseconds (default: 24 hours)
     * @return true if valid checkpoint exists
     */
    suspend fun hasValidCheckpoint(
        key: String,
        maxAgeMs: Long = 24 * 60 * 60 * 1000L,
    ): Boolean {
        val checkpoint = getCheckpoint(key) ?: return false
        val age = System.currentTimeMillis() - checkpoint.lastSyncTimestamp
        return age < maxAgeMs && checkpoint.isPartialSync
    }

    /**
     * Update cursor within current phase (for paginated APIs).
     *
     * @param key Unique key identifying the source
     * @param cursor New cursor value
     */
    suspend fun updateCursor(
        key: String,
        cursor: String,
    ) {
        try {
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("${PREFIX_CURSOR}$key")] = cursor
                prefs[longPreferencesKey("${PREFIX_TIMESTAMP}$key")] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to update cursor for $key" }
        }
    }
}

/**
 * Checkpoint data for resumable sync.
 */
data class SyncCheckpoint(
    /** Last fully completed sync phase */
    val lastCompletedPhase: SyncPhase?,
    /** Cursor/offset within current phase (for partial phase resume) */
    val lastCursor: String? = null,
    /** Timestamp when checkpoint was saved */
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    /** Total items synced up to this checkpoint */
    val totalItemsSynced: Long = 0,
    /** Whether sync was interrupted (true) or completed (false) */
    val isPartialSync: Boolean = true,
    /** Schema version for migration handling */
    val schemaVersion: Long = 1,
) {
    /**
     * Determine starting phase for resumed sync.
     *
     * @param defaultPhase Phase to use if no checkpoint
     * @return Phase to start from
     */
    fun getStartPhase(defaultPhase: SyncPhase = SyncPhase.INITIALIZING): SyncPhase {
        // If we have a cursor, resume the same phase
        if (lastCursor != null && lastCompletedPhase != null) {
            return lastCompletedPhase
        }
        // Otherwise start after the completed phase
        return lastCompletedPhase?.let { completed ->
            val phases = SyncPhase.entries
            val nextIndex = phases.indexOf(completed) + 1
            if (nextIndex < phases.size && !phases[nextIndex].isTerminal) {
                phases[nextIndex]
            } else {
                defaultPhase
            }
        } ?: defaultPhase
    }
}
