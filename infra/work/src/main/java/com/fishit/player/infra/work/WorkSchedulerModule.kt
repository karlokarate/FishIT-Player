package com.fishit.player.infra.work

import com.fishit.player.core.sourceactivation.SourceActivationStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Work Scheduler Infrastructure Module
 *
 * Purpose: WorkManager scheduling/orchestration (catalog sync, background fetch)
 * + Source Activation persistence and observation.
 *
 * Provides:
 * - [SourceActivationStore] - SSOT for source activation states (persisted via DataStore)
 * - [SourceActivationObserver] - Bridges activation changes to scheduler
 *
 * Note: CatalogSyncWorkScheduler is provided by app-v2/di/AppWorkModule.
 *
 * See: docs/v2/FROZEN_MODULE_MANIFEST.md
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SourceActivationModule {
    
    @Binds
    @Singleton
    abstract fun bindSourceActivationStore(
        impl: DefaultSourceActivationStore
    ): SourceActivationStore
}
