package com.fishit.player.feature.settings.debug

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op implementation of [ChuckerDiagnostics] for release builds.
 *
 * Chucker is not available in release builds.
 */
@Singleton
class ChuckerDiagnosticsImpl
    @Inject
    constructor() : ChuckerDiagnostics {
        override val isAvailable: Boolean = false

        override fun openChuckerUi(context: Context): Boolean = false
    }
