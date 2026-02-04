package com.fishit.player.infra.http.di

import com.fishit.player.infra.http.DefaultHttpClient
import com.fishit.player.infra.http.HttpClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing HTTP client dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HttpClientModule {
    @Binds
    @Singleton
    abstract fun bindHttpClient(impl: DefaultHttpClient): HttpClient

    companion object {
        /**
         * Provides base OkHttpClient for dependency injection.
         *
         * This is a minimal HTTP client without interceptors.
         * The DefaultHttpClient implementation adds caching, rate limiting, etc.
         */
        @Provides
        @Singleton
        fun provideBaseOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
