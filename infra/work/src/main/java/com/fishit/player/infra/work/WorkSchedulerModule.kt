package com.fishit.player.infra.work

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * RESERVED MODULE: Work Scheduler Infrastructure
 *
 * Purpose: WorkManager scheduling/orchestration (catalog sync, background fetch)
 *
 * TODO: Implement when needed:
 * - [ ] WorkManager scheduler API for catalog sync
 * - [ ] Background sync worker implementations
 * - [ ] Work constraints and policies
 * - [ ] Integration with core:catalog-sync contracts
 *
 * Contract Rules:
 * - Consumes core:catalog-sync contracts (domain/types)
 * - app-v2 only triggers via interfaces
 * - MUST NOT contain WorkManager scheduling in core:catalog-sync
 * - Scheduler implementation lives here, contracts live in core
 *
 * See: docs/v2/FROZEN_MODULE_MANIFEST.md
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkSchedulerModule {
    // TODO: Provide WorkManager configuration when ready
    // TODO: Provide scheduler API for catalog sync triggers
}
