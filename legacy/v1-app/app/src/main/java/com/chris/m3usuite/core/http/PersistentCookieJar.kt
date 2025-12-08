package com.chris.m3usuite.core.http

import android.content.Context
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Disk-backed CookieJar that persists cookies across app restarts.
 * - Merges by (name, domain, path)
 * - Removes expired cookies on save/load
 */
class PersistentCookieJar private constructor(
    private val appContext: Context,
) : CookieJar {
    private val prefs by lazy { appContext.getSharedPreferences("cookies_v1", Context.MODE_PRIVATE) }
    private val saving = AtomicBoolean(false)

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        val now = System.currentTimeMillis()
        val all = loadAll().toMutableList()
        // remove expired
        all.removeAll { it.expiresAt < now }
        for (c in cookies) {
            all.removeAll { it.name == c.name && it.domain.equals(c.domain, true) && it.path == c.path }
            all.add(c)
        }
        persist(all)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val host = url.host
        val path = url.encodedPath
        val https = url.isHttps
        val all = loadAll().filter { it.expiresAt >= now }
        return all.filter { c ->
            (!c.secure || https) &&
                (host.equals(c.domain, true) || host.endsWith("." + c.domain, true)) &&
                path.startsWith(c.path)
        }
    }

    private fun loadAll(): List<Cookie> {
        val json = prefs.getString("cookies", null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(json)
            buildList {
                val now = System.currentTimeMillis()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val c =
                        Cookie
                            .Builder()
                            .name(o.getString("name"))
                            .value(o.getString("value"))
                            .domain(o.getString("domain"))
                            .path(o.optString("path", "/"))
                            .apply {
                                if (o.optBoolean("secure", false)) secure()
                                if (o.optBoolean("hostOnly", false)) hostOnlyDomain(o.getString("domain"))
                            }.expiresAt(o.optLong("expiresAt", now + 7L * 24 * 60 * 60 * 1000))
                            .build()
                    add(c)
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun persist(list: List<Cookie>) {
        if (saving.getAndSet(true)) return
        try {
            val arr = org.json.JSONArray()
            for (c in list) {
                val o = org.json.JSONObject()
                o.put("name", c.name)
                o.put("value", c.value)
                o.put("domain", c.domain)
                o.put("path", c.path)
                o.put("secure", c.secure)
                o.put("hostOnly", c.hostOnly)
                o.put("expiresAt", c.expiresAt)
                arr.put(o)
            }
            prefs.edit().putString("cookies", arr.toString()).apply()
        } finally {
            saving.set(false)
        }
    }

    fun importFromWebView(url: String) {
        val cm = CookieManager.getInstance()
        val cookieStr = cm.getCookie(url) ?: return
        val u = url.toHttpUrlOrNull() ?: return
        val parts = cookieStr.split("; ")
        val now = System.currentTimeMillis()
        val https = u.isHttps
        val imported = mutableListOf<Cookie>()
        for (p in parts) {
            val idx = p.indexOf('=')
            if (idx <= 0) continue
            val name = p.substring(0, idx)
            val value = p.substring(idx + 1)
            // Session cookies: give them a short lifetime (3 days) to persist clearance
            val c =
                Cookie
                    .Builder()
                    .name(name)
                    .value(value)
                    .domain(u.host)
                    .path("/")
                    .apply { if (https) secure() }
                    .expiresAt(now + 3L * 24 * 60 * 60 * 1000)
                    .build()
            imported.add(c)
        }
        if (imported.isNotEmpty()) saveFromResponse(u, imported)
    }

    companion object {
        @Volatile private var instance: PersistentCookieJar? = null

        fun get(context: Context): PersistentCookieJar {
            val cur = instance
            if (cur != null) return cur
            val created = PersistentCookieJar(context.applicationContext)
            instance = created
            return created
        }
    }
}

object CookieBridge {
    fun importFromWebView(
        context: Context,
        portalBase: String,
    ) {
        PersistentCookieJar.get(context).importFromWebView(portalBase)
    }
}
