package com.fishit.player.core.epg.model

/**
 * CanonicalChannelId – Stable channel identity across sources.
 *
 * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-30):
 * For Xtream: `CanonicalChannelId = "xtream:<providerKey>:<channel_id>"`
 *
 * This ensures:
 * - Unique identification per provider account
 * - Stable EPG mapping regardless of provider stream_id changes
 * - Clean separation from ephemeral IDs
 *
 * @param value The canonical channel identifier string
 */
@JvmInline
value class CanonicalChannelId(val value: String) {
    init {
        require(value.isNotBlank()) { "CanonicalChannelId must not be blank" }
    }

    companion object {
        /**
         * Create Xtream channel ID from provider key and EPG channel ID.
         *
         * @param providerKey Unique provider/account identifier
         * @param epgChannelId The epg_channel_id from Xtream stream
         * @return CanonicalChannelId or null if epgChannelId is blank
         */
        fun fromXtream(providerKey: String, epgChannelId: String?): CanonicalChannelId? {
            if (epgChannelId.isNullOrBlank()) return null
            return CanonicalChannelId("xtream:$providerKey:$epgChannelId")
        }

        /**
         * Create Xtream channel ID using stream ID as fallback.
         * Use only when epg_channel_id is not available.
         *
         * @param providerKey Unique provider/account identifier
         * @param streamId The live stream ID
         * @return CanonicalChannelId
         */
        fun fromXtreamStreamId(providerKey: String, streamId: Int): CanonicalChannelId {
            return CanonicalChannelId("xtream:$providerKey:stream_$streamId")
        }
    }

    /**
     * Extract the source type from the canonical ID.
     * @return "xtream" or other source identifier
     */
    val sourceType: String
        get() = value.substringBefore(':')

    /**
     * Extract the provider key from the canonical ID.
     * @return Provider key or empty if malformed
     */
    val providerKey: String
        get() = value.split(':').getOrNull(1).orEmpty()

    /**
     * Extract the raw channel identifier from the canonical ID.
     * @return Raw channel ID or empty if malformed
     */
    val rawChannelId: String
        get() = value.split(':', limit = 3).getOrNull(2).orEmpty()

    override fun toString(): String = value
}
