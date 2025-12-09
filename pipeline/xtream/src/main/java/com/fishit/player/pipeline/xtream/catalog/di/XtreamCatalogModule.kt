package com.fishit.player.pipeline.xtream.catalog.di

import com.fishit.player.pipeline.xtream.catalog.DefaultXtreamCatalogSource
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogMapper
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogMapperImpl
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogPipeline
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogPipelineImpl
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Xtream catalog pipeline bindings.
 *
 * Provides:
 * - XtreamCatalogPipeline: Event-based catalog scanning
 * - XtreamCatalogMapper: Model to catalog item conversion
 * - XtreamCatalogSource: Content loading from transport layer
 *
 * **Dependencies (from transport layer):**
 * - XtreamApiClient: Provided by XtreamTransportModule
 *
 * **Consumers:**
 * - CatalogSync: Processes catalog events and persists to storage
 * - Feature modules: For manual sync triggers
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamCatalogModule {

    @Binds
    @Singleton
    abstract fun bindXtreamCatalogPipeline(
        impl: XtreamCatalogPipelineImpl,
    ): XtreamCatalogPipeline

    @Binds
    @Singleton
    abstract fun bindXtreamCatalogMapper(
        impl: XtreamCatalogMapperImpl,
    ): XtreamCatalogMapper

    @Binds
    @Singleton
    abstract fun bindXtreamCatalogSource(
        impl: DefaultXtreamCatalogSource,
    ): XtreamCatalogSource
}
