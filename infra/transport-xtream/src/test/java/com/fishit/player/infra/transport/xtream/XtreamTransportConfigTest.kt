package com.fishit.player.infra.transport.xtream

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for XtreamTransportConfig.
 *
 * Verifies Premium Contract Section 5 compliance:
 * - Phone/Tablet: parallelism = 12
 * - FireTV/low-RAM: parallelism = 3
 */
class XtreamTransportConfigTest {
    @Test
    fun `detectDeviceClass returns PHONE_TABLET for non-TV high-RAM device`() {
        // Arrange
        val context = mockk<Context>()
        val uiModeManager = mockk<UiModeManager>()
        val activityManager = mockk<ActivityManager>()
        val memoryInfo =
            ActivityManager.MemoryInfo().apply {
                totalMem = 4L * 1024 * 1024 * 1024 // 4GB RAM
            }

        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = memoryInfo.totalMem
        }
        every { activityManager.isLowRamDevice } returns false

        // Act
        val result = XtreamTransportConfig.detectDeviceClass(context)

        // Assert
        assertEquals(XtreamTransportConfig.DeviceClass.PHONE_TABLET, result)
        assertEquals(12, result.parallelism)
    }

    @Test
    fun `detectDeviceClass returns TV_LOW_RAM for TV device`() {
        // Arrange
        val context = mockk<Context>()
        val uiModeManager = mockk<UiModeManager>()
        val activityManager = mockk<ActivityManager>()
        val memoryInfo =
            ActivityManager.MemoryInfo().apply {
                totalMem = 4L * 1024 * 1024 * 1024 // 4GB RAM
            }

        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_TELEVISION
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = memoryInfo.totalMem
        }
        every { activityManager.isLowRamDevice } returns false

        // Act
        val result = XtreamTransportConfig.detectDeviceClass(context)

        // Assert
        assertEquals(XtreamTransportConfig.DeviceClass.TV_LOW_RAM, result)
        assertEquals(3, result.parallelism)
    }

    @Test
    fun `detectDeviceClass returns TV_LOW_RAM for low-RAM device`() {
        // Arrange
        val context = mockk<Context>()
        val uiModeManager = mockk<UiModeManager>()
        val activityManager = mockk<ActivityManager>()
        val memoryInfo =
            ActivityManager.MemoryInfo().apply {
                totalMem = 1L * 1024 * 1024 * 1024 // 1GB RAM (below 2GB threshold)
            }

        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = memoryInfo.totalMem
        }
        every { activityManager.isLowRamDevice } returns false

        // Act
        val result = XtreamTransportConfig.detectDeviceClass(context)

        // Assert
        assertEquals(XtreamTransportConfig.DeviceClass.TV_LOW_RAM, result)
        assertEquals(3, result.parallelism)
    }

    @Test
    fun `detectDeviceClass returns TV_LOW_RAM when isLowRamDevice is true`() {
        // Arrange
        val context = mockk<Context>()
        val uiModeManager = mockk<UiModeManager>()
        val activityManager = mockk<ActivityManager>()
        val memoryInfo =
            ActivityManager.MemoryInfo().apply {
                totalMem = 4L * 1024 * 1024 * 1024 // 4GB RAM
            }

        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = memoryInfo.totalMem
        }
        every { activityManager.isLowRamDevice } returns true

        // Act
        val result = XtreamTransportConfig.detectDeviceClass(context)

        // Assert
        assertEquals(XtreamTransportConfig.DeviceClass.TV_LOW_RAM, result)
        assertEquals(3, result.parallelism)
    }

    @Test
    fun `getParallelism returns 12 for phone tablet`() {
        // Arrange
        val context = mockk<Context>()
        val uiModeManager = mockk<UiModeManager>()
        val activityManager = mockk<ActivityManager>()

        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = 4L * 1024 * 1024 * 1024
        }
        every { activityManager.isLowRamDevice } returns false

        // Act
        val result = XtreamTransportConfig.getParallelism(context)

        // Assert
        assertEquals(12, result)
    }

    @Test
    fun `getParallelism returns 3 for FireTV`() {
        // Arrange
        val context = mockk<Context>()
        val uiModeManager = mockk<UiModeManager>()
        val activityManager = mockk<ActivityManager>()

        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_TELEVISION
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.totalMem = 2L * 1024 * 1024 * 1024
        }
        every { activityManager.isLowRamDevice } returns false

        // Act
        val result = XtreamTransportConfig.getParallelism(context)

        // Assert
        assertEquals(3, result)
    }

    @Test
    fun `constants match Premium Contract Section 5`() {
        assertEquals(12, XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
        assertEquals(3, XtreamTransportConfig.PARALLELISM_FIRETV_LOW_RAM)
    }

    @Test
    fun `DeviceClass parallelism values are correct`() {
        assertEquals(12, XtreamTransportConfig.DeviceClass.PHONE_TABLET.parallelism)
        assertEquals(3, XtreamTransportConfig.DeviceClass.TV_LOW_RAM.parallelism)
    }
}
