package com.fishit.player.core.debugsettings.interceptor

import com.fishit.player.core.debugsettings.DebugFlagsHolder
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Release variant of GatedChuckerInterceptor - no-op pass-through.
 *
 * **Contract:**
 * - Always bypasses (no Chucker in release)
 * - Zero overhead (immediate chain.proceed)
 * - Thread-safe
 */
@Singleton
class GatedChuckerInterceptor
    @Inject
    constructor(
        private val flagsHolder: DebugFlagsHolder,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            // Release builds: always pass-through (no Chucker)
            return chain.proceed(chain.request())
        }
    }
