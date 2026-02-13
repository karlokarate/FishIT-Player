package com.fishit.player.infra.transport.xtream

import android.content.Context
import com.fishit.player.core.device.DeviceClassProvider
import com.fishit.player.infra.networking.PlatformHttpConfig

/**
 * XtreamTransportConfig – Xtream-specific transport configuration (Premium Contract).
 *
 * Contains ONLY values that are **Xtream-specific** and differ from platform defaults.
 * Base timeouts, User-Agent, and connection pool are provided by [@PlatformHttpClient].
 *
 * This class is the SSOT for:
 * - callTimeout (Section 3 — Xtream-specific hard stop)
 * - Streaming Timeouts (extended for large catalog downloads)
 * - Accept header (JSON for Xtream API)
 * - Device-class parallelism (Section 5)
 *
 * **Parent–Child HTTP Architecture:**
 * Base connect/read/write timeouts and User-Agent come from [PlatformHttpConfig].
 * This config adds Xtream overrides applied via `OkHttpClient.newBuilder()`.
 *
 * @see PlatformHttpConfig for inherited platform defaults
 * @see <a href="contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md">Premium Contract</a>
 * @see DeviceClassProvider for device detection interface
 */
object XtreamTransportConfig {
    // =========================================================================
    // Xtream-Specific Timeouts (Premium Contract Section 3)
    // Base connect/read/write (30s each) inherited from PlatformHttpConfig
    // =========================================================================

    /** Call timeout in seconds (mandatory, hard stop semantics). Xtream-specific. */
    const val CALL_TIMEOUT_SECONDS: Long = 30L

    // =========================================================================
    // Streaming Timeouts (Extended for large JSON responses)
    // PERF FIX: Socket closed errors during catalog sync (21k+ items)
    // =========================================================================

    /**
     * Extended read timeout for streaming large JSON arrays (VOD, Live, Series catalogs).
     * Regular API calls use platform default (30s).
     * Streaming calls use this value (120s) to handle 20k+ item responses.
     *
     * Note: The call timeout is set per-request using OkHttpClient.newBuilder().
     */
    const val STREAMING_READ_TIMEOUT_SECONDS: Long = 120L

    /**
     * Extended call timeout for streaming operations.
     * Allows enough time for full catalog downloads.
     */
    const val STREAMING_CALL_TIMEOUT_SECONDS: Long = 180L

    // =========================================================================
    // Headers (Premium Contract Section 4)
    // User-Agent now lives in PlatformHttpConfig (app-wide SSOT)
    // =========================================================================

    /**
     * Premium User-Agent string.
     * Delegates to [PlatformHttpConfig.USER_AGENT] — the app-wide SSOT.
     */
    const val USER_AGENT: String = PlatformHttpConfig.USER_AGENT

    /** Accept header for JSON responses. Xtream-specific. */
    const val ACCEPT_JSON: String = "application/json"

    /** Accept-Encoding header for compressed responses. */
    const val ACCEPT_ENCODING: String = "gzip"

    // =========================================================================
    // Parallelism (Premium Contract Section 5)
    // "Phone/Tablet: target parallelism = 12"
    // "FireTV/low-RAM: target parallelism = 3"
    // =========================================================================

    /** Default parallelism for phone/tablet devices. */
    const val PARALLELISM_PHONE_TABLET: Int = 12

    /** Reduced parallelism for FireTV/Android TV/low-RAM devices. */
    const val PARALLELISM_FIRETV_LOW_RAM: Int = 3

    /**
     * Get the appropriate parallelism for the current device using DeviceClassProvider.
     *
     * This method uses the centralized DeviceClassProvider from core:device-api.
     * The actual device detection implementation is in infra:device-android.
     *
     * Premium Contract Section 5:
     * - Phone/Tablet/TV: parallelism = 12
     * - TV_LOW_RAM: parallelism = 3
     *
     * @param deviceClassProvider Provider for device classification (injected via Hilt)
     * @param context Android context for device detection
     * @return Parallelism level (number of concurrent requests)
     * @see DeviceClassProvider for device detection abstraction
     */
    fun getParallelism(
        deviceClassProvider: DeviceClassProvider,
        context: Context,
    ): Int {
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        return if (deviceClass.isLowResource) {
            PARALLELISM_FIRETV_LOW_RAM
        } else {
            PARALLELISM_PHONE_TABLET
        }
    }



    // =========================================================================
    // Rate Limiting (from existing implementation)
    // =========================================================================

    /** Minimum interval between calls to same host in milliseconds. */
    const val MIN_INTERVAL_MS: Long = 120L

    // =========================================================================
    // Cache TTLs
    // =========================================================================

    /** General response cache TTL in milliseconds. */
    const val CACHE_TTL_MS: Long = 60_000L

    /** EPG-specific cache TTL (shorter for fresher data). */
    const val EPG_CACHE_TTL_MS: Long = 15_000L

    /** Port cache TTL in milliseconds (24h). */
    const val PORT_CACHE_TTL_MS: Long = 24 * 60 * 60 * 1000L

    /** Capability cache TTL in milliseconds (6h). */
    const val CAPABILITY_CACHE_TTL_MS: Long = 6 * 60 * 60 * 1000L
}
