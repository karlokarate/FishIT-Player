package com.fishit.player.infra.data.telegram.di

import com.fishit.player.feature.telegram.domain.TelegramMediaRepository
import com.fishit.player.infra.data.telegram.ObxTelegramContentRepository
import com.fishit.player.infra.data.telegram.TelegramContentRepository
import com.fishit.player.infra.data.telegram.TelegramMediaRepositoryAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Telegram data layer bindings.
 *
 * Provides:
 * - TelegramContentRepository: ObjectBox-backed repository for Telegram media (RawMediaMetadata)
 * - TelegramMediaRepository: Feature-facing adapter (domain models)
 *
 * **Dependencies (from core:persistence):**
 * - BoxStore: Provided by PersistenceModule
 *
 * **Consumers:**
 * - CatalogSync: Persists catalog events to repository
 * - Feature modules: Query and observe Telegram media via adapter
 * - Domain layer: Business logic and use cases
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramDataModule {

    @Binds
    @Singleton
    abstract fun bindTelegramContentRepository(
        impl: ObxTelegramContentRepository
    ): TelegramContentRepository

    /**
     * Binds the adapter that implements the feature's repository interface.
     *
     * This allows the feature layer to depend on its own interface
     * while the data layer provides the implementation (Dependency Inversion).
     */
    @Binds
    @Singleton
    abstract fun bindTelegramMediaRepository(
        adapter: TelegramMediaRepositoryAdapter
    ): TelegramMediaRepository
}
