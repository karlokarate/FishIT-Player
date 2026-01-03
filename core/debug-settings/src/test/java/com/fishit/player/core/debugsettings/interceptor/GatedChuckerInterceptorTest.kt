package com.fishit.player.core.debugsettings.interceptor

import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.fishit.player.core.debugsettings.DebugFlagsHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull

/**
 * Unit tests for GatedChuckerInterceptor.
 *
 * Tests that:
 * - When disabled (default): bypasses Chucker and proceeds immediately
 * - When enabled: delegates to actual ChuckerInterceptor
 */
class GatedChuckerInterceptorTest {
    private lateinit var flagsHolder: DebugFlagsHolder
    private lateinit var chuckerInterceptor: ChuckerInterceptor
    private lateinit var gatedInterceptor: GatedChuckerInterceptor
    private lateinit var chain: Interceptor.Chain
    private lateinit var request: Request
    private lateinit var response: Response

    @Before
    fun setup() {
        flagsHolder = DebugFlagsHolder()
        chuckerInterceptor = mockk(relaxed = true)
        gatedInterceptor = GatedChuckerInterceptor(flagsHolder, chuckerInterceptor)

        request = Request.Builder().url("https://example.com").build()
        response = mockk(relaxed = true)
        chain =
            mockk {
                every { request() } returns request
                every { proceed(any()) } returns response
            }
    }

    @Test
    fun `bypasses when disabled (default state)`() {
        // Default state: disabled (false)
        val result = gatedInterceptor.intercept(chain)

        // Should proceed immediately without calling Chucker
        assertNotNull(result)
        verify(exactly = 1) { chain.proceed(request) }
        verify(exactly = 0) { chuckerInterceptor.intercept(any()) }
    }

    @Test
    fun `bypasses when explicitly disabled`() {
        // Explicitly disable
        flagsHolder.chuckerEnabled.set(false)

        val result = gatedInterceptor.intercept(chain)

        // Should proceed immediately without calling Chucker
        assertNotNull(result)
        verify(exactly = 1) { chain.proceed(request) }
        verify(exactly = 0) { chuckerInterceptor.intercept(any()) }
    }

    @Test
    fun `delegates to Chucker when enabled`() {
        // Enable Chucker
        flagsHolder.chuckerEnabled.set(true)
        every { chuckerInterceptor.intercept(chain) } returns response

        val result = gatedInterceptor.intercept(chain)

        // Should delegate to actual ChuckerInterceptor
        assertNotNull(result)
        verify(exactly = 1) { chuckerInterceptor.intercept(chain) }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `can toggle at runtime`() {
        // Start disabled
        flagsHolder.chuckerEnabled.set(false)
        gatedInterceptor.intercept(chain)
        verify(exactly = 1) { chain.proceed(any()) }
        verify(exactly = 0) { chuckerInterceptor.intercept(any()) }

        // Enable
        flagsHolder.chuckerEnabled.set(true)
        every { chuckerInterceptor.intercept(chain) } returns response
        gatedInterceptor.intercept(chain)
        verify(exactly = 1) { chuckerInterceptor.intercept(any()) }

        // Disable again
        flagsHolder.chuckerEnabled.set(false)
        gatedInterceptor.intercept(chain)
        verify(exactly = 2) { chain.proceed(any()) }
    }
}
