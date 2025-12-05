package com.fishit.player.v2

import android.app.Application
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
        // Initialization logic will be added in later phases:
        // - DeviceProfile detection
        // - Local profile loading
        // - Pipeline initialization (background)
    }
}
