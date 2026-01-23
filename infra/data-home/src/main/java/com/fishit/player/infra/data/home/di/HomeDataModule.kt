package com.fishit.player.infra.data.home.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for Home data layer bindings.
 *
 * **MIGRATION NOTE (Dec 2025):**
 * The HomeContentRepository binding has been moved to NxDataModule in infra:data-nx.
 * This module is kept for backward compatibility but is now empty.
 * It can be removed once the migration to NX is complete.
 *
 * **Old bindings (now in NxDataModule):**
 * - HomeContentRepository → NxHomeContentRepositoryImpl (was HomeContentRepositoryAdapter)
 *
 * **Legacy:**
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
    // HomeContentRepository binding moved to NxDataModule
    // This module is kept empty for now during migration
}
