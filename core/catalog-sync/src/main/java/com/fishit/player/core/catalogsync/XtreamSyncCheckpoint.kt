package com.fishit.player.core.catalogsync

/**
 * Resumable checkpoint for Xtream catalog synchronization.
 *
 * Defines where the sync process should resume from after interruption.
 * The checkpoint is encoded as a stable string format for persistence in
 * WorkManager progress or DataStore.
 *
 * **Format:** `xtream|phase=<PHASE>|offset=<N>|extra=<...>`
 *
 * **Phases (in execution order):**
 * 1. VOD_LIST - Scanning VOD items
 * 2. SERIES_LIST - Scanning series containers
 * 3. SERIES_EPISODES - Scanning episodes (by series cursor)
 * 4. LIVE_LIST - Scanning live channels
 * 5. VOD_INFO - Backfilling VOD info (plot/cast/director)
 * 6. SERIES_INFO - Backfilling series info
 * 7. COMPLETED - Scan fully completed
 *
 * @property phase Current sync phase
 * @property offset Progress within the current phase (e.g., index in list)
 * @property seriesIndex For SERIES_EPISODES: which series we're processing
 * @property lastVodInfoId For VOD_INFO: last processed VOD ID
 * @property lastSeriesInfoId For SERIES_INFO: last processed series ID
 */
