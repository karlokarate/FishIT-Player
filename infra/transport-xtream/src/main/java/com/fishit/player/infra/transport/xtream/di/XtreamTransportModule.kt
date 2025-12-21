package com.fishit.player.infra.transport.xtream.di

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.fishit.player.infra.transport.xtream.BuildConfig
import com.fishit.player.infra.transport.xtream.DefaultXtreamApiClient
import com.fishit.player.infra.transport.xtream.EncryptedXtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamDiscovery
import com.fishit.player.infra.transport.xtream.XtreamHttpHeaders
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import java.net.CookieManager
import java.net.CookiePolicy
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
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(
                JavaNetCookieJar(
                    CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) },
                ),
            ).addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()

                if (request.header("User-Agent") == null) {
                    builder.header("User-Agent", XtreamHttpHeaders.LEGACY_USER_AGENT)
                }

                chain.proceed(builder.build())
            }.apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        ChuckerInterceptor
                            .Builder(context)
                            .collector(ChuckerCollector(context, showNotification = true))
                            .redactHeaders("Authorization", "Cookie")
                            .alwaysReadResponseBody(true)
                            .build(),
                    )
                }
            }.build()

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
