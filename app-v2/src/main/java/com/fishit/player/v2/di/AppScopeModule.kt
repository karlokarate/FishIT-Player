package com.fishit.player.v2.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    const val APP_LIFECYCLE_SCOPE = "AppLifecycleScope"

    @Provides
    @Singleton
    @Named(APP_LIFECYCLE_SCOPE)
    fun provideAppLifecycleScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
