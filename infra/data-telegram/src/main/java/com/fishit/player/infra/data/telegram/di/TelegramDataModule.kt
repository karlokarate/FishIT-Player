package com.fishit.player.infra.data.telegram.di

import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.telegrammedia.domain.TelegramMediaRepository
import com.fishit.player.infra.data.telegram.ObxTelegramContentRepository
import com.fishit.player.infra.data.telegram.TelegramContentRepository
import com.fishit.player.infra.data.telegram.TelegramMediaRepositoryAdapter
import com.fishit.player.infra.data.telegram.auth.TelegramAuthRepositoryImpl
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
    abstract fun bindTelegramContentRepository(impl: ObxTelegramContentRepository): TelegramContentRepository

    // NOTE: TelegramMediaRepository binding has been migrated to NxDataModule (NX-based implementation).
    // The legacy adapter is kept for reference but binding is disabled to avoid DI conflicts.

    // /**
    //  * Binds the adapter that implements the feature's repository interface.
    //  *
    //  * This allows the feature layer to depend on its own interface
    //  * while the data layer provides the implementation (Dependency Inversion).
    //  *
    //  * @deprecated Replaced by NxTelegramMediaRepositoryImpl in infra:data-nx.
    //  */
    // @Binds
    // @Singleton
    // abstract fun bindTelegramMediaRepository(adapter: TelegramMediaRepositoryAdapter): TelegramMediaRepository

    @Binds
    @Singleton
    abstract fun bindTelegramAuthRepository(impl: TelegramAuthRepositoryImpl): TelegramAuthRepository
}
