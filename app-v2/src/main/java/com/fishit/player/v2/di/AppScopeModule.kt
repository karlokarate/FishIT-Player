package com.fishit.player.v2.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {
    const val APP_LIFECYCLE_SCOPE = "AppLifecycleScope"

    @Provides
    @Singleton
    @Named(APP_LIFECYCLE_SCOPE)
    fun provideAppLifecycleScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
