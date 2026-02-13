package com.fishit.player.infra.networking

/**
 * PlatformHttpConfig – Shared HTTP defaults for ALL pipeline clients.
 *
 * These are **sensible defaults** that pipeline-specific clients inherit via
 * [OkHttpClient.newBuilder()][okhttp3.OkHttpClient.newBuilder]. Each pipeline MAY override
 * individual values (e.g., Xtream overrides callTimeout, streaming overrides readTimeout).
 *
 * **NOT included here (pipeline-specific):**
 * - callTimeout → Xtream needs 30s, streaming needs 180s, others may differ
 * - Accept header → Xtream needs application/json, others may need text/xml
 * - Dispatcher limits → Device-class parallelism is Xtream-specific
 * - followSslRedirects → Xtream disables it for security
 *
 * @see com.fishit.player.infra.networking.di.NetworkingModule
 */
object PlatformHttpConfig {
    // =========================================================================
    // Timeouts (sensible defaults, overridable per pipeline)
    // =========================================================================

    /** Connect timeout in seconds. */
    const val CONNECT_TIMEOUT_SECONDS: Long = 30L

    /** Read timeout in seconds. */
    const val READ_TIMEOUT_SECONDS: Long = 30L

    /** Write timeout in seconds. */
    const val WRITE_TIMEOUT_SECONDS: Long = 30L

    // NOTE: No callTimeout here — it's pipeline-specific.
    // Xtream uses 30s, streaming uses 180s. Platform leaves it at OkHttp default (0 = no limit).

    // =========================================================================
    // Headers
    // =========================================================================

    /** App-wide User-Agent string. */
    const val USER_AGENT: String = "FishIT-Player/2.x (Android)"
}
