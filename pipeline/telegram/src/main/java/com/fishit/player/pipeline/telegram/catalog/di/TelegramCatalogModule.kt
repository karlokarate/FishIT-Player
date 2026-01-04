package com.fishit.player.pipeline.telegram.catalog.di

import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogPipeline
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogPipelineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Telegram catalog pipeline bindings.
 *
 * Provides:
 * - TelegramCatalogPipeline: Event-based catalog scanning
 *
 * **Dependencies (must be provided elsewhere):**
 * - TelegramClient: From infra:transport-telegram (TelegramTransportModule)
 *
 * **Consumers:**
 * - CatalogSync: Processes catalog events and persists to storage
 * - Feature modules: For manual sync triggers
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramCatalogModule {
    @Binds
    @Singleton
    abstract fun bindTelegramCatalogPipeline(impl: TelegramCatalogPipelineImpl): TelegramCatalogPipeline
}
