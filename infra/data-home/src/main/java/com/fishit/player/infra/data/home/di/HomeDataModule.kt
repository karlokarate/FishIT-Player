package com.fishit.player.infra.data.home.di

import com.fishit.player.core.home.domain.HomeContentRepository
import com.fishit.player.infra.data.home.HomeContentRepositoryAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Home data layer bindings.
 *
 * Provides:
 * - HomeContentRepository: Composite adapter aggregating Telegram and Xtream repositories
 *
 * **Dependencies:**
 * - TelegramContentRepository: From infra:data-telegram
 * - XtreamCatalogRepository: From infra:data-xtream
 * - XtreamLiveRepository: From infra:data-xtream
 *
 * **Consumers:**
 * - feature:home: HomeViewModel uses HomeContentRepository for content aggregation
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeDataModule {

    /**
     * Binds the adapter that implements the feature's repository interface.
     *
     * This allows the Home feature to depend on its own interface
     * while the data layer provides the implementation (Dependency Inversion).
     */
    @Binds
    @Singleton
    abstract fun bindHomeContentRepository(
        adapter: HomeContentRepositoryAdapter
    ): HomeContentRepository
}
