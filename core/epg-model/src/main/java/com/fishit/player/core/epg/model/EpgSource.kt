package com.fishit.player.core.epg.model

/**
 * EpgSource – Identifies the origin of EPG data.
 *
 * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-10, EPG-11):
 * - XTREAM is the primary canonical source
 * - Future sources may include XMLTV, etc.
 */
enum class EpgSource {
    /**
     * Xtream Codes API EPG (get_short_epg, get_simple_data_table).
     * Primary canonical source per contract.
     */
    XTREAM,

    /**
     * XMLTV format EPG (future).
     * External XMLTV file import.
     */
    XMLTV,

    /**
     * Manual EPG entry (future).
     * User-created programme entries.
     */
    MANUAL,

    /**
     * Unknown/fallback source.
     */
    UNKNOWN,
}
