package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for EPG (Electronic Program Guide) data.
 *
 * Provides access to program schedule information for live TV channels.
 *
 * **SSOT Contract:** NX_EpgEntry replaces legacy ObxEpgNowNext with richer data model.
 *
 * ## Key Differences from Legacy
 *
 * | Legacy (ObxEpgNowNext) | New (NxEpgRepository) |
 * |------------------------|----------------------|
 * | Only now/next | Full program schedule |
 * | streamId (Int) | channelWorkKey (String FK) |
 * | Flat structure | Hierarchical with NX_Work |
 *
 * @see com.fishit.player.core.persistence.obx.NX_EpgEntry
 */
interface NxEpgRepository {
    /**
     * EPG entry domain model.
     *
     * @property epgEntryKey Unique key: `<channelWorkKey>:<startMs>`
     * @property channelWorkKey Work key of the LIVE channel (FK to NX_Work)
     * @property epgChannelId External EPG provider channel ID
     * @property title Program title
     * @property startMs Program start time in milliseconds (epoch)
     * @property endMs Program end time in milliseconds (epoch)
     * @property description Program description/plot
     * @property category Program category (Movie, News, Sports, etc.)
     * @property iconUrl Program icon/poster URL
     * @property createdAtMs Entry creation timestamp
     * @property updatedAtMs Entry last update timestamp
     */
    data class EpgEntry(
        val epgEntryKey: String,
        val channelWorkKey: String,
        val epgChannelId: String,
        val title: String,
        val startMs: Long,
        val endMs: Long,
        val description: String? = null,
        val category: String? = null,
        val iconUrl: String? = null,
        val createdAtMs: Long = 0L,
        val updatedAtMs: Long = 0L,
    ) {
        /** Duration in milliseconds */
        val durationMs: Long get() = endMs - startMs

        /** Check if currently airing */
        fun isCurrentlyAiring(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs in startMs until endMs

        /** Check if upcoming */
        fun isUpcoming(nowMs: Long = System.currentTimeMillis()): Boolean = startMs > nowMs

        /** Progress percentage (0.0 - 1.0) if currently airing */
        fun progressPercent(nowMs: Long = System.currentTimeMillis()): Float {
            if (!isCurrentlyAiring(nowMs)) return 0f
            val elapsed = nowMs - startMs
            return (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
        }
    }

    /**
     * Now/Next pair for a channel.
     *
     * @property channelWorkKey Work key of the LIVE channel
     * @property now Currently airing program (null if no EPG data)
     * @property next Next upcoming program (null if no EPG data)
     */
    data class NowNext(
        val channelWorkKey: String,
        val now: EpgEntry?,
        val next: EpgEntry?,
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Reads (UI-critical)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Get now/next for a single channel.
     *
     * @param channelWorkKey Work key of the LIVE channel
     * @return NowNext pair (now/next may be null if no EPG data)
     */
    suspend fun getNowNext(channelWorkKey: String): NowNext

    /**
     * Observe now/next for a single channel.
     *
     * Flow emits on EPG data changes and time progression.
     *
     * @param channelWorkKey Work key of the LIVE channel
     * @return Flow of NowNext pairs
     */
    fun observeNowNext(channelWorkKey: String): Flow<NowNext>

    /**
     * Get now/next for multiple channels (batch).
     *
     * Efficient for Live TV grid UI.
     *
     * @param channelWorkKeys List of channel work keys
     * @return Map of channelWorkKey to NowNext
     */
    suspend fun getNowNextBatch(channelWorkKeys: List<String>): Map<String, NowNext>

    /**
     * Observe now/next for multiple channels.
     *
     * @param channelWorkKeys List of channel work keys
     * @return Flow of Map<channelWorkKey, NowNext>
     */
    fun observeNowNextBatch(channelWorkKeys: List<String>): Flow<Map<String, NowNext>>

    /**
     * Get full program schedule for a channel within time range.
     *
     * @param channelWorkKey Work key of the LIVE channel
     * @param fromMs Start of time range (epoch ms)
     * @param toMs End of time range (epoch ms)
     * @return List of EPG entries sorted by startMs
     */
    suspend fun getSchedule(
        channelWorkKey: String,
        fromMs: Long,
        toMs: Long,
    ): List<EpgEntry>

    /**
     * Observe program schedule for a channel.
     *
     * @param channelWorkKey Work key of the LIVE channel
     * @param fromMs Start of time range (epoch ms)
     * @param toMs End of time range (epoch ms)
     * @return Flow of EPG entries
     */
    fun observeSchedule(
        channelWorkKey: String,
        fromMs: Long,
        toMs: Long,
    ): Flow<List<EpgEntry>>

    /**
     * Search EPG entries by title.
     *
     * @param query Search query (case-insensitive)
     * @param fromMs Only search programs starting after this time
     * @param limit Maximum results
     * @return Matching EPG entries
     */
    suspend fun searchByTitle(
        query: String,
        fromMs: Long = System.currentTimeMillis(),
        limit: Int = 50,
    ): List<EpgEntry>

    // ──────────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Upsert a single EPG entry.
     *
     * @param entry EPG entry to upsert
     * @return Upserted entry with updated timestamps
     */
    suspend fun upsert(entry: EpgEntry): EpgEntry

    /**
     * Upsert multiple EPG entries (batch).
     *
     * Efficient for bulk EPG import.
     *
     * @param entries List of EPG entries
     * @return Number of upserted entries
     */
    suspend fun upsertBatch(entries: List<EpgEntry>): Int

    /**
     * Replace all EPG entries for a channel.
     *
     * Deletes existing entries and inserts new ones atomically.
     * Useful for full EPG refresh from provider.
     *
     * @param channelWorkKey Work key of the LIVE channel
     * @param entries New EPG entries for the channel
     * @return Number of inserted entries
     */
    suspend fun replaceForChannel(
        channelWorkKey: String,
        entries: List<EpgEntry>,
    ): Int

    /**
     * Delete EPG entries older than given time.
     *
     * @param beforeMs Delete entries ending before this time
     * @return Number of deleted entries
     */
    suspend fun deleteOlderThan(beforeMs: Long): Int

    /**
     * Delete all EPG entries for a channel.
     *
     * @param channelWorkKey Work key of the LIVE channel
     * @return Number of deleted entries
     */
    suspend fun deleteForChannel(channelWorkKey: String): Int

    // ──────────────────────────────────────────────────────────────────────────
    // Maintenance
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Get total EPG entry count.
     */
    suspend fun count(): Long

    /**
     * Get EPG entry count for a channel.
     */
    suspend fun countForChannel(channelWorkKey: String): Long

    /**
     * Prune expired EPG entries (default: older than 24 hours).
     *
     * @param maxAgeMs Maximum age of entries to keep
     * @return Number of deleted entries
     */
    suspend fun pruneExpired(maxAgeMs: Long = 24 * 60 * 60 * 1000L): Int
}
