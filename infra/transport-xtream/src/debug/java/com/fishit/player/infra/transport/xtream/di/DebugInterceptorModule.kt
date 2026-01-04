package com.fishit.player.infra.transport.xtream.di

import com.fishit.player.core.debugsettings.interceptor.GatedChuckerInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import javax.inject.Singleton

/**
 * Debug variant: Provides GatedChuckerInterceptor for runtime toggle.
 *
 * **Contract:**
 * - Chucker is OFF by default
 * - User can enable via Settings
 * - No reinstall needed
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugInterceptorModule {
    @Provides
    @Singleton
    fun provideChuckerInterceptor(gatedChuckerInterceptor: GatedChuckerInterceptor): Interceptor = gatedChuckerInterceptor
}
