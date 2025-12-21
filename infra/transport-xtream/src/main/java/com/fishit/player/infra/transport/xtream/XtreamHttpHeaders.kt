package com.fishit.player.infra.transport.xtream

/**
 * Shared HTTP header defaults for Xtream integrations.
 *
 * Legacy-compatible defaults mimic common Android Xtream players so that
 * panels that gate requests on User-Agent or Referer continue to work when
 * callers provide no explicit headers.
 */
object XtreamHttpHeaders {
    const val LEGACY_USER_AGENT: String = "IBOPlayer/1.4 (Android)"

    /**
     * Default header set when no caller-supplied headers are available.
     *
     * @param referer Optional referer to include alongside the legacy user agent
     */
    fun defaults(referer: String? = null): Map<String, String> =
        buildMap {
            put("User-Agent", LEGACY_USER_AGENT)
            referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
        }

    /**
     * Merge caller-supplied headers with legacy defaults when needed.
     *
     * The returned map always includes the legacy User-Agent when absent and
     * adds the provided Referer when it is not already present.
     */
    fun withDefaults(
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
    ): Map<String, String> {
        val normalizedReferer = referer?.takeIf { it.isNotBlank() }
        val merged = headers.toMutableMap()

        merged.putIfAbsent("User-Agent", LEGACY_USER_AGENT)
        normalizedReferer?.let { merged.putIfAbsent("Referer", it) }

        return merged
    }
}
