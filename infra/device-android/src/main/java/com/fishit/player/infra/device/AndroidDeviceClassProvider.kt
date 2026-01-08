package com.fishit.player.infra.device

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import com.fishit.player.core.device.DeviceClass
import com.fishit.player.core.device.DeviceClassProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of DeviceClassProvider.
 *
 * **PLATIN Architecture:**
 * - Reads runtime signals from Android system services
 * - Creates DeviceProfile (raw data)
 * - Maps DeviceProfile → DeviceClass via pure function
 * - Caches result (device class doesn't change at runtime)
 *
 * **Detection Strategy:**
 * 1. Check UiModeManager for TV mode
 * 2. Check ActivityManager for low-RAM flag
 * 3. Check total RAM via MemoryInfo
 * 4. Apply deterministic mapping
 *
 * **Contract Compliance:**
 * - Uses ONLY runtime signals (no manifest checks)
 * - Deterministic (same device → same result)
 * - Cached (single detection per app lifecycle)
 * - Testable (pure mapping function can be unit tested)
 *
 * @see DeviceClassProvider for interface contract
 * @see DeviceProfile for raw data model
 * @see mapDeviceProfileToClass for mapping logic
 */
@Singleton
class AndroidDeviceClassProvider @Inject constructor() : DeviceClassProvider {
    @Volatile
    private var cachedDeviceClass: DeviceClass? = null

    override fun getDeviceClass(context: Context): DeviceClass {
        // Return cached result if available
        cachedDeviceClass?.let { return it }

        // Detect device profile from runtime signals
        val profile = detectDeviceProfile(context)

        // Map profile to device class via pure function
        val deviceClass = mapDeviceProfileToClass(profile)

        // Cache and return
        cachedDeviceClass = deviceClass
        return deviceClass
    }

    /**
     * Detect device profile from Android system services.
     *
     * This function reads raw runtime signals and packages them into
     * a DeviceProfile data class. No classification logic here.
     *
     * @param context Android context for system service access
     * @return DeviceProfile with runtime characteristics
     */
    private fun detectDeviceProfile(context: Context): DeviceProfile {
        // Check if TV via UiModeManager
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTV = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        // Check RAM via ActivityManager
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamMB = memoryInfo.totalMem / (1024 * 1024)
        val isLowRamDevice = activityManager?.isLowRamDevice == true

        return DeviceProfile(
            isTV = isTV,
            totalRamMB = totalRamMB,
            isLowRamDevice = isLowRamDevice,
        )
    }

    companion object {
        /**
         * Map DeviceProfile to DeviceClass.
         *
         * **Pure function** - deterministic mapping with NO side effects.
         * This can be unit tested with example profiles.
         *
         * **Classification Logic:**
         * - TV + Low RAM → TV_LOW_RAM (conservative settings)
         * - TV + Normal RAM → TV (balanced settings)
         * - Non-TV → PHONE_TABLET (aggressive settings)
         *
         * **Low RAM Detection:**
         * - ActivityManager.isLowRamDevice flag (highest priority)
         * - OR total RAM < 2GB threshold
         *
         * @param profile Raw device profile from runtime detection
         * @return DeviceClass for performance tuning
         */
        fun mapDeviceProfileToClass(profile: DeviceProfile): DeviceClass =
            when {
                // TV with low RAM → conservative settings
                profile.isTV && (
                    profile.isLowRamDevice ||
                        profile.totalRamMB < DeviceProfile.LOW_RAM_THRESHOLD_MB
                ) ->
                    DeviceClass.TV_LOW_RAM

                // TV with adequate RAM → balanced settings
                profile.isTV -> DeviceClass.TV

                // Phone/Tablet → aggressive settings
                else -> DeviceClass.PHONE_TABLET
            }
    }
}
