package com.fishit.player.infra.transport.xtream

import android.content.Context
import com.fishit.player.core.device.DeviceClassProvider

/**
 * XtreamTransportConfig – Zentrale Transport-Konfiguration gemäß Premium Contract.
 *
 * Diese Klasse ist die SSOT (Single Source of Truth) für:
 * - HTTP Timeouts (Section 3)
 * - User-Agent und Headers (Section 4)
 * - Device-Class-basierte Parallelität (Section 5)
 *
 * **Device Detection:**
 * Uses DeviceClassProvider from core:device-api (proper PLATIN architecture).
 * Device detection implementation is in infra:device-android.
 *
 * @see <a href="contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md">Premium Contract</a>
 * @see DeviceClassProvider for device detection interface
 */
object XtreamTransportConfig {
    // =========================================================================
    // HTTP Timeouts (Premium Contract Section 3)
    // "These values MUST be configured: connect/read/write/call jeweils 30s"
    // Rationale: Script uses --max-time 30
    // =========================================================================

    /** Connect timeout in seconds. */
    const val CONNECT_TIMEOUT_SECONDS: Long = 30L

    /** Read timeout in seconds. */
    const val READ_TIMEOUT_SECONDS: Long = 30L

    /** Write timeout in seconds. */
    const val WRITE_TIMEOUT_SECONDS: Long = 30L

    /** Call timeout in seconds (mandatory, hard stop semantics). */
    const val CALL_TIMEOUT_SECONDS: Long = 30L

    // =========================================================================
    // Streaming Timeouts (Extended for large JSON responses)
    // PERF FIX: Socket closed errors during catalog sync (21k+ items)
    // =========================================================================

    /**
     * Extended read timeout for streaming large JSON arrays (VOD, Live, Series catalogs).
     * Regular API calls use [READ_TIMEOUT_SECONDS] (30s).
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
    // "User-Agent: FishIT-Player/2.x (Android) (mandatory)"
    // =========================================================================

    /** Premium User-Agent string. */
    const val USER_AGENT: String = "FishIT-Player/2.x (Android)"

    /** Accept header for JSON responses. */
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
