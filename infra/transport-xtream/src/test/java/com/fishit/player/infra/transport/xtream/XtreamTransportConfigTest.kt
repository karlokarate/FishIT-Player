package com.fishit.player.infra.transport.xtream

import android.content.Context
import com.fishit.player.core.device.DeviceClass
import com.fishit.player.core.device.DeviceClassProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for XtreamTransportConfig.
 *
 * Verifies Premium Contract Section 5 compliance:
 * - Phone/Tablet/TV: parallelism = 12
 * - TV_LOW_RAM: parallelism = 3
 *
 * Uses the new DeviceClassProvider architecture from core:device-api.
 */
class XtreamTransportConfigTest {
    @Test
    fun `getParallelism returns 12 for PHONE_TABLET device`() {
        // Arrange
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        val phoneTabletDevice = DeviceClass.PHONE_TABLET

        every { deviceClassProvider.getDeviceClass(context) } returns phoneTabletDevice

        // Act
        val result = XtreamTransportConfig.getParallelism(deviceClassProvider, context)

        // Assert
        assertEquals(12, result)
    }

    @Test
    fun `getParallelism returns 12 for TV device (not low-RAM)`() {
        // Arrange
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        val tvDevice = DeviceClass.TV

        every { deviceClassProvider.getDeviceClass(context) } returns tvDevice

        // Act
        val result = XtreamTransportConfig.getParallelism(deviceClassProvider, context)

        // Assert
        assertEquals(12, result)
    }

    @Test
    fun `getParallelism returns 3 for TV_LOW_RAM device`() {
        // Arrange
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        val lowRamDevice = DeviceClass.TV_LOW_RAM

        every { deviceClassProvider.getDeviceClass(context) } returns lowRamDevice

        // Act
        val result = XtreamTransportConfig.getParallelism(deviceClassProvider, context)

        // Assert
        assertEquals(3, result)
    }

    @Test
    fun `constants match Premium Contract Section 5`() {
        assertEquals(12, XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
        assertEquals(3, XtreamTransportConfig.PARALLELISM_FIRETV_LOW_RAM)
    }
}
