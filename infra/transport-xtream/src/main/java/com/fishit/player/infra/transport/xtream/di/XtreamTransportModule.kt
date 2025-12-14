package com.fishit.player.infra.transport.xtream.di

import com.fishit.player.infra.transport.xtream.DefaultXtreamApiClient
import com.fishit.player.infra.transport.xtream.EncryptedXtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamDiscovery
import com.fishit.player.infra.transport.xtream.bootstrap.XtreamBootstrapper
import com.fishit.player.infra.transport.xtream.bootstrap.XtreamSessionBootstrap
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for Xtream transport layer.
 *
 * Provides XtreamApiClient, XtreamDiscovery, and related transport components.
 */
@Module
@InstallIn(SingletonComponent::class)
object XtreamTransportModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    @Provides
    @Singleton
    fun provideXtreamDiscovery(
        okHttpClient: OkHttpClient,
        json: Json,
    ): XtreamDiscovery = XtreamDiscovery(okHttpClient, json)

    @Provides
    @Singleton
    fun provideXtreamApiClient(
        okHttpClient: OkHttpClient,
        json: Json,
    ): XtreamApiClient = DefaultXtreamApiClient(okHttpClient, json)
}

/**
 * Hilt module for Xtream credentials storage.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamCredentialsModule {
    @Binds
    @Singleton
    abstract fun bindCredentialsStore(impl: EncryptedXtreamCredentialsStore): XtreamCredentialsStore
}

/**
 * Hilt module for Xtream session bootstrap.
 *
 * **Architecture (Phase B2):**
 * - Migrated from app-v2 to infra/transport-xtream
 * - Binds XtreamBootstrapper interface to implementation
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamBootstrapModule {
    @Binds
    @Singleton
    abstract fun bindXtreamBootstrapper(impl: XtreamSessionBootstrap): XtreamBootstrapper
}
