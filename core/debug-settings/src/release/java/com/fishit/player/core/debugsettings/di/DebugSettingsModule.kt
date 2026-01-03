package com.fishit.player.core.debugsettings.di

import com.fishit.player.core.debugsettings.DataStoreDebugToolsSettingsRepository
import com.fishit.player.core.debugsettings.DebugToolsSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Release variant of Hilt module for debug tools settings.
 *
 * **Provides:**
 * - DebugToolsSettingsRepository (DataStore-backed)
 * - DebugFlagsHolder (provided via constructor injection)
 * - No ChuckerInterceptor (uses no-op in release)
 * - No debug tool dependencies
 *
 * **Contract:**
 * - Release builds only
 * - All debug tools are no-op
 * - Zero overhead
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugSettingsModule {
    @Binds
    @Singleton
    abstract fun bindDebugToolsSettingsRepository(impl: DataStoreDebugToolsSettingsRepository): DebugToolsSettingsRepository
}
