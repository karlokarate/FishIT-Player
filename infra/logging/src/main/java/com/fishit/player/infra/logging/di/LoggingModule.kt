package com.fishit.player.infra.logging.di

import com.fishit.player.infra.logging.DefaultLogBufferProvider
import com.fishit.player.infra.logging.LogBufferProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for logging infrastructure.
 *
 * Provides:
 * - [LogBufferProvider] for accessing in-memory log buffer
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {

    @Binds
    @Singleton
    abstract fun bindLogBufferProvider(
        impl: DefaultLogBufferProvider
    ): LogBufferProvider
}
