package com.fishit.player.core.debugsettings.di

import com.fishit.player.core.debugsettings.DataStoreDebugToolsSettingsRepository
import com.fishit.player.core.debugsettings.DebugToolsSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for debug tools settings.
 *
 * **Issue #564 Compile-Time Gating:**
 * - ChuckerInterceptor is NO LONGER provided via DI
 * - GatedChuckerInterceptor creates Chucker via reflection when available
 * - This allows the module to compile even when Chucker is excluded
 *
 * **Provides:**
 * - DebugToolsSettingsRepository (DataStore-backed)
 * - DebugFlagsHolder (AtomicBoolean-backed runtime state)
 * - DebugToolsInitializer (syncs DataStore to runtime flags)
 *
 * **Contract:**
 * - DEBUG builds only
 * - All defaults are OFF
 * - No global mutable singletons (uses DI)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugSettingsModule {
    @Binds
    @Singleton
    abstract fun bindDebugToolsSettingsRepository(impl: DataStoreDebugToolsSettingsRepository): DebugToolsSettingsRepository
}
