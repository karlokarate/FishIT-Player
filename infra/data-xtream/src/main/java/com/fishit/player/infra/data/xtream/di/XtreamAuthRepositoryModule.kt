package com.fishit.player.infra.data.xtream.di

import com.fishit.player.core.onboarding.domain.XtreamAuthRepository
import com.fishit.player.infra.data.xtream.XtreamAuthRepositoryAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for XtreamAuthRepository binding.
 *
 * Binds the feature-owned XtreamAuthRepository interface to its implementation
 * in the data layer, following Dependency Inversion Principle.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamAuthRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindXtreamAuthRepository(impl: XtreamAuthRepositoryAdapter): XtreamAuthRepository
}
