package com.fishit.player.infra.data.telegram.di

import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.infra.data.telegram.ObxTelegramContentRepository
import com.fishit.player.infra.data.telegram.TelegramContentRepository
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
 * - TelegramAuthRepository: Telegram authentication state management
 *
 * **Note:** TelegramMediaRepository is now provided by NxDataModule (NX-based implementation).
 *
 * **Dependencies (from core:persistence):**
 * - BoxStore: Provided by PersistenceModule
 *
 * **Consumers:**
 * - CatalogSync: Persists catalog events to repository
 * - Feature modules: Query Telegram media via NxTelegramMediaRepositoryImpl
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramDataModule {
    @Binds
    @Singleton
    abstract fun bindTelegramContentRepository(impl: ObxTelegramContentRepository): TelegramContentRepository

    @Binds
    @Singleton
    abstract fun bindTelegramAuthRepository(impl: TelegramAuthRepositoryImpl): TelegramAuthRepository
}
