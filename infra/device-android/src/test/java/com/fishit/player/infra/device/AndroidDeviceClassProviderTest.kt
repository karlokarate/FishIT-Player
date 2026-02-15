package com.fishit.player.infra.device

import com.fishit.player.core.device.DeviceClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for device profile to device class mapping.
 *
 * These tests validate the pure mapping function using example device profiles
 * for various real-world devices (FireTV, Shield, Pixel, etc.).
 *
 * **Contract:**
 * - Mapping function is pure (no side effects)
 * - Deterministic (same input → same output)
 * - Covers all device classes
 */
class AndroidDeviceClassProviderTest {
    @Test
    fun `FireTV Stick 4K (1-5GB RAM) returns TV_LOW_RAM`() {
        val profile =
            DeviceProfile(
                isTV = true,
                totalRamMB = 1536, // 1.5GB
                isLowRamDevice = false,
            )
        val result = AndroidDeviceClassProvider.mapDeviceProfileToClass(profile)
        assertEquals(DeviceClass.TV_LOW_RAM, result)
    }

    @Test
    fun `FireTV Stick (1GB RAM, low-RAM flag) returns TV_LOW_RAM`() {
        val profile =
            DeviceProfile(
                isTV = true,
                totalRamMB = 1024, // 1GB
                isLowRamDevice = true,
            )
        val result = AndroidDeviceClassProvider.mapDeviceProfileToClass(profile)
        assertEquals(DeviceClass.TV_LOW_RAM, result)
    }

    @Test
    fun `Nvidia Shield (3GB RAM) returns TV`() {
        val profile =
            DeviceProfile(
                isTV = true,
                totalRamMB = 3072, // 3GB
                isLowRamDevice = false,
            )
        val result = AndroidDeviceClassProvider.mapDeviceProfileToClass(profile)
        assertEquals(DeviceClass.TV, result)
    }

    @Test
    fun `Chromecast with Google TV (2GB RAM) returns TV`() {
        val profile =
            DeviceProfile(
                isTV = true,
                totalRamMB = 2048, // Exactly at threshold
                isLowRamDevice = false,
            )
        val result = AndroidDeviceClassProvider.mapDeviceProfileToClass(profile)
        assertEquals(DeviceClass.TV, result)
    }

    @Test
    fun `Chromecast with Google TV (low-RAM flag despite RAM) returns TV_LOW_RAM`() {
        val profile =
            DeviceProfile(
                isTV = true,
                totalRamMB = 2048, // At threshold, but flagged as low-RAM
                isLowRamDevice = true,
            )
        val result = AndroidDeviceClassProvider.mapDeviceProfileToClass(profile)
        assertEquals(DeviceClass.TV_LOW_RAM, result)
    }

    @Test
    fun `Pixel 5 (8GB RAM) returns PHONE_TABLET`() {
        val profile =
            DeviceProfile(
                isTV = false,
                totalRamMB = 8192, // 8GB
                isLowRamDevice = false,
            )
        val result = AndroidDeviceClassProvider.mapDeviceProfileToClass(profile)
        assertEquals(DeviceClass.PHONE_TABLET, result)
    }

    @Test
    fun `Samsung Galaxy Tab S8 (12GB RAM) returns PHONE_TABLET`() {
        val profile =
            DeviceProfile(
                isTV = false,
                totalRamMB = 12288, // 12GB
                isLowRamDevice = false,
            )
        val result = AndroidDeviceClassProvider.mapDeviceProfileToClass(profile)
        assertEquals(DeviceClass.PHONE_TABLET, result)
    }

    @Test
    fun `Old Android Go phone (1GB RAM, low-RAM flag) returns PHONE_TABLET`() {
        // Note: Even low-RAM phones get PHONE_TABLET class
        // because they're not TVs. App may struggle but it's the correct classification.
        val profile =
            DeviceProfile(
                isTV = false,
                totalRamMB = 1024, // 1GB
                isLowRamDevice = true,
            )
        val result = AndroidDeviceClassProvider.mapDeviceProfileToClass(profile)
        assertEquals(DeviceClass.PHONE_TABLET, result)
    }

    @Test
    fun `Budget tablet (1-5GB RAM) returns PHONE_TABLET`() {
        val profile =
            DeviceProfile(
                isTV = false,
                totalRamMB = 1536, // 1.5GB
                isLowRamDevice = false,
            )
        val result = AndroidDeviceClassProvider.mapDeviceProfileToClass(profile)
        assertEquals(DeviceClass.PHONE_TABLET, result)
    }

    @Test
    fun `Parallelism values are correct for each device class`() {
        assertEquals(3, DeviceClass.TV_LOW_RAM.parallelism)
        assertEquals(6, DeviceClass.TV.parallelism)
        assertEquals(12, DeviceClass.PHONE_TABLET.parallelism)
    }

    @Test
    fun `isTV property works correctly`() {
        assertEquals(true, DeviceClass.TV_LOW_RAM.isTV)
        assertEquals(true, DeviceClass.TV.isTV)
        assertEquals(false, DeviceClass.PHONE_TABLET.isTV)
    }

    @Test
    fun `isLowResource property works correctly`() {
        assertEquals(true, DeviceClass.TV_LOW_RAM.isLowResource)
        assertEquals(false, DeviceClass.TV.isLowResource)
        assertEquals(false, DeviceClass.PHONE_TABLET.isLowResource)
    }
}
