package com.chris.m3usuite.tg

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
    fun obxEnabled(): Boolean = cached ?: false

    /** Mirror-Only (Reflect-Flow erzwingen) */
    fun mirrorOnly(): Boolean = !obxEnabled()
}
