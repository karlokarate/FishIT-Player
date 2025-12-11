package com.fishit.player.v2

import android.app.Application
import com.fishit.player.infra.logging.UnifiedLogInitializer
import dagger.hilt.android.HiltAndroidApp

/**
 * FishIT Player v2 Application.
 *
 * Entry point for the v2 generation of FishIT Player.
 * Uses Hilt for dependency injection.
 */
@HiltAndroidApp
class FishItV2Application : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize unified logging system FIRST
        // This ensures all subsequent logging works correctly
        UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)

        // Initialization logic will be added in later phases:
        // - DeviceProfile detection
        // - Local profile loading
        // - Pipeline initialization (background)
    }
}
