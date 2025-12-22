package com.fishit.player.infra.transport.xtream

/**
 * Shared HTTP header defaults for Xtream integrations.
 *
 * Premium Contract Section 4:
 * - User-Agent: FishIT-Player/2.x (Android) (mandatory)
 * - Accept: application/json
 * - Accept-Encoding: gzip
 *
 * @see XtreamTransportConfig for centralized configuration.
 * @see <a href="contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md">Premium Contract Section 4</a>
 */
object XtreamHttpHeaders {

    /**
     * Premium User-Agent string (mandatory per Premium Contract).
     * Centralized in [XtreamTransportConfig.USER_AGENT].
     */
    const val PREMIUM_USER_AGENT: String = XtreamTransportConfig.USER_AGENT

    /**
     * Legacy User-Agent for backward compatibility with older panel configs.
     * @deprecated Use [PREMIUM_USER_AGENT] for new code.
     */
    @Deprecated(
        message = "Use PREMIUM_USER_AGENT per Premium Contract Section 4",
        replaceWith = ReplaceWith("PREMIUM_USER_AGENT"),
    )
    const val LEGACY_USER_AGENT: String = "IBOPlayer/1.4 (Android)"

    /**
     * Default header set per Premium Contract Section 4.
     *
     * Always includes:
     * - User-Agent: FishIT-Player/2.x (Android)
     * - Accept: application/json
     * - Accept-Encoding: gzip
     *
     * @param referer Optional referer to include.
     */
    fun defaults(referer: String? = null): Map<String, String> =
        buildMap {
            put("User-Agent", PREMIUM_USER_AGENT)
            put("Accept", XtreamTransportConfig.ACCEPT_JSON)
            put("Accept-Encoding", XtreamTransportConfig.ACCEPT_ENCODING)
            referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
        }

    /**
     * Merge caller-supplied headers with premium defaults.
     *
     * The returned map always includes the premium headers when absent.
     */
    fun withDefaults(headers: Map<String, String> = emptyMap(), referer: String? = null): Map<String, String> {
        val normalizedReferer = referer?.takeIf { it.isNotBlank() }
        val merged = headers.toMutableMap()

        merged.putIfAbsent("User-Agent", PREMIUM_USER_AGENT)
        merged.putIfAbsent("Accept", XtreamTransportConfig.ACCEPT_JSON)
        merged.putIfAbsent("Accept-Encoding", XtreamTransportConfig.ACCEPT_ENCODING)
        normalizedReferer?.let { merged.putIfAbsent("Referer", it) }

        return merged
    }
}
