package com.fishit.player.core.debugsettings.di

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.fishit.player.core.debugsettings.DataStoreDebugToolsSettingsRepository
import com.fishit.player.core.debugsettings.DebugToolsSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for debug tools settings.
 *
 * **Provides:**
 * - DebugToolsSettingsRepository (DataStore-backed)
 * - DebugFlagsHolder (AtomicBoolean-backed runtime state)
 * - ChuckerInterceptor (for GatedChuckerInterceptor)
 * - GatedChuckerInterceptor (soft-gated Chucker)
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
    abstract fun bindDebugToolsSettingsRepository(
        impl: DataStoreDebugToolsSettingsRepository,
    ): DebugToolsSettingsRepository

    companion object {
        /**
         * Provides the underlying ChuckerInterceptor.
         * Always created, but gated at runtime via GatedChuckerInterceptor.
         */
        @Provides
        @Singleton
        fun provideChuckerInterceptor(
            @ApplicationContext context: Context,
        ): ChuckerInterceptor =
            ChuckerInterceptor.Builder(context)
                .maxContentLength(250_000L) // 250KB max body
                .alwaysReadResponseBody(false) // Don't read bodies by default (performance)
                .build()
    }
}
