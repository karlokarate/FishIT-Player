package com.fishit.player.feature.settings.debug

import android.content.Context
import com.fishit.player.feature.settings.BuildConfig
import com.fishit.player.infra.logging.UnifiedLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug implementation of [ChuckerDiagnostics] using Chucker 4.x.
 *
 * Opens the Chucker HTTP Inspector UI which shows all captured HTTP requests.
 *
 * **Issue #564 Compliance:**
 * - [isAvailable] checks BuildConfig.INCLUDE_CHUCKER AND runtime class availability
 * - When disabled via Gradle properties, Chucker classes are not in the APK
 * - Uses reflection to safely check for Chucker presence
 */
@Singleton
class ChuckerDiagnosticsImpl
    @Inject
    constructor() : ChuckerDiagnostics {
        // Check both BuildConfig flag AND actual class presence (compile-time gating)
        override val isAvailable: Boolean = BuildConfig.INCLUDE_CHUCKER && isChuckerClassPresent()

        override fun openChuckerUi(context: Context): Boolean {
            if (!isAvailable) {
                UnifiedLog.w(TAG) { "Chucker not available (disabled via compile-time gating)" }
                return false
            }
            return try {
                // Use reflection to avoid compile-time dependency when Chucker is excluded
                val chuckerClass = Class.forName("com.chuckerteam.chucker.api.Chucker")
                val getLaunchIntentMethod = chuckerClass.getMethod("getLaunchIntent", Context::class.java)
                val intent = getLaunchIntentMethod.invoke(null, context) as android.content.Intent
                context.startActivity(intent)
                UnifiedLog.i(TAG) { "Opened Chucker HTTP Inspector UI" }
                true
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to open Chucker UI: ${e.message}" }
                false
            }
        }

        private fun isChuckerClassPresent(): Boolean =
            try {
                Class.forName("com.chuckerteam.chucker.api.Chucker")
                true
            } catch (e: ClassNotFoundException) {
                false
            }

        private companion object {
            private const val TAG = "ChuckerDiagnostics"
        }
    }
