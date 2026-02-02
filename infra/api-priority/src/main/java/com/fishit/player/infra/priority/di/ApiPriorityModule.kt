package com.fishit.player.infra.priority.di

import com.fishit.player.infra.priority.ApiPriorityDispatcher
import com.fishit.player.infra.priority.DefaultApiPriorityDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for API Priority infrastructure.
 *
 * Provides singleton-scoped [ApiPriorityDispatcher] for the entire app.
 * All modules that need priority coordination should inject this interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ApiPriorityModule {
    @Binds
    @Singleton
    abstract fun bindApiPriorityDispatcher(impl: DefaultApiPriorityDispatcher): ApiPriorityDispatcher
}
