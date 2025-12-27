package com.fishit.player.feature.settings.di

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
 * Binds [LeakDiagnostics] to [LeakDiagnosticsImpl].
 * The actual implementation differs between debug and release source sets:
 * - Debug: Real LeakCanary integration
 * - Release: No-op stub
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindLeakDiagnostics(impl: LeakDiagnosticsImpl): LeakDiagnostics
}
