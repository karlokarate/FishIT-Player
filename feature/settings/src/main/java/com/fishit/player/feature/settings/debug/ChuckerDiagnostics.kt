package com.fishit.player.feature.settings.debug

import android.content.Context

/**
 * Abstraction for Chucker HTTP Inspector diagnostics.
 *
 * Pattern follows LeakDiagnostics: interface in main/, implementation in debug/ and release/.
 * This allows the DebugScreen to open Chucker UI without compile-time dependency on Chucker in release.
 */
interface ChuckerDiagnostics {
    /** Whether Chucker is available (true in debug builds). */
    val isAvailable: Boolean

    /**
     * Launch the Chucker UI.
     *
     * @param context Android context for starting the activity
     * @return true if Chucker UI was launched, false if not available
     */
    fun openChuckerUi(context: Context): Boolean
}
