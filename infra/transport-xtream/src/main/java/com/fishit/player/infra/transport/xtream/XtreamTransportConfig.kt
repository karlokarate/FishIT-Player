package com.fishit.player.infra.transport.xtream

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * XtreamTransportConfig – Zentrale Transport-Konfiguration gemäß Premium Contract.
 *
 * Diese Klasse ist die SSOT (Single Source of Truth) für:
 * - HTTP Timeouts (Section 3)
 * - User-Agent und Headers (Section 4)
 * - Device-Class-basierte Parallelität (Section 5)
 *
 * @see <a href="contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md">Premium Contract</a>
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
    // "Phone/Tablet: target parallelism = 10"
    // "FireTV/low-RAM: target parallelism = 3"
    // =========================================================================

    /** Default parallelism for phone/tablet devices. */
    const val PARALLELISM_PHONE_TABLET: Int = 10

    /** Reduced parallelism for FireTV/Android TV/low-RAM devices. */
    const val PARALLELISM_FIRETV_LOW_RAM: Int = 3

    /** Low RAM threshold in MB (below this, use reduced parallelism). */
    private const val LOW_RAM_THRESHOLD_MB: Long = 2048L

    /**
     * Device classification for transport tuning.
     */
    enum class DeviceClass {
        PHONE_TABLET,
        TV_LOW_RAM,
        ;

        val parallelism: Int
            get() =
                when (this) {
                    PHONE_TABLET -> PARALLELISM_PHONE_TABLET
                    TV_LOW_RAM -> PARALLELISM_FIRETV_LOW_RAM
                }
    }

    /**
     * Detect the device class for transport tuning.
     *
     * @param context Android context for device detection.
     * @return DeviceClass based on device characteristics.
     */
    fun detectDeviceClass(context: Context): DeviceClass {
        // Check if TV
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTV = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        // Check for low RAM
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE)
                as? android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamMB = memoryInfo.totalMem / (1024 * 1024)
        val isLowRam = totalRamMB < LOW_RAM_THRESHOLD_MB || activityManager?.isLowRamDevice == true

        return when {
            isTV -> DeviceClass.TV_LOW_RAM
            isLowRam -> DeviceClass.TV_LOW_RAM
            else -> DeviceClass.PHONE_TABLET
        }
    }

    /**
     * Get the appropriate parallelism for the current device.
     *
     * @param context Android context for device detection.
     * @return Parallelism level (number of concurrent requests).
     */
    fun getParallelism(context: Context): Int = detectDeviceClass(context).parallelism

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
