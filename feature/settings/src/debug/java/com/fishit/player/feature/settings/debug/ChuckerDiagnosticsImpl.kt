package com.fishit.player.feature.settings.debug

import android.content.Context
import com.chuckerteam.chucker.api.Chucker
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
 * - [isAvailable] checks BuildConfig.INCLUDE_CHUCKER which can be disabled via Gradle properties
 * - When disabled, the UI will not show Chucker-related options
 */
@Singleton
class ChuckerDiagnosticsImpl
    @Inject
    constructor() : ChuckerDiagnostics {
        override val isAvailable: Boolean = BuildConfig.INCLUDE_CHUCKER

        override fun openChuckerUi(context: Context): Boolean =
            try {
                val intent = Chucker.getLaunchIntent(context)
                context.startActivity(intent)
                UnifiedLog.i(TAG) { "Opened Chucker HTTP Inspector UI" }
                true
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to open Chucker UI: ${e.message}" }
                false
            }

        private companion object {
            private const val TAG = "ChuckerDiagnostics"
        }
    }
