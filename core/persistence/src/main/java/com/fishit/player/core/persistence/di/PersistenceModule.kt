package com.fishit.player.core.persistence.di

import com.fishit.player.core.persistence.inspector.DefaultObxDatabaseInspector
import com.fishit.player.core.persistence.inspector.ObxDatabaseInspector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for persistence-layer bindings.
 *
 * All legacy Obx repository bindings have been removed (P3 cleanup, 2025-01).
 * Active repository bindings live in NxDataModule.kt (infra/data-nx).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceModule {
    /** Debug / power-user database introspection tooling. */
    @Binds
    @Singleton
    abstract fun bindObxDatabaseInspector(impl: DefaultObxDatabaseInspector): ObxDatabaseInspector
}
