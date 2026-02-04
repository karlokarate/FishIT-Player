package com.fishit.player.infra.http.interceptors

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor for adding custom headers to all requests.
 *
 * @param headers Map of header name to value
 */
class HeaderInterceptor(
    private val headers: Map<String, String>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        headers.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }
        return chain.proceed(requestBuilder.build())
    }
}
