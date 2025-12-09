package com.fishit.player.pipeline.xtream.catalog.di

import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogMapper
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogMapperImpl
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogPipeline
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogPipelineImpl
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
 *
 * **Dependencies (must be provided elsewhere):**
 * - XtreamCatalogSource: Provided by app module or repository layer
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
}
