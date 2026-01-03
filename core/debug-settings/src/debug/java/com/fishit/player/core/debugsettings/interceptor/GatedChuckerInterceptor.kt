package com.fishit.player.core.debugsettings.interceptor

import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.fishit.player.core.debugsettings.DebugFlagsHolder
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gated Chucker interceptor with runtime toggle.
 *
 * **Contract:**
 * - If disabled (default): immediately chain.proceed(request) - minimal overhead
 * - If enabled: delegate to ChuckerInterceptor.intercept
 * - No second OkHttpClient created
 * - Thread-safe via AtomicBoolean
 *
 * **Usage:**
 * ```kotlin
 * OkHttpClient.Builder()
 *     .addInterceptor(gatedChuckerInterceptor)
 *     .build()
 * ```
 *
 * **Toggle:**
 * ```kotlin
 * // Via DebugToolsInitializer:
 * settingsRepo.networkInspectorEnabledFlow.collect { enabled ->
 *     flagsHolder.chuckerEnabled.set(enabled)
 * }
 * ```
 */
@Singleton
class GatedChuckerInterceptor
    @Inject
    constructor(
        private val flagsHolder: DebugFlagsHolder,
        private val chuckerInterceptor: ChuckerInterceptor,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            // Fast path: if disabled, bypass Chucker
            if (!flagsHolder.chuckerEnabled.get()) {
                return chain.proceed(chain.request())
            }

            // Enabled: delegate to actual Chucker interceptor
            return chuckerInterceptor.intercept(chain)
        }
    }
