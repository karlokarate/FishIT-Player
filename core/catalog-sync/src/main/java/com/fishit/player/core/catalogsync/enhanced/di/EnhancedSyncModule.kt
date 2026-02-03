package com.fishit.player.core.catalogsync.enhanced.di

import com.fishit.player.core.catalogsync.enhanced.EnhancedBatchRouter
import com.fishit.player.core.catalogsync.enhanced.XtreamEnhancedSyncOrchestrator
import com.fishit.player.core.catalogsync.enhanced.XtreamEventHandlerRegistry
import com.fishit.player.core.catalogsync.enhanced.handlers.ItemDiscoveredHandler
import com.fishit.player.core.catalogsync.enhanced.handlers.ScanCancelledHandler
import com.fishit.player.core.catalogsync.enhanced.handlers.ScanCompletedHandler
import com.fishit.player.core.catalogsync.enhanced.handlers.ScanErrorHandler
import com.fishit.player.core.catalogsync.enhanced.handlers.ScanProgressHandler
import com.fishit.player.core.catalogsync.enhanced.handlers.SeriesEpisodeHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Enhanced Sync infrastructure.
 *
 * **Provides:**
 * - XtreamEnhancedSyncOrchestrator (main orchestrator)
 * - All event handlers (ItemDiscovered, ScanCompleted, etc.)
 * - XtreamEventHandlerRegistry (handler dispatcher)
 * - EnhancedBatchRouter (batch management)
 *
 * **Architecture:**
 * All components use @Singleton scope and are injected via constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
object EnhancedSyncModule {
    /**
     * Provides the orchestrator (already @Singleton annotated on class)
     */
    // Note: XtreamEnhancedSyncOrchestrator, handlers, router, and registry
    // are all @Singleton classes with @Inject constructors, so Hilt will
    // automatically provide them. This module exists for explicit documentation
    // and potential future provides methods if needed.

    /**
     * If any provides methods are needed in the future, add them here.
     * For now, all dependencies use @Inject constructors.
     */
}
