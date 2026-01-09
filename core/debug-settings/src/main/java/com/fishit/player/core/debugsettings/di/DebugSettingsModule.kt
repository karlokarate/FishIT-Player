package com.fishit.player.core.debugsettings.di

import com.fishit.player.core.debugsettings.DataStoreDebugToolsSettingsRepository
import com.fishit.player.core.debugsettings.DebugToolsSettingsRepository
import com.fishit.player.core.debugsettings.nx.DataStoreNxMigrationSettingsRepository
import com.fishit.player.core.debugsettings.nx.NxMigrationSettingsRepository
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
 * **Issue #621 NX Migration Settings:**
 * - NxMigrationSettingsRepository controls OBX PLATIN migration modes
 * - All defaults are conservative (LEGACY_ONLY, OFF, HIDDEN)
 *
 * **Provides:**
 * - DebugToolsSettingsRepository (DataStore-backed)
 * - NxMigrationSettingsRepository (DataStore-backed, Issue #621)
 * - DebugFlagsHolder (AtomicBoolean-backed runtime state)
 * - DebugToolsInitializer (syncs DataStore to runtime flags)
 *
 * **Contract:**
 * - DEBUG builds only
 * - All defaults are OFF / LEGACY_ONLY / HIDDEN
 * - No global mutable singletons (uses DI)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugSettingsModule {
    @Binds
    @Singleton
    abstract fun bindDebugToolsSettingsRepository(
        impl: DataStoreDebugToolsSettingsRepository
    ): DebugToolsSettingsRepository

    @Binds
    @Singleton
    abstract fun bindNxMigrationSettingsRepository(
        impl: DataStoreNxMigrationSettingsRepository
    ): NxMigrationSettingsRepository
}
