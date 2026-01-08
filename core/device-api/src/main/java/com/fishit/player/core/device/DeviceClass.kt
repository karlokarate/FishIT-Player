package com.fishit.player.core.device

/**
 * Device classification for performance tuning.
 *
 * This enum represents the final classification used by all performance-sensitive
 * components (batch sizes, parallelism, caching policies, etc.).
 *
 * **PLATIN Contract:**
 * - This is the SSOT (Single Source of Truth) for device classification
 * - All performance configs consume ONLY this enum
 * - No component should perform device detection themselves
 *
 * @see DeviceClassProvider for runtime device detection
 */
enum class DeviceClass {
    /**
     * TV device with low RAM (< 2GB or flagged as low-RAM).
     * Examples: FireTV Stick, older Android TV devices.
     *
     * Performance profile:
     * - Small batch sizes (35 items)
     * - Low parallelism (3 concurrent operations)
     * - Conservative memory usage
     */
    TV_LOW_RAM,

    /**
     * TV device with adequate RAM (≥ 2GB).
     * Examples: Nvidia Shield, newer Android TV devices.
     *
     * Performance profile:
     * - Medium batch sizes (100 items)
     * - Medium parallelism (6 concurrent operations)
     * - Balanced memory usage
     */
    TV,

    /**
     * Phone or tablet device.
     * Examples: Most Android phones and tablets.
     *
     * Performance profile:
     * - Large batch sizes (100-600 items depending on phase)
     * - High parallelism (12 concurrent operations)
     * - Aggressive memory usage for best performance
     */
    PHONE_TABLET,
    ;

    /**
     * Get the recommended parallelism level for this device class.
     *
     * This is the maximum number of concurrent network/IO operations
     * that should be performed simultaneously.
     *
     * @return Parallelism level (3 for TV_LOW_RAM, 6 for TV, 12 for PHONE_TABLET)
     */
    val parallelism: Int
        get() =
            when (this) {
                TV_LOW_RAM -> 3
                TV -> 6
                PHONE_TABLET -> 12
            }

    /**
     * Check if this is a TV device (any variant).
     *
     * @return true if TV_LOW_RAM or TV, false otherwise
     */
    val isTV: Boolean
        get() = this == TV_LOW_RAM || this == TV

    /**
     * Check if this is a low-resource device requiring conservative settings.
     *
     * @return true if TV_LOW_RAM, false otherwise
     */
    val isLowResource: Boolean
        get() = this == TV_LOW_RAM
}
