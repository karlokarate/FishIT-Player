package com.chris.m3usuite.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/**
 * Zentraler Settings-Store der App.
 * Erweiterungen: Player-Modus (ask/internal/external) + Subtitle-Stil + Landscape-Header-Default.
 */
object Keys {
    // Basis / Netzwerk
    val M3U_URL = stringPreferencesKey("m3u_url")
    val EPG_URL = stringPreferencesKey("epg_url")
    val USER_AGENT = stringPreferencesKey("user_agent")
    val REFERER = stringPreferencesKey("referer")
    val EXTRA_HEADERS = stringPreferencesKey("extra_headers_json")

    // Player-Auswahl (externes Paket optional)
    val PREF_PLAYER_PACKAGE = stringPreferencesKey("preferred_player_pkg")
    val PLAYER_MODE = stringPreferencesKey("player_mode") // "ask" | "internal" | "external"

    // Xtream (aus get.php abgeleitet)
    val XT_HOST = stringPreferencesKey("xt_host")
    val XT_PORT = intPreferencesKey("xt_port")
    val XT_USER = stringPreferencesKey("xt_user")
    val XT_PASS = stringPreferencesKey("xt_pass")
    val XT_OUTPUT = stringPreferencesKey("xt_output")

    // Untertitel (Media3)
    val SUB_SCALE = floatPreferencesKey("sub_scale")         // 0.04..0.12 (FractionalTextSize)
    val SUB_FG = intPreferencesKey("sub_fg")                 // ARGB
    val SUB_BG = intPreferencesKey("sub_bg")                 // ARGB (halb transparent empfohlen)
    val SUB_FG_OPACITY_PCT = intPreferencesKey("sub_fg_opacity_pct") // 0..100
    val SUB_BG_OPACITY_PCT = intPreferencesKey("sub_bg_opacity_pct") // 0..100

    // UI-Verhalten
    val HEADER_COLLAPSED_LAND = booleanPreferencesKey("header_collapsed_land") // Default in Landscape
    val HEADER_COLLAPSED = booleanPreferencesKey("header_collapsed") // globaler Zustand
    val ROTATION_LOCKED = booleanPreferencesKey("rotation_locked")

    // Profile/PIN
    val CURRENT_PROFILE_ID = longPreferencesKey("current_profile_id")
    val ADULT_PIN_SET = booleanPreferencesKey("adult_pin_set")
    val ADULT_PIN_HASH = stringPreferencesKey("adult_pin_hash")
}

class SettingsStore(private val context: Context) {

    // -------- Flows (reaktiv) --------
    val m3uUrl: Flow<String> = context.dataStore.data.map { it[Keys.M3U_URL].orEmpty() }
    val epgUrl: Flow<String> = context.dataStore.data.map { it[Keys.EPG_URL].orEmpty() }
    val userAgent: Flow<String> = context.dataStore.data.map { it[Keys.USER_AGENT] ?: "IBOPlayer/1.4 (Android)" }
    val referer: Flow<String> = context.dataStore.data.map { it[Keys.REFERER].orEmpty() }
    val extraHeadersJson: Flow<String> = context.dataStore.data.map { it[Keys.EXTRA_HEADERS].orEmpty() }

    val preferredPlayerPkg: Flow<String> = context.dataStore.data.map { it[Keys.PREF_PLAYER_PACKAGE].orEmpty() }
    val playerMode: Flow<String> = context.dataStore.data.map { it[Keys.PLAYER_MODE] ?: "ask" } // ask/internal/external

    val xtHost: Flow<String> = context.dataStore.data.map { it[Keys.XT_HOST].orEmpty() }
    val xtPort: Flow<Int> = context.dataStore.data.map { it[Keys.XT_PORT] ?: 80 }
    val xtUser: Flow<String> = context.dataStore.data.map { it[Keys.XT_USER].orEmpty() }
    val xtPass: Flow<String> = context.dataStore.data.map { it[Keys.XT_PASS].orEmpty() }
    val xtOutput: Flow<String> = context.dataStore.data.map { it[Keys.XT_OUTPUT] ?: "m3u8" }

