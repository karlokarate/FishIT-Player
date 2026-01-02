package com.fishit.player.infra.transport.xtream

/**
 * Shared HTTP header defaults for Xtream integrations.
 *
 * **IMPORTANT: Two distinct header profiles exist:**
 *
 * 1. **API Headers** (for JSON/metadata requests):
 *    - Accept: application/json
 *    - Accept-Encoding: gzip
 *    - Used by: XtreamApiClient, metadata fetching
 *
 * 2. **PLAYBACK Headers** (for media streaming):
 *    - Accept: *&#47;*
 *    - Accept-Encoding: identity (NO compression - critical for streams!)
 *    - Used by: XtreamPlaybackSourceFactoryImpl, OkHttpDataSource
 *
 * **Why the distinction matters:**
 * - Cloudflare/WAF may reject video requests with JSON Accept headers
 * - Compressed video streams break playback (gzip on mp4/mkv/ts = corrupt)
 * - Legacy IPTV clients always used identity encoding for media
 *
 * Premium Contract Section 4:
 * - User-Agent: FishIT-Player/2.x (Android) (mandatory for BOTH profiles)
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

    // =========================================================================
    // API Headers (JSON/metadata requests)
    // =========================================================================

    /**
     * Default header set for **API/JSON requests** per Premium Contract Section 4.
     *
     * Always includes:
     * - User-Agent: FishIT-Player/2.x (Android)
     * - Accept: application/json
     * - Accept-Encoding: gzip
     *
     * **NOT for media playback!** Use [playbackDefaults] for streams.
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
     * Merge caller-supplied headers with premium API defaults.
     *
     * The returned map always includes the premium headers when absent.
     * **NOT for media playback!** Use [withPlaybackDefaults] for streams.
     */
    fun withDefaults(
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
    ): Map<String, String> {
        val normalizedReferer = referer?.takeIf { it.isNotBlank() }
        val merged = headers.toMutableMap()

        merged.putIfAbsent("User-Agent", PREMIUM_USER_AGENT)
        merged.putIfAbsent("Accept", XtreamTransportConfig.ACCEPT_JSON)
        merged.putIfAbsent("Accept-Encoding", XtreamTransportConfig.ACCEPT_ENCODING)
        normalizedReferer?.let { merged.putIfAbsent("Referer", it) }

        return merged
    }

    // =========================================================================
    // PLAYBACK Headers (media streaming)
    // =========================================================================

    /**
     * Default header set for **media playback** (Live/VOD/Series streams).
     *
     * **CRITICAL for legacy-parity and Cloudflare compatibility:**
     * - User-Agent: FishIT-Player/2.x (Android)
     * - Accept: *&#47;* (NOT application/json!)
     * - Accept-Encoding: identity (NO compression - critical!)
     * - Optional: Icy-MetaData: 1 (for IPTV stream metadata)
     *
     * **Why identity encoding?**
     * - Video streams must NOT be gzip-compressed
     * - Cloudflare/WAF may inject compression if gzip is requested
     * - Legacy IPTV clients always used identity
     *
     * @param referer Optional referer to include.
     * @param includeIcyMetadata Include Icy-MetaData header (for live streams).
     */
    fun playbackDefaults(
        referer: String? = null,
        includeIcyMetadata: Boolean = true,
    ): Map<String, String> =
        buildMap {
            put("User-Agent", PREMIUM_USER_AGENT)
            put("Accept", "*/*")
            put("Accept-Encoding", "identity")
            if (includeIcyMetadata) {
                put("Icy-MetaData", "1")
            }
            referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
        }

    /**
     * Merge caller-supplied headers with playback defaults.
     *
     * The returned map always includes the playback headers when absent.
     * Use this for OkHttpDataSource / ExoPlayer media requests.
     *
     * @param headers Caller-supplied headers (may override defaults)
     * @param referer Optional referer to include.
     * @param includeIcyMetadata Include Icy-MetaData header (for live streams).
     */
    fun withPlaybackDefaults(
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        includeIcyMetadata: Boolean = true,
    ): Map<String, String> {
        val normalizedReferer = referer?.takeIf { it.isNotBlank() }
        val merged = headers.toMutableMap()

        merged.putIfAbsent("User-Agent", PREMIUM_USER_AGENT)
        merged.putIfAbsent("Accept", "*/*")
        merged.putIfAbsent("Accept-Encoding", "identity")
        if (includeIcyMetadata) {
            merged.putIfAbsent("Icy-MetaData", "1")
        }
        normalizedReferer?.let { merged.putIfAbsent("Referer", it) }

        return merged
    }
}
