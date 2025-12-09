package com.fishit.player.infra.data.telegram.di

import com.fishit.player.infra.data.telegram.ObxTelegramContentRepository
import com.fishit.player.infra.data.telegram.TelegramContentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Telegram data layer bindings.
 *
 * Provides:
 * - TelegramContentRepository: ObjectBox-backed repository for Telegram media
 *
 * **Dependencies (from core:persistence):**
 * - BoxStore: Provided by PersistenceModule
 *
 * **Consumers:**
 * - CatalogSync: Persists catalog events to repository
 * - Feature modules: Query and observe Telegram media
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
}
