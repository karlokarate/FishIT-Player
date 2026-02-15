package com.fishit.player.playback.xtream

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.fishit.player.infra.transport.xtream.di.XtreamHttpClient
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [XtreamDataSourceFactoryProvider].
 *
 * Creates [OkHttpDataSource.Factory] instances configured for Xtream playback.
 * Uses the shared [XtreamHttpClient] OkHttpClient (SSOT for all Xtream HTTP),
 * with `followSslRedirects=true` override for CDN redirects (HTTP→HTTPS).
 *
 * **SSOT:** All Xtream HTTP goes through the same OkHttpClient configured in
 * XtreamTransportModule (headers, Chucker, parallelism, timeouts).
 */
@Singleton
class DefaultXtreamDataSourceFactoryProvider
    @Inject
    constructor(
        @XtreamHttpClient private val xtreamOkHttpClient: OkHttpClient,
    ) : XtreamDataSourceFactoryProvider {
        /**
         * Playback client derived from the shared Xtream client.
         * Overrides `followSslRedirects=true` because CDNs commonly redirect HTTP→HTTPS.
         * Shares the connection pool with the transport client.
         */
        private val playbackClient: OkHttpClient by lazy {
            xtreamOkHttpClient
                .newBuilder()
                .followSslRedirects(true)
                .build()
        }

        override fun create(headers: Map<String, String>): DataSource.Factory =
            OkHttpDataSource
                .Factory(playbackClient)
                .setDefaultRequestProperties(headers)
    }
