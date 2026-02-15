package com.fishit.player.core.debugsettings.interceptor

import com.fishit.player.core.debugsettings.DebugFlagsHolder
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gated Chucker interceptor with runtime toggle.
 *
 * **Issue #564 Compile-Time Gating:**
 * - Uses reflection to delegate to ChuckerInterceptor when available
 * - When Chucker is not in the classpath (disabled via Gradle properties),
 *   this interceptor becomes a no-op passthrough
 *
 * **Contract:**
 * - If disabled (default): immediately chain.proceed(request) - minimal overhead
 * - If enabled AND Chucker available: delegate to ChuckerInterceptor.intercept
 * - No second OkHttpClient created
 * - Thread-safe via AtomicBoolean
 */
@Singleton
class GatedChuckerInterceptor
    @Inject
    constructor(
        private val flagsHolder: DebugFlagsHolder,
    ) : Interceptor {
        // Lazily check if Chucker is available and create the interceptor
        private val chuckerInterceptor: Interceptor? by lazy {
            createChuckerInterceptor()
        }

        private val isChuckerAvailable: Boolean by lazy {
            chuckerInterceptor != null
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            // Fast path: if disabled OR Chucker not available, bypass
            if (!flagsHolder.chuckerEnabled.get() || !isChuckerAvailable) {
                return chain.proceed(chain.request())
            }

            // Enabled and available: delegate to actual Chucker interceptor
            return chuckerInterceptor!!.intercept(chain)
        }

        /**
         * Creates ChuckerInterceptor via reflection to avoid compile-time dependency.
         * Returns null if Chucker classes are not available.
         */
        private fun createChuckerInterceptor(): Interceptor? =
            try {
                // Get the Application context from Android's current process
                val appClass = Class.forName("android.app.ActivityThread")
                val currentAppMethod = appClass.getMethod("currentApplication")
                val context = currentAppMethod.invoke(null) as android.content.Context

                // Create ChuckerInterceptor via reflection
                val builderClass = Class.forName("com.chuckerteam.chucker.api.ChuckerInterceptor\$Builder")
                val constructor = builderClass.getConstructor(android.content.Context::class.java)
                val builder = constructor.newInstance(context)
                val buildMethod = builderClass.getMethod("build")
                buildMethod.invoke(builder) as Interceptor
            } catch (e: ClassNotFoundException) {
                // Chucker not in classpath - this is expected when disabled
                null
            } catch (e: Exception) {
                // Log error but don't crash
                null
            }
    }
