package com.fishit.player.pipeline.telegram.catalog.di

import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogMessageMapper
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogMessageMapperImpl
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogPipeline
import com.fishit.player.pipeline.telegram.catalog.TelegramCatalogPipelineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Telegram Catalog Pipeline.
 *
 * Provides DI bindings for:
 * - [TelegramCatalogPipeline] implementation
 * - [TelegramCatalogMessageMapper] implementation
 *
 * All bindings are singletons to ensure consistent behavior across the app.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramCatalogModule {
    @Binds
    @Singleton
    abstract fun bindTelegramCatalogPipeline(impl: TelegramCatalogPipelineImpl): TelegramCatalogPipeline

    @Binds
    @Singleton
    abstract fun bindTelegramCatalogMessageMapper(impl: TelegramCatalogMessageMapperImpl): TelegramCatalogMessageMapper
}
