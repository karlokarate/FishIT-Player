package com.fishit.player.infra.device

/**
 * Runtime device profile captured from Android system services.
 *
 * This is a pure data class representing the raw device characteristics
 * detected at runtime. It contains NO business logic for classification.
 *
 * **Separation of Concerns:**
 * - DeviceProfile = Raw runtime data (what we detect)
 * - DeviceClass = Classification enum (what we decide)
 * - Mapping function = Pure Kotlin logic (how we decide)
 *
 * @property isTV True if device is a TV (detected via UiModeManager)
 * @property totalRamMB Total RAM in megabytes
 * @property isLowRamDevice True if ActivityManager.isLowRamDevice() returns true
 */
data class DeviceProfile(
    val isTV: Boolean,
    val totalRamMB: Long,
    val isLowRamDevice: Boolean,
) {
    companion object {
        /** RAM threshold in MB below which a device is considered low-RAM. */
        const val LOW_RAM_THRESHOLD_MB: Long = 2048L
    }
}
