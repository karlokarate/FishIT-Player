package com.fishit.player.core.synccommon.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.fishit.player.core.model.sync.DeviceProfile
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime device profile detection for adaptive sync parameters.
 *
 * **Purpose:**
 * Detect device capabilities at runtime to select optimal sync parameters
 * (buffer size, batch size, consumer count) for the current hardware.
 *
 * **Detection Strategy:**
 * 1. Check device model/manufacturer for known devices (FireTV, Shield, etc.)
 * 2. Fall back to memory-based detection
 * 3. Cache result for session
 *
 * **Known Device Mappings:**
 * - Amazon Fire TV Stick: FIRETV_STICK (512MB-1GB usable)
 * - Amazon Fire TV Cube: FIRETV_CUBE (1.5GB+ usable)
 * - NVIDIA Shield TV: SHIELD_TV (2GB+ usable)
 * - Chromecast with Google TV: CHROMECAST_GTV (1.5GB usable)
 * - Generic Android TV: ANDROID_TV_GENERIC
 * - Phones/Tablets: Based on RAM
 *
 * **Usage:**
 * ```kotlin
 * val detector = DeviceProfileDetector(context)
 * val profile = detector.detect()
 *
 * val buffer = ChannelSyncBuffer<RawMediaMetadata>(
 *     capacity = profile.bufferCapacity
 * )
 * ```
 *
 * @param context Application context for system services
 */
@Singleton
class DeviceProfileDetector
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "DeviceProfileDetector"

            // Memory thresholds (in MB)
            private const val LOW_RAM_THRESHOLD_MB = 2048 // 2GB
            private const val HIGH_RAM_THRESHOLD_MB = 4096 // 4GB

            // Known device identifiers
            private val FIRETV_STICK_MODELS =
                setOf(
                    "AFTB",
                    "AFTSS",
                    "AFTT",
                    "AFTS",
                    "AFTM", // Fire TV Sticks
                    "AFTN",
                    "AFTA", // Fire TV Stick 4K, Fire TV Stick Lite
                )
            private val FIRETV_CUBE_MODELS =
                setOf(
                    "AFTR",
                    "AFTKA", // Fire TV Cube
                )
            private val SHIELD_MODELS =
                setOf(
                    "SHIELD",
                    "SHIELD Android TV",
                )
            private val CHROMECAST_MODELS =
                setOf(
                    "Sabrina",
                    "Chromecast", // Chromecast with Google TV
                )
        }

        @Volatile
        private var cachedProfile: DeviceProfile? = null

        /**
         * Detect the device profile.
         *
         * @param forceRefresh If true, re-detect even if cached
         * @return Detected device profile
         */
        fun detect(forceRefresh: Boolean = false): DeviceProfile {
            if (!forceRefresh) {
                cachedProfile?.let { return it }
            }

            val profile = detectInternal()
            cachedProfile = profile
            UnifiedLog.i(TAG) { "Detected device profile: $profile (buffer=${profile.bufferCapacity}, batch=${profile.dbBatchSize})" }
            return profile
        }

        /**
         * Get current detected profile (or AUTO if not yet detected).
         */
        val currentProfile: DeviceProfile
            get() = cachedProfile ?: DeviceProfile.AUTO

        private fun detectInternal(): DeviceProfile {
            val model = Build.MODEL ?: ""
            val manufacturer = Build.MANUFACTURER ?: ""
            val device = Build.DEVICE ?: ""

            // Check known device models
            FIRETV_STICK_MODELS.forEach { prefix ->
                if (model.startsWith(prefix) || device.startsWith(prefix)) {
                    return DeviceProfile.FIRETV_STICK
                }
            }

            FIRETV_CUBE_MODELS.forEach { prefix ->
                if (model.startsWith(prefix) || device.startsWith(prefix)) {
                    return DeviceProfile.FIRETV_CUBE
                }
            }

            if (SHIELD_MODELS.any { model.contains(it, ignoreCase = true) }) {
                return DeviceProfile.SHIELD_TV
            }

            if (CHROMECAST_MODELS.any { model.contains(it, ignoreCase = true) }) {
                return DeviceProfile.CHROMECAST_GTV
            }

            // Check if Android TV (Leanback feature)
            val isAndroidTv = context.packageManager.hasSystemFeature("android.software.leanback")

            // Get available memory
            val memoryMb = getAvailableMemoryMb()

            return when {
                isAndroidTv && memoryMb >= HIGH_RAM_THRESHOLD_MB -> DeviceProfile.SHIELD_TV
                isAndroidTv && memoryMb >= LOW_RAM_THRESHOLD_MB -> DeviceProfile.ANDROID_TV_GENERIC
                isAndroidTv -> DeviceProfile.FIRETV_STICK // Assume low-end TV device
                isTablet() && memoryMb >= LOW_RAM_THRESHOLD_MB -> DeviceProfile.TABLET
                memoryMb >= HIGH_RAM_THRESHOLD_MB -> DeviceProfile.PHONE_HIGH_RAM
                else -> DeviceProfile.PHONE_LOW_RAM
            }
        }

        /**
         * Get total available memory in MB.
         */
        private fun getAvailableMemoryMb(): Long {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            return memInfo.totalMem / (1024 * 1024)
        }

        /**
         * Check if device is likely a tablet based on screen size.
         */
        private fun isTablet(): Boolean {
            val metrics = context.resources.displayMetrics
            val widthDp = metrics.widthPixels / metrics.density
            val heightDp = metrics.heightPixels / metrics.density
            val smallestWidth = minOf(widthDp, heightDp)
            return smallestWidth >= 600 // Standard tablet threshold
        }

        /**
         * Get device info for debugging.
         */
        fun getDeviceInfo(): DeviceInfo =
            DeviceInfo(
                model = Build.MODEL ?: "unknown",
                manufacturer = Build.MANUFACTURER ?: "unknown",
                device = Build.DEVICE ?: "unknown",
                sdkVersion = Build.VERSION.SDK_INT,
                totalMemoryMb = getAvailableMemoryMb(),
                isAndroidTv = context.packageManager.hasSystemFeature("android.software.leanback"),
                isTablet = isTablet(),
                detectedProfile = currentProfile,
            )
    }

/**
 * Device information for debugging.
 */
data class DeviceInfo(
    val model: String,
    val manufacturer: String,
    val device: String,
    val sdkVersion: Int,
    val totalMemoryMb: Long,
    val isAndroidTv: Boolean,
    val isTablet: Boolean,
    val detectedProfile: DeviceProfile,
) {
    override fun toString(): String =
        buildString {
            append("DeviceInfo(")
            append("model=$model, ")
            append("mfr=$manufacturer, ")
            append("sdk=$sdkVersion, ")
            append("mem=${totalMemoryMb}MB, ")
            append("tv=$isAndroidTv, ")
            append("tablet=$isTablet, ")
            append("profile=$detectedProfile")
            append(")")
        }
}
