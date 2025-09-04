package com.chris.m3usuite.core.http

import android.content.Context
import com.chris.m3usuite.prefs.SettingsStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, List<Cookie>>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { store[url.host] = cookies }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
}

object HttpClientFactory {
    fun create(context: Context, settings: SettingsStore): OkHttpClient {
        // Seed headers snapshot once to avoid blocking in interceptor
        val seeded = RequestHeadersProvider.ensureSeededBlocking(settings)

        return OkHttpClient.Builder()
            .cookieJar(MemoryCookieJar())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request()
                val headers = RequestHeadersProvider.snapshot().ifEmpty { seeded }
                val nb = req.newBuilder().header("Accept", "*/*")
                for ((k, v) in headers) nb.header(k, v)
                chain.proceed(nb.build())
            }
            .build()
    }
}
