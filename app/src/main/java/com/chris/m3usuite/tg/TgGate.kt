package com.chris.m3usuite.tg

import com.chris.m3usuite.BuildConfig

/**
 * Zentraler Gate-Helper für Telegram-OBX.
 * Aktuell: hart auf BuildConfig-Default (false) -> Mirror-Only.
 * Optional kannst du später ein DataStore-Flow reinreichen.
 */
object TgGate {
    @Volatile private var cached: Boolean? = null

    /** Optional: spätere Initialisierung mit DataStore-Flow */
    fun set(value: Boolean) { cached = value }

    /** OBX/Indexing aktiviert? */
    fun obxEnabled(): Boolean = cached ?: BuildConfig.TG_OBX_ENABLED_DEFAULT

    /** Mirror-Only (Reflect-Flow erzwingen) */
    fun mirrorOnly(): Boolean = !obxEnabled()
}
