package com.fishit.player.core.device

import android.content.Context

/**
 * Provider interface for device classification.
 *
 * This is the SSOT (Single Source of Truth) abstraction for detecting
 * the device class at runtime. All performance-sensitive components should
 * use this interface rather than performing their own device detection.
 *
 * **PLATIN Architecture:**
 * - Interface lives in `core:device-api` (pure abstraction, no implementation)
 * - Implementation lives in `infra:device-android` (Android-specific runtime detection)
 * - Consumers inject this interface via Hilt/Dagger
 *
 * **Implementation Contract:**
 * - MUST be deterministic for the same device (no random behavior)
 * - SHOULD cache the result (device class doesn't change at runtime)
 * - MUST use only runtime signals (UiModeManager, ActivityManager, etc.)
 * - MUST NOT use manifest/build-time checks for performance tuning
 *
 * @see DeviceClass for the classification enum
 */
interface DeviceClassProvider {
    /**
     * Get the device class for the current device.
     *
     * This method reads runtime device characteristics and returns the
     * appropriate device classification for performance tuning.
     *
     * **Implementation Requirements:**
     * - Check UiModeManager for TV detection
     * - Check ActivityManager.isLowRamDevice for low-RAM detection
     * - Check total RAM via ActivityManager.MemoryInfo
     * - Apply deterministic mapping: DeviceProfile → DeviceClass
     *
     * @param context Android context for accessing system services
     * @return DeviceClass based on runtime device characteristics
     */
    fun getDeviceClass(context: Context): DeviceClass
}
