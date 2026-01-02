package com.fishit.player.infra.data.detail.di

import com.fishit.player.core.detail.domain.DetailEnrichmentService
import com.fishit.player.infra.data.detail.DetailEnrichmentServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DetailDataModule {

    @Binds
    @Singleton
    abstract fun bindDetailEnrichmentService(impl: DetailEnrichmentServiceImpl): DetailEnrichmentService
}