data class XtreamSyncCheckpoint(
    val phase: XtreamSyncPhase,
    val offset: Int = 0,
    val seriesIndex: Int = 0,
    val lastVodInfoId: Int? = null,
    val lastSeriesInfoId: Int? = null,
) {
    companion object {
        private const val PREFIX = "xtream"
        private const val SEPARATOR = "|"
        private const val KEY_PHASE = "phase"
        private const val KEY_OFFSET = "offset"
        private const val KEY_SERIES_INDEX = "series_index"
        private const val KEY_LAST_VOD_INFO_ID = "last_vod_info_id"
        private const val KEY_LAST_SERIES_INFO_ID = "last_series_info_id"

        /** Initial checkpoint - start from the beginning */
        val INITIAL = XtreamSyncCheckpoint(phase = XtreamSyncPhase.VOD_LIST)

        /**
         * Decode checkpoint from string representation.
         *
         * @param encoded Encoded checkpoint string
         * @return Decoded checkpoint or [INITIAL] if parsing fails
         */
        fun decode(encoded: String?): XtreamSyncCheckpoint {
            if (encoded.isNullOrBlank()) return INITIAL
            if (!encoded.startsWith(PREFIX)) return INITIAL

            val parts = encoded.split(SEPARATOR).drop(1) // Skip prefix
            val map =
                parts
                    .mapNotNull { part ->
                        val (key, value) =
                            part.split("=", limit = 2).takeIf { it.size == 2 }
                                ?: return@mapNotNull null
                        key to value
                    }.toMap()

            val phaseName = map[KEY_PHASE] ?: return INITIAL
            val phase = XtreamSyncPhase.entries.find { it.name == phaseName } ?: return INITIAL

            return XtreamSyncCheckpoint(
                phase = phase,
                offset = map[KEY_OFFSET]?.toIntOrNull() ?: 0,
                seriesIndex = map[KEY_SERIES_INDEX]?.toIntOrNull() ?: 0,
                lastVodInfoId = map[KEY_LAST_VOD_INFO_ID]?.toIntOrNull(),
                lastSeriesInfoId = map[KEY_LAST_SERIES_INFO_ID]?.toIntOrNull(),
            )
        }
    }

    /**
     * Encode checkpoint to stable string representation.
     *
     * @return Encoded checkpoint string (never null or empty)
     */
    fun encode(): String =
        buildString {
            append(PREFIX)
            append(SEPARATOR)
            append("$KEY_PHASE=${phase.name}")
            append(SEPARATOR)
            append("$KEY_OFFSET=$offset")

            if (seriesIndex > 0) {
                append(SEPARATOR)
                append("$KEY_SERIES_INDEX=$seriesIndex")
            }
            lastVodInfoId?.let {
                append(SEPARATOR)
                append("$KEY_LAST_VOD_INFO_ID=$it")
            }
            lastSeriesInfoId?.let {
                append(SEPARATOR)
                append("$KEY_LAST_SERIES_INFO_ID=$it")
            }
        }

    /**
     * Advance to the next phase.
     *
     * @return New checkpoint at the start of the next phase, or COMPLETED if done
     */
    fun advancePhase(): XtreamSyncCheckpoint {
        val nextPhase =
            when (phase) {
                XtreamSyncPhase.VOD_LIST -> XtreamSyncPhase.SERIES_LIST
                XtreamSyncPhase.SERIES_LIST -> XtreamSyncPhase.SERIES_EPISODES
                XtreamSyncPhase.SERIES_EPISODES -> XtreamSyncPhase.LIVE_LIST
                XtreamSyncPhase.LIVE_LIST -> XtreamSyncPhase.VOD_INFO
                XtreamSyncPhase.VOD_INFO -> XtreamSyncPhase.SERIES_INFO
                XtreamSyncPhase.SERIES_INFO -> XtreamSyncPhase.COMPLETED
                XtreamSyncPhase.COMPLETED -> XtreamSyncPhase.COMPLETED
            }
        return XtreamSyncCheckpoint(phase = nextPhase)
    }

    /**
     * Update offset within current phase.
     *
     * @param newOffset New offset value
     * @return Updated checkpoint
     */
    fun withOffset(newOffset: Int): XtreamSyncCheckpoint = copy(offset = newOffset)

    /**
     * Update series index for SERIES_EPISODES phase.
     *
     * @param newSeriesIndex New series index
     * @return Updated checkpoint
     */
    fun withSeriesIndex(newSeriesIndex: Int): XtreamSyncCheckpoint = copy(seriesIndex = newSeriesIndex)

    /**
     * Update last processed VOD info ID.
     *
     * @param vodId Last VOD ID that was processed
     * @return Updated checkpoint
     */
    fun withLastVodInfoId(vodId: Int): XtreamSyncCheckpoint = copy(lastVodInfoId = vodId)

    /**
     * Update last processed series info ID.
     *
     * @param seriesId Last series ID that was processed
     * @return Updated checkpoint
     */
    fun withLastSeriesInfoId(seriesId: Int): XtreamSyncCheckpoint = copy(lastSeriesInfoId = seriesId)

    /** Check if sync is fully completed */
    val isCompleted: Boolean
        get() = phase == XtreamSyncPhase.COMPLETED

    /** Check if we're in a list phase (can skip to next phase if desired) */
    val isListPhase: Boolean
        get() =
            phase in
                listOf(
                    XtreamSyncPhase.VOD_LIST,
                    XtreamSyncPhase.SERIES_LIST,
                    XtreamSyncPhase.LIVE_LIST,
                )

    /** Check if we're in an info backfill phase */
    val isInfoBackfillPhase: Boolean
        get() =
            phase in
                listOf(
                    XtreamSyncPhase.VOD_INFO,
                    XtreamSyncPhase.SERIES_INFO,
                )
}

/**
 * Xtream sync phases in execution order.
 *
 * Phases are processed sequentially. List phases are prioritized
 * to get content into the library quickly, then info backfill
 * fills in details (plot, cast, etc.).
 */
enum class XtreamSyncPhase {
    /** Scanning VOD items from get_vod_streams */
    VOD_LIST,

    /** Scanning series containers from get_series */
    SERIES_LIST,

    /** Scanning episodes for each series from get_series_info */
    SERIES_EPISODES,

    /** Scanning live channels from get_live_streams */
    LIVE_LIST,

    /** Backfilling VOD details from get_vod_info */
    VOD_INFO,

    /** Backfilling series details from get_series_info */
    SERIES_INFO,

    /** All phases completed */
    COMPLETED,
}
