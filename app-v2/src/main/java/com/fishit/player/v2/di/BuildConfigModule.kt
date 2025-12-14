package com.fishit.player.v2.di

import com.fishit.player.v2.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/**
 * Provides BuildConfig values for dependency injection.
 *
 * **Purpose:**
 * - Expose app-level config values (API keys, version) to infra modules
 * - Keeps config source (BuildConfig) in app module
 * - Allows infra modules to receive values via DI
 *
 * **Architecture (Phase B2):**
 * - app-v2 owns BuildConfig access
 * - infra modules declare @Named parameters
 * - This module bridges the gap via DI
 */
@Module
@InstallIn(SingletonComponent::class)
object BuildConfigModule {
    @Provides
    @Named("TG_API_ID")
    fun provideTelegramApiId(): Int = BuildConfig.TG_API_ID

    @Provides
    @Named("TG_API_HASH")
    fun provideTelegramApiHash(): String = BuildConfig.TG_API_HASH

    @Provides
    @Named("APP_VERSION")
    fun provideAppVersion(): String = BuildConfig.VERSION_NAME
}
