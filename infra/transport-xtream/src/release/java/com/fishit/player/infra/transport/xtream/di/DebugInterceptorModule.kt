package com.fishit.player.infra.transport.xtream.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import javax.inject.Singleton

/**
 * Release variant: Provides no-op interceptor (no Chucker).
 *
 * **Contract:**
 * - No debug tools in release builds
 * - Zero overhead (immediate pass-through)
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugInterceptorModule {
    @Provides
    @Singleton
    fun provideChuckerInterceptor(): Interceptor =
        Interceptor { chain ->
            chain.proceed(chain.request())
        }
}
