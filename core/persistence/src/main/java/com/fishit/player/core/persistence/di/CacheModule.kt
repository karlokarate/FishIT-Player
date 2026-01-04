package com.fishit.player.core.persistence.di

import com.fishit.player.core.persistence.cache.HomeContentCache
import com.fishit.player.core.persistence.cache.impl.InMemoryHomeCache
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for cache infrastructure.
 *
 * **Phase 2: In-Memory Cache**
 * - InMemoryHomeCache bound as HomeContentCache
 * - Singleton scope for global cache instance
 *
 * **Layer Compliance:**
 * - Module in core/persistence (infra layer)
 * - No UI or feature dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CacheModule {
    @Binds @Singleton
    abstract fun bindHomeContentCache(impl: InMemoryHomeCache): HomeContentCache
}
