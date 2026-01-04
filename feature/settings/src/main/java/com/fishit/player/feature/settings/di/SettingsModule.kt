package com.fishit.player.feature.settings.di

import com.fishit.player.feature.settings.debug.ChuckerDiagnostics
import com.fishit.player.feature.settings.debug.ChuckerDiagnosticsImpl
import com.fishit.player.feature.settings.debug.DebugToolsController
import com.fishit.player.feature.settings.debug.DebugToolsControllerImpl
import com.fishit.player.feature.settings.debug.LeakDiagnostics
import com.fishit.player.feature.settings.debug.LeakDiagnosticsImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for settings feature dependencies.
 *
 * Binds:
 * - [LeakDiagnostics] to [LeakDiagnosticsImpl] (memory leak detection)
 * - [ChuckerDiagnostics] to [ChuckerDiagnosticsImpl] (HTTP inspector)
 * - [DebugToolsController] to [DebugToolsControllerImpl] (runtime tool toggles)
 *
 * The actual implementations differ between debug and release source sets:
 * - Debug: Real LeakCanary/Chucker integration
 * - Release: No-op stubs
 *
 * **Compile-time Gating:**
 * This module ensures NO references to :core:debug-settings appear in release builds.
 * All debug tool functionality is isolated via interfaces with separate implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindLeakDiagnostics(impl: LeakDiagnosticsImpl): LeakDiagnostics

    @Binds
    @Singleton
    abstract fun bindChuckerDiagnostics(impl: ChuckerDiagnosticsImpl): ChuckerDiagnostics

    @Binds
    @Singleton
    abstract fun bindDebugToolsController(impl: DebugToolsControllerImpl): DebugToolsController
}
