package com.chris.m3usuite.core.http

import android.content.Context
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        return OkHttpClient.Builder()
            .cookieJar(MemoryCookieJar())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request()
                val ua = runBlocking { settings.userAgent.first() }
                val ref = runBlocking { settings.referer.first() }
                val extrasJson = runBlocking { settings.extraHeadersJson.first() }
                val extras = runCatching {
                    kotlinx.serialization.json.Json.decodeFromString<Map<String,String>>(extrasJson)
                }.getOrNull() ?: emptyMap()

                val nb = req.newBuilder()
                    .header("User-Agent", ua)
                    .header("Accept", "*/*")
                if (ref.isNotBlank()) nb.header("Referer", ref)
                for ((k, v) in extras) nb.header(k, v)
                chain.proceed(nb.build())
            }
            .build()
    }
}
