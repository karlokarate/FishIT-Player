package com.fishit.player.infra.cache.di

import com.fishit.player.infra.cache.CacheManager
import com.fishit.player.infra.cache.DefaultCacheManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for cache management.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CacheModule {

    @Binds
    @Singleton
    abstract fun bindCacheManager(impl: DefaultCacheManager): CacheManager
}
