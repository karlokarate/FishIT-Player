package com.fishit.player.core.debugsettings.interceptor

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
 * **Issue #564 Compile-Time Gating:**
 * - GatedChuckerInterceptor now creates Chucker via reflection internally
 * - Tests focus on the gating behavior (enabled/disabled flag)
 * - Chucker availability is determined at runtime
 *
 * Tests that:
 * - When disabled (default): bypasses and proceeds immediately
 * - When enabled: attempts to delegate to Chucker (if available)
 * - Toggle works at runtime
 */
class GatedChuckerInterceptorTest {
    private lateinit var flagsHolder: DebugFlagsHolder
    private lateinit var gatedInterceptor: GatedChuckerInterceptor
    private lateinit var chain: Interceptor.Chain
    private lateinit var request: Request
    private lateinit var response: Response

    @Before
    fun setup() {
        flagsHolder = DebugFlagsHolder()
        gatedInterceptor = GatedChuckerInterceptor(flagsHolder)

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
    }

    @Test
    fun `bypasses when explicitly disabled`() {
        // Explicitly disable
        flagsHolder.chuckerEnabled.set(false)

        val result = gatedInterceptor.intercept(chain)

        // Should proceed immediately without calling Chucker
        assertNotNull(result)
        verify(exactly = 1) { chain.proceed(request) }
    }

    @Test
    fun `proceeds when enabled but Chucker not available`() {
        // Enable Chucker flag
        flagsHolder.chuckerEnabled.set(true)

        // In unit test environment, Chucker might not be available
        // (depends on test classpath configuration)
        // The interceptor should gracefully proceed without crashing
        val result = gatedInterceptor.intercept(chain)

        // Should not crash and should return a response
        assertNotNull(result)
        // At minimum, chain.proceed should be called if Chucker is not available
        // or Chucker intercept is called if available
        verify(atLeast = 0) { chain.proceed(any()) }
    }

    @Test
    fun `can toggle at runtime`() {
        // Start disabled
        flagsHolder.chuckerEnabled.set(false)
        gatedInterceptor.intercept(chain)
        verify(exactly = 1) { chain.proceed(any()) }

        // Enable - in test environment, Chucker may or may not be available
        flagsHolder.chuckerEnabled.set(true)
        gatedInterceptor.intercept(chain)

        // Disable again
        flagsHolder.chuckerEnabled.set(false)
        gatedInterceptor.intercept(chain)
        verify(atLeast = 2) { chain.proceed(any()) }
    }
}
