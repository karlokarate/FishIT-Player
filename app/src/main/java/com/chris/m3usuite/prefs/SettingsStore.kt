package com.chris.m3usuite.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.chris.m3usuite.core.xtream.XtreamCreds
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
    // UI state
    val LIBRARY_TAB_INDEX = intPreferencesKey("library_tab_index")
    // Playback extras
    val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    // Profile/Gate behavior
    val REMEMBER_LAST_PROFILE = booleanPreferencesKey("remember_last_profile")

    // Live filters (persist UI state)
    val LIVE_FILTER_GERMAN = booleanPreferencesKey("live_filter_german")
    val LIVE_FILTER_KIDS = booleanPreferencesKey("live_filter_kids")
    val LIVE_FILTER_PROVIDERS = stringPreferencesKey("live_filter_providers_csv")
    val LIVE_FILTER_GENRES = stringPreferencesKey("live_filter_genres_csv")

    // Profile/PIN
    val CURRENT_PROFILE_ID = longPreferencesKey("current_profile_id")
    val ADULT_PIN_SET = booleanPreferencesKey("adult_pin_set")
    val ADULT_PIN_HASH = stringPreferencesKey("adult_pin_hash")

    // New tracking (persist new items across runs)
    val FIRST_VOD_SNAPSHOT = booleanPreferencesKey("first_vod_snapshot_done")
    val FIRST_SERIES_SNAPSHOT = booleanPreferencesKey("first_series_snapshot_done")
    val FIRST_LIVE_SNAPSHOT = booleanPreferencesKey("first_live_snapshot_done")
    val NEW_VOD_IDS_CSV = stringPreferencesKey("new_vod_ids_csv")
    val NEW_SERIES_IDS_CSV = stringPreferencesKey("new_series_ids_csv")
    val NEW_LIVE_IDS_CSV = stringPreferencesKey("new_live_ids_csv")
    // Episodes snapshot to detect new episodes since last run
    val EPISODE_SNAPSHOT_IDS_CSV = stringPreferencesKey("episode_snapshot_ids_csv")
    val LAST_APP_START_MS = longPreferencesKey("last_app_start_ms")

    // Home selections
    val FAV_LIVE_IDS_CSV = stringPreferencesKey("fav_live_ids_csv")
    // EPG behavior toggles
    val EPG_FAV_USE_XTREAM = booleanPreferencesKey("epg_fav_use_xtream")
    val EPG_FAV_SKIP_XMLTV_IF_X_OK = booleanPreferencesKey("epg_fav_skip_xmltv_if_xtream_ok")

    // Live TV category rows: collapsed set + expansion order (CSV of category keys)
    val LIVE_CAT_COLLAPSED_CSV = stringPreferencesKey("live_cat_collapsed_csv")
    val LIVE_CAT_EXPANDED_ORDER_CSV = stringPreferencesKey("live_cat_expanded_order_csv")
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
    val subtitleFg: Flow<Int> = context.dataStore.data.map { it[Keys.SUB_FG] ?: 0xE6FFFFFF.toInt() } // Weiß, ~90% Deckkraft (Default)
    val subtitleBg: Flow<Int> = context.dataStore.data.map { it[Keys.SUB_BG] ?: 0x66000000 }        // Schwarz, ~40% Deckkraft (Default)
    val subtitleFgOpacityPct: Flow<Int> = context.dataStore.data.map { it[Keys.SUB_FG_OPACITY_PCT] ?: 90 }
    val subtitleBgOpacityPct: Flow<Int> = context.dataStore.data.map { it[Keys.SUB_BG_OPACITY_PCT] ?: 40 }

    val headerCollapsedDefaultInLandscape: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HEADER_COLLAPSED_LAND] ?: true }
    val headerCollapsed: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HEADER_COLLAPSED] ?: false }
    val rotationLocked: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ROTATION_LOCKED] ?: false }
    val libraryTabIndex: Flow<Int> =
        context.dataStore.data.map { it[Keys.LIBRARY_TAB_INDEX] ?: 0 }
    val autoplayNext: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTOPLAY_NEXT] ?: false }
    val hapticsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HAPTICS_ENABLED] ?: false }

    val liveFilterGerman: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIVE_FILTER_GERMAN] ?: false }
    val liveFilterKids: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIVE_FILTER_KIDS] ?: false }
    val liveFilterProvidersCsv: Flow<String> = context.dataStore.data.map { it[Keys.LIVE_FILTER_PROVIDERS].orEmpty() }
    val liveFilterGenresCsv: Flow<String> = context.dataStore.data.map { it[Keys.LIVE_FILTER_GENRES].orEmpty() }

    val currentProfileId: Flow<Long> =
        context.dataStore.data.map { it[Keys.CURRENT_PROFILE_ID] ?: -1L }
    val adultPinSet: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ADULT_PIN_SET] ?: false }
    val adultPinHash: Flow<String> =
        context.dataStore.data.map { it[Keys.ADULT_PIN_HASH].orEmpty() }
    val rememberLastProfile: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.REMEMBER_LAST_PROFILE] ?: false }

    // New tracking flows
    val firstVodSnapshot: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_VOD_SNAPSHOT] ?: false }
    val firstSeriesSnapshot: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_SERIES_SNAPSHOT] ?: false }
    val firstLiveSnapshot: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_LIVE_SNAPSHOT] ?: false }
    val newVodIdsCsv: Flow<String> = context.dataStore.data.map { it[Keys.NEW_VOD_IDS_CSV].orEmpty() }
    val newSeriesIdsCsv: Flow<String> = context.dataStore.data.map { it[Keys.NEW_SERIES_IDS_CSV].orEmpty() }
    val newLiveIdsCsv: Flow<String> = context.dataStore.data.map { it[Keys.NEW_LIVE_IDS_CSV].orEmpty() }
    val episodeSnapshotIdsCsv: Flow<String> = context.dataStore.data.map { it[Keys.EPISODE_SNAPSHOT_IDS_CSV].orEmpty() }
    val lastAppStartMs: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_APP_START_MS] ?: 0L }
    val favoriteLiveIdsCsv: Flow<String> = context.dataStore.data.map { it[Keys.FAV_LIVE_IDS_CSV].orEmpty() }
    val epgFavUseXtream: Flow<Boolean> = context.dataStore.data.map { it[Keys.EPG_FAV_USE_XTREAM] ?: true }
    val epgFavSkipXmltvIfXtreamOk: Flow<Boolean> = context.dataStore.data.map { it[Keys.EPG_FAV_SKIP_XMLTV_IF_X_OK] ?: false }

    // Live category rows state
    val liveCatCollapsedCsv: Flow<String> = context.dataStore.data.map { it[Keys.LIVE_CAT_COLLAPSED_CSV].orEmpty() }
    val liveCatExpandedOrderCsv: Flow<String> = context.dataStore.data.map { it[Keys.LIVE_CAT_EXPANDED_ORDER_CSV].orEmpty() }

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
    suspend fun setLibraryTabIndex(value: Int) {
        context.dataStore.edit { it[Keys.LIBRARY_TAB_INDEX] = value }
    }
    suspend fun setAutoplayNext(value: Boolean) { context.dataStore.edit { it[Keys.AUTOPLAY_NEXT] = value } }
    suspend fun setHapticsEnabled(value: Boolean) { context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = value } }
    suspend fun setLiveFilterGerman(value: Boolean) { context.dataStore.edit { it[Keys.LIVE_FILTER_GERMAN] = value } }
    suspend fun setLiveFilterKids(value: Boolean) { context.dataStore.edit { it[Keys.LIVE_FILTER_KIDS] = value } }
    suspend fun setLiveFilterProvidersCsv(value: String) { context.dataStore.edit { it[Keys.LIVE_FILTER_PROVIDERS] = value } }
    suspend fun setLiveFilterGenresCsv(value: String) { context.dataStore.edit { it[Keys.LIVE_FILTER_GENRES] = value } }
    // Explicit setters for frequently used settings
    suspend fun setM3uUrl(value: String) { context.dataStore.edit { it[Keys.M3U_URL] = value } }
    suspend fun setEpgUrl(value: String) { context.dataStore.edit { it[Keys.EPG_URL] = value } }
    suspend fun setXtHost(value: String) { context.dataStore.edit { it[Keys.XT_HOST] = value } }
    suspend fun setXtPort(value: Int) { context.dataStore.edit { it[Keys.XT_PORT] = value } }
    suspend fun setXtUser(value: String) { context.dataStore.edit { it[Keys.XT_USER] = value } }
    suspend fun setXtPass(value: String) { context.dataStore.edit { it[Keys.XT_PASS] = value } }
    suspend fun setXtOutput(value: String) { context.dataStore.edit { it[Keys.XT_OUTPUT] = value } }
    // Helper to set all Xtream creds at once (no removal of existing API)
    suspend fun setXtream(creds: XtreamCreds) {
        setXtHost(creds.host)
        setXtPort(creds.port)
        setXtUser(creds.username)
        setXtPass(creds.password)
        setXtOutput(creds.output)
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
    suspend fun setRememberLastProfile(value: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_LAST_PROFILE] = value }
    }

    // New tracking setters
    suspend fun setFirstVodSnapshot(value: Boolean) { context.dataStore.edit { it[Keys.FIRST_VOD_SNAPSHOT] = value } }
    suspend fun setFirstSeriesSnapshot(value: Boolean) { context.dataStore.edit { it[Keys.FIRST_SERIES_SNAPSHOT] = value } }
    suspend fun setFirstLiveSnapshot(value: Boolean) { context.dataStore.edit { it[Keys.FIRST_LIVE_SNAPSHOT] = value } }
    suspend fun setNewVodIdsCsv(csv: String) { context.dataStore.edit { it[Keys.NEW_VOD_IDS_CSV] = csv } }
    suspend fun setNewSeriesIdsCsv(csv: String) { context.dataStore.edit { it[Keys.NEW_SERIES_IDS_CSV] = csv } }
    suspend fun setNewLiveIdsCsv(csv: String) { context.dataStore.edit { it[Keys.NEW_LIVE_IDS_CSV] = csv } }
    suspend fun setEpisodeSnapshotIdsCsv(csv: String) { context.dataStore.edit { it[Keys.EPISODE_SNAPSHOT_IDS_CSV] = csv } }
    suspend fun setLastAppStartMs(value: Long) { context.dataStore.edit { it[Keys.LAST_APP_START_MS] = value } }
    suspend fun setFavoriteLiveIdsCsv(csv: String) { context.dataStore.edit { it[Keys.FAV_LIVE_IDS_CSV] = csv } }
    suspend fun setEpgFavUseXtream(value: Boolean) { context.dataStore.edit { it[Keys.EPG_FAV_USE_XTREAM] = value } }
    suspend fun setEpgFavSkipXmltvIfXtreamOk(value: Boolean) { context.dataStore.edit { it[Keys.EPG_FAV_SKIP_XMLTV_IF_X_OK] = value } }

    // -------- Optional: direktes Abfragen --------
    suspend fun getString(key: Preferences.Key<String>, default: String = ""): String =
        context.dataStore.data.map { it[key] ?: default }.first()

    suspend fun getInt(key: Preferences.Key<Int>, default: Int = 0): Int =
        context.dataStore.data.map { it[key] ?: default }.first()

    suspend fun getBool(key: Preferences.Key<Boolean>, default: Boolean = false): Boolean =
        context.dataStore.data.map { it[key] ?: default }.first()

    suspend fun getFloat(key: Preferences.Key<Float>, default: Float = 0.06f): Float =
        context.dataStore.data.map { it[key] ?: default }.first()

    // Raw dump/restore helpers to avoid multiple DataStore instances elsewhere
    suspend fun dumpAll(): Map<String, String> =
        context.dataStore.data.map { prefs ->
            prefs.asMap().mapKeys { it.key.name }.mapValues { (_, v) -> v?.toString() ?: "" }
        }.first()

    suspend fun restoreAll(values: Map<String, String>, replace: Boolean) {
        context.dataStore.edit { prefs ->
            if (replace) prefs.clear()
            for ((name, s) in values) {
                when {
                    s.equals("true", true) || s.equals("false", true) -> prefs[booleanPreferencesKey(name)] = s.equals("true", true)
                    s.toLongOrNull() != null -> {
                        val lv = s.toLong()
                        if (lv in Int.MIN_VALUE..Int.MAX_VALUE) prefs[intPreferencesKey(name)] = lv.toInt() else prefs[longPreferencesKey(name)] = lv
                    }
                    s.toFloatOrNull() != null -> prefs[floatPreferencesKey(name)] = s.toFloat()
                    else -> prefs[stringPreferencesKey(name)] = s
                }
            }
        }
    }

    // Optional convenience
    suspend fun hasXtream(): Boolean {
        val host = xtHost.first()
        val user = xtUser.first()
        val pass = xtPass.first()
        return host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
    }

    // Live category rows state setters
    suspend fun setLiveCatCollapsedCsv(value: String) { context.dataStore.edit { it[Keys.LIVE_CAT_COLLAPSED_CSV] = value } }
    suspend fun setLiveCatExpandedOrderCsv(value: String) { context.dataStore.edit { it[Keys.LIVE_CAT_EXPANDED_ORDER_CSV] = value } }
}