    val subtitleScale: Flow<Float> = context.dataStore.data.map { it[Keys.SUB_SCALE] ?: 0.06f }
    val subtitleFg: Flow<Int> = context.dataStore.data.map { it[Keys.SUB_FG] ?: 0xF2FFFFFF.toInt() } // fast Weiß
    val subtitleBg: Flow<Int> = context.dataStore.data.map { it[Keys.SUB_BG] ?: 0x66000000 }        // halbtransparent Schwarz
    val subtitleFgOpacityPct: Flow<Int> = context.dataStore.data.map { it[Keys.SUB_FG_OPACITY_PCT] ?: 90 }
    val subtitleBgOpacityPct: Flow<Int> = context.dataStore.data.map { it[Keys.SUB_BG_OPACITY_PCT] ?: 40 }

    val headerCollapsedDefaultInLandscape: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HEADER_COLLAPSED_LAND] ?: true }
    val headerCollapsed: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HEADER_COLLAPSED] ?: false }
    val rotationLocked: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ROTATION_LOCKED] ?: false }

    val currentProfileId: Flow<Long> =
        context.dataStore.data.map { it[Keys.CURRENT_PROFILE_ID] ?: -1L }
    val adultPinSet: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ADULT_PIN_SET] ?: false }
    val adultPinHash: Flow<String> =
        context.dataStore.data.map { it[Keys.ADULT_PIN_HASH].orEmpty() }

    // -------- Setzen --------
    suspend fun set(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setBool(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setFloat(key: Preferences.Key<Float>, value: Float) {
        context.dataStore.edit { it[key] = value }
    }

    // Komfortfunktionen
    suspend fun setPlayerMode(mode: String) { // "ask"|"internal"|"external"
        context.dataStore.edit { it[Keys.PLAYER_MODE] = mode }
    }

    suspend fun setPreferredPlayerPackage(pkg: String) {
        context.dataStore.edit { it[Keys.PREF_PLAYER_PACKAGE] = pkg }
    }

    suspend fun setSubtitleStyle(scale: Float, fg: Int, bg: Int) {
        context.dataStore.edit {
            it[Keys.SUB_SCALE] = scale
            it[Keys.SUB_FG] = fg
            it[Keys.SUB_BG] = bg
        }
    }

    suspend fun setSubtitleFgOpacityPct(value: Int) {
        context.dataStore.edit { it[Keys.SUB_FG_OPACITY_PCT] = value.coerceIn(0, 100) }
    }
    suspend fun setSubtitleBgOpacityPct(value: Int) {
        context.dataStore.edit { it[Keys.SUB_BG_OPACITY_PCT] = value.coerceIn(0, 100) }
    }
    suspend fun setHeaderCollapsed(value: Boolean) {
        context.dataStore.edit { it[Keys.HEADER_COLLAPSED] = value }
    }
    suspend fun setRotationLocked(value: Boolean) {
        context.dataStore.edit { it[Keys.ROTATION_LOCKED] = value }
    }
    suspend fun setCurrentProfileId(id: Long) {
        context.dataStore.edit { it[Keys.CURRENT_PROFILE_ID] = id }
    }
    suspend fun setAdultPinSet(value: Boolean) {
        context.dataStore.edit { it[Keys.ADULT_PIN_SET] = value }
    }
    suspend fun setAdultPinHash(hash: String) {
        context.dataStore.edit { it[Keys.ADULT_PIN_HASH] = hash }
    }

    // -------- Optional: direktes Abfragen --------
    suspend fun getString(key: Preferences.Key<String>, default: String = ""): String =
        context.dataStore.data.map { it[key] ?: default }.first()

    suspend fun getInt(key: Preferences.Key<Int>, default: Int = 0): Int =
        context.dataStore.data.map { it[key] ?: default }.first()

    suspend fun getBool(key: Preferences.Key<Boolean>, default: Boolean = false): Boolean =
        context.dataStore.data.map { it[key] ?: default }.first()

    suspend fun getFloat(key: Preferences.Key<Float>, default: Float = 0.06f): Float =
        context.dataStore.data.map { it[key] ?: default }.first()
}
