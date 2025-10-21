package com.chris.m3usuite.prefs

import android.content.Context
import com.chris.m3usuite.BuildConfig
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
    // Verified flags to prevent later auto-overwrite after successful fallback
    val XT_PORT_VERIFIED = booleanPreferencesKey("xt_port_verified")
    val XT_OUTPUT_VERIFIED = booleanPreferencesKey("xt_output_verified")

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

        // Seeding: Prefix-Whitelist (z. B. DE, US, UK, VOD)
        val SEED_PREFIXES_GLOBAL_CSV = stringPreferencesKey("seed_prefixes_global_csv")

    // Live TV category rows: collapsed set + expansion order (CSV of category keys)
    val LIVE_CAT_COLLAPSED_CSV = stringPreferencesKey("live_cat_collapsed_csv")
    val LIVE_CAT_EXPANDED_ORDER_CSV = stringPreferencesKey("live_cat_expanded_order_csv")

    // VOD category rows: collapsed set + expansion order
    val VOD_CAT_COLLAPSED_CSV = stringPreferencesKey("vod_cat_collapsed_csv")
    val VOD_CAT_EXPANDED_ORDER_CSV = stringPreferencesKey("vod_cat_expanded_order_csv")

    // Series category rows: collapsed set + expansion order
    val SERIES_CAT_COLLAPSED_CSV = stringPreferencesKey("series_cat_collapsed_csv")
    val SERIES_CAT_EXPANDED_ORDER_CSV = stringPreferencesKey("series_cat_expanded_order_csv")

    // Telegram (global feature flag + options)
    val TG_ENABLED = booleanPreferencesKey("tg_enabled")
    val TG_SELECTED_CHATS_CSV = stringPreferencesKey("tg_selected_chats_csv")
    val TG_CACHE_LIMIT_GB = intPreferencesKey("tg_cache_limit_gb")
    val TG_PREFER_IPV6 = booleanPreferencesKey("tg_prefer_ipv6")
    val TG_STAY_ONLINE = booleanPreferencesKey("tg_stay_online")
    val TG_PROXY_TYPE = stringPreferencesKey("tg_proxy_type")
    val TG_PROXY_HOST = stringPreferencesKey("tg_proxy_host")
    val TG_PROXY_PORT = intPreferencesKey("tg_proxy_port")
    val TG_PROXY_USERNAME = stringPreferencesKey("tg_proxy_username")
    val TG_PROXY_PASSWORD = stringPreferencesKey("tg_proxy_password")
    val TG_PROXY_SECRET = stringPreferencesKey("tg_proxy_secret")
    val TG_PROXY_ENABLED = booleanPreferencesKey("tg_proxy_enabled")
    val TG_LOG_VERBOSITY = intPreferencesKey("tg_log_verbosity")
    val LOG_DIR_TREE_URI = stringPreferencesKey("log_dir_tree_uri")
    val TG_LOG_OVERLAY = booleanPreferencesKey("tg_log_overlay")
    val TG_PREFETCH_WINDOW_MB = intPreferencesKey("tg_prefetch_window_mb")
    val TG_SEEK_BOOST_ENABLED = booleanPreferencesKey("tg_seek_boost_enabled")
    val TG_MAX_PARALLEL_DOWNLOADS = intPreferencesKey("tg_max_parallel_downloads")
    val TG_STORAGE_OPTIMIZER = booleanPreferencesKey("tg_storage_optimizer")
    val TG_IGNORE_FILE_NAMES = booleanPreferencesKey("tg_ignore_file_names")
    val TG_AUTO_WIFI_ENABLED = booleanPreferencesKey("tg_auto_wifi_enabled")
    val TG_AUTO_WIFI_PRELOAD_LARGE = booleanPreferencesKey("tg_auto_wifi_preload_large")
    val TG_AUTO_WIFI_PRELOAD_NEXT_AUDIO = booleanPreferencesKey("tg_auto_wifi_preload_next_audio")
    val TG_AUTO_WIFI_PRELOAD_STORIES = booleanPreferencesKey("tg_auto_wifi_preload_stories")
    val TG_AUTO_WIFI_LESS_DATA_CALLS = booleanPreferencesKey("tg_auto_wifi_less_data_calls")
    val TG_AUTO_MOBILE_ENABLED = booleanPreferencesKey("tg_auto_mobile_enabled")
    val TG_AUTO_MOBILE_PRELOAD_LARGE = booleanPreferencesKey("tg_auto_mobile_preload_large")
    val TG_AUTO_MOBILE_PRELOAD_NEXT_AUDIO = booleanPreferencesKey("tg_auto_mobile_preload_next_audio")
    val TG_AUTO_MOBILE_PRELOAD_STORIES = booleanPreferencesKey("tg_auto_mobile_preload_stories")
    val TG_AUTO_MOBILE_LESS_DATA_CALLS = booleanPreferencesKey("tg_auto_mobile_less_data_calls")
    val TG_AUTO_ROAM_ENABLED = booleanPreferencesKey("tg_auto_roam_enabled")
    val TG_AUTO_ROAM_PRELOAD_LARGE = booleanPreferencesKey("tg_auto_roam_preload_large")
    val TG_AUTO_ROAM_PRELOAD_NEXT_AUDIO = booleanPreferencesKey("tg_auto_roam_preload_next_audio")
    val TG_AUTO_ROAM_PRELOAD_STORIES = booleanPreferencesKey("tg_auto_roam_preload_stories")
    val TG_AUTO_ROAM_LESS_DATA_CALLS = booleanPreferencesKey("tg_auto_roam_less_data_calls")
    // Telegram sync mapping (separate selections for VOD and Series)
    val TG_SELECTED_VOD_CHATS_CSV = stringPreferencesKey("tg_selected_vod_chats_csv")
    val TG_SELECTED_SERIES_CHATS_CSV = stringPreferencesKey("tg_selected_series_chats_csv")
    // Telegram API overrides (optional)
    val TG_API_ID = intPreferencesKey("tg_api_id")
    val TG_API_HASH = stringPreferencesKey("tg_api_hash")

    // Debug/Logging
    val HTTP_LOG_ENABLED = booleanPreferencesKey("http_log_enabled")
    val GLOBAL_DEBUG_ENABLED = booleanPreferencesKey("global_debug_enabled")
    // Feature gates
    val ROOM_ENABLED = booleanPreferencesKey("room_enabled")
    // Global gate to allow M3U/Xtream workers & related API calls
    val M3U_WORKERS_ENABLED = booleanPreferencesKey("m3u_workers_enabled")
    // Global toggle: show "For Adults" category
    val SHOW_ADULTS = booleanPreferencesKey("show_adults")

    // Library sort toggles
    val LIB_VOD_SORT_NEWEST = booleanPreferencesKey("lib_vod_sort_newest")
    val LIB_SERIES_SORT_NEWEST = booleanPreferencesKey("lib_series_sort_newest")
    // Library grouping: per-tab toggle for grouping by Genres (true) vs Providers (false)
    val LIB_GROUP_BY_GENRE_LIVE = booleanPreferencesKey("lib_group_by_genre_live")
    val LIB_GROUP_BY_GENRE_VOD = booleanPreferencesKey("lib_group_by_genre_vod")
    val LIB_GROUP_BY_GENRE_SERIES = booleanPreferencesKey("lib_group_by_genre_series")

    // Import diagnostics
    val LAST_IMPORT_AT_MS = longPreferencesKey("last_import_at_ms")
    val LAST_SEED_LIVE = intPreferencesKey("last_seed_live")
    val LAST_SEED_VOD = intPreferencesKey("last_seed_vod")
    val LAST_SEED_SERIES = intPreferencesKey("last_seed_series")
    val LAST_DELTA_LIVE = intPreferencesKey("last_delta_live")
    val LAST_DELTA_VOD = intPreferencesKey("last_delta_vod")
    val LAST_DELTA_SERIES = intPreferencesKey("last_delta_series")
}

class SettingsStore(private val context: Context) {

    // -------- Flows (reaktiv) --------
    val m3uUrl: Flow<String> = context.dataStore.data.map { it[Keys.M3U_URL].orEmpty() }
    val epgUrl: Flow<String> = context.dataStore.data.map { it[Keys.EPG_URL].orEmpty() }
    val userAgent: Flow<String> = context.dataStore.data.map { it[Keys.USER_AGENT] ?: "IBOPlayer/1.4 (Android)" }
    val referer: Flow<String> = context.dataStore.data.map { it[Keys.REFERER].orEmpty() }
    val extraHeadersJson: Flow<String> = context.dataStore.data.map { it[Keys.EXTRA_HEADERS].orEmpty() }

    val preferredPlayerPkg: Flow<String> = context.dataStore.data.map { it[Keys.PREF_PLAYER_PACKAGE].orEmpty() }
    val playerMode: Flow<String> = context.dataStore.data.map { it[Keys.PLAYER_MODE] ?: "internal" } // ask/internal/external

    val xtHost: Flow<String> = context.dataStore.data.map { it[Keys.XT_HOST].orEmpty() }
    val xtPort: Flow<Int> = context.dataStore.data.map { it[Keys.XT_PORT] ?: 80 }
    val xtUser: Flow<String> = context.dataStore.data.map { it[Keys.XT_USER].orEmpty() }
    val xtPass: Flow<String> = context.dataStore.data.map { Crypto.decrypt(it[Keys.XT_PASS].orEmpty()) }
    val xtOutput: Flow<String> = context.dataStore.data.map { it[Keys.XT_OUTPUT] ?: "m3u8" }
    val xtPortVerified: Flow<Boolean> = context.dataStore.data.map { it[Keys.XT_PORT_VERIFIED] ?: false }
    val xtOutputVerified: Flow<Boolean> = context.dataStore.data.map { it[Keys.XT_OUTPUT_VERIFIED] ?: false }

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

    // Seeding prefixes (global). Default: DE,US,UK,VOD
    val seedPrefixesCsv: Flow<String> = context.dataStore.data.map { it[Keys.SEED_PREFIXES_GLOBAL_CSV].orEmpty() }
    suspend fun seedPrefixesSet(): Set<String> {
        val csv = seedPrefixesCsv.first().trim()
        val def = setOf("DE", "US", "UK", "VOD", "FOR")
        if (csv.isBlank()) return def
        return csv.split(',').mapNotNull { it.trim().uppercase().takeIf { s -> s.isNotBlank() } }.toSet().ifEmpty { def }
    }

    // Feature gates
    val roomEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.ROOM_ENABLED] ?: false }
    // Global M3U/Xtream worker + API gate (default ON)
    val m3uWorkersEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.M3U_WORKERS_ENABLED] ?: true }
    val showAdults: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_ADULTS] ?: false }
    // Library sort toggles
    val libVodSortNewest: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIB_VOD_SORT_NEWEST] ?: true }
    val libSeriesSortNewest: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIB_SERIES_SORT_NEWEST] ?: true }
    val libGroupByGenreLive: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIB_GROUP_BY_GENRE_LIVE] ?: false }
    val libGroupByGenreVod: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIB_GROUP_BY_GENRE_VOD] ?: false }
    val libGroupByGenreSeries: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIB_GROUP_BY_GENRE_SERIES] ?: false }
    // Import diagnostics flows
    val lastImportAtMs: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_IMPORT_AT_MS] ?: 0L }
    val lastSeedLive: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_SEED_LIVE] ?: 0 }
    val lastSeedVod: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_SEED_VOD] ?: 0 }
    val lastSeedSeries: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_SEED_SERIES] ?: 0 }
    val lastDeltaLive: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_DELTA_LIVE] ?: 0 }
    val lastDeltaVod: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_DELTA_VOD] ?: 0 }
    val lastDeltaSeries: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_DELTA_SERIES] ?: 0 }

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

    // VOD/Series category rows state
    val vodCatCollapsedCsv: Flow<String> = context.dataStore.data.map { it[Keys.VOD_CAT_COLLAPSED_CSV].orEmpty() }
    val vodCatExpandedOrderCsv: Flow<String> = context.dataStore.data.map { it[Keys.VOD_CAT_EXPANDED_ORDER_CSV].orEmpty() }
    val seriesCatCollapsedCsv: Flow<String> = context.dataStore.data.map { it[Keys.SERIES_CAT_COLLAPSED_CSV].orEmpty() }
    val seriesCatExpandedOrderCsv: Flow<String> = context.dataStore.data.map { it[Keys.SERIES_CAT_EXPANDED_ORDER_CSV].orEmpty() }

    // Telegram (default off)
    val tgEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_ENABLED] ?: false }
    // Für neue Logik: ein gemeinsames CSV
    val tgSelectedChatsCsv: Flow<String> = context.dataStore.data.map { it[Keys.TG_SELECTED_CHATS_CSV].orEmpty() }
    // Legacy-Reader (falls Altdaten existieren):
    val tgSelectedVodChatsCsv: Flow<String> = context.dataStore.data.map { it[Keys.TG_SELECTED_VOD_CHATS_CSV].orEmpty() }
    val tgSelectedSeriesChatsCsv: Flow<String> = context.dataStore.data.map { it[Keys.TG_SELECTED_SERIES_CHATS_CSV].orEmpty() }
    val tgCacheLimitGb: Flow<Int> = context.dataStore.data.map { it[Keys.TG_CACHE_LIMIT_GB] ?: 2 }
    val tgPreferIpv6: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_PREFER_IPV6] ?: true }
    val tgStayOnline: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_STAY_ONLINE] ?: true }
    val tgProxyType: Flow<String> = context.dataStore.data.map { it[Keys.TG_PROXY_TYPE].orEmpty() }
    val tgProxyHost: Flow<String> = context.dataStore.data.map { it[Keys.TG_PROXY_HOST].orEmpty() }
    val tgProxyPort: Flow<Int> = context.dataStore.data.map { it[Keys.TG_PROXY_PORT] ?: 0 }
    val tgProxyUsername: Flow<String> = context.dataStore.data.map { it[Keys.TG_PROXY_USERNAME].orEmpty() }
    val tgProxyPassword: Flow<String> = context.dataStore.data.map { Crypto.decrypt(it[Keys.TG_PROXY_PASSWORD].orEmpty()) }
    val tgProxySecret: Flow<String> = context.dataStore.data.map { Crypto.decrypt(it[Keys.TG_PROXY_SECRET].orEmpty()) }
    val tgProxyEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_PROXY_ENABLED] ?: false }
    val tgLogVerbosity: Flow<Int> = context.dataStore.data.map { it[Keys.TG_LOG_VERBOSITY] ?: 1 }
    val logDirTreeUri: Flow<String> = context.dataStore.data.map { it[Keys.LOG_DIR_TREE_URI].orEmpty() }
    val tgLogOverlayEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_LOG_OVERLAY] ?: false }
    val tgPrefetchWindowMb: Flow<Int> = context.dataStore.data.map { it[Keys.TG_PREFETCH_WINDOW_MB] ?: 8 }
    val tgSeekBoostEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_SEEK_BOOST_ENABLED] ?: true }
    val tgMaxParallelDownloads: Flow<Int> = context.dataStore.data.map { it[Keys.TG_MAX_PARALLEL_DOWNLOADS] ?: 2 }
    val tgStorageOptimizerEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_STORAGE_OPTIMIZER] ?: true }
    val tgIgnoreFileNames: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_IGNORE_FILE_NAMES] ?: false }
    val tgAutoWifiEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_WIFI_ENABLED] ?: true }
    val tgAutoWifiPreloadLarge: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_WIFI_PRELOAD_LARGE] ?: true }
    val tgAutoWifiPreloadNextAudio: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_WIFI_PRELOAD_NEXT_AUDIO] ?: true }
    val tgAutoWifiPreloadStories: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_WIFI_PRELOAD_STORIES] ?: false }
    val tgAutoWifiLessDataCalls: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_WIFI_LESS_DATA_CALLS] ?: false }
    val tgAutoMobileEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_MOBILE_ENABLED] ?: true }
    val tgAutoMobilePreloadLarge: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_MOBILE_PRELOAD_LARGE] ?: false }
    val tgAutoMobilePreloadNextAudio: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_MOBILE_PRELOAD_NEXT_AUDIO] ?: false }
    val tgAutoMobilePreloadStories: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_MOBILE_PRELOAD_STORIES] ?: false }
    val tgAutoMobileLessDataCalls: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_MOBILE_LESS_DATA_CALLS] ?: true }
    val tgAutoRoamingEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_ROAM_ENABLED] ?: false }
    val tgAutoRoamingPreloadLarge: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_ROAM_PRELOAD_LARGE] ?: false }
    val tgAutoRoamingPreloadNextAudio: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_ROAM_PRELOAD_NEXT_AUDIO] ?: false }
    val tgAutoRoamingPreloadStories: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_ROAM_PRELOAD_STORIES] ?: false }
    val tgAutoRoamingLessDataCalls: Flow<Boolean> = context.dataStore.data.map { it[Keys.TG_AUTO_ROAM_LESS_DATA_CALLS] ?: true }
    val tgApiId: Flow<Int> = context.dataStore.data.map { it[Keys.TG_API_ID] ?: 0 }
    val tgApiHash: Flow<String> = context.dataStore.data.map { it[Keys.TG_API_HASH].orEmpty() }

    // Debug/Logging
    val httpLogEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HTTP_LOG_ENABLED] ?: false }
    val globalDebugEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.GLOBAL_DEBUG_ENABLED] ?: false }

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
    suspend fun setUserAgent(value: String) { context.dataStore.edit { it[Keys.USER_AGENT] = value } }
    suspend fun setReferer(value: String) { context.dataStore.edit { it[Keys.REFERER] = value } }
    suspend fun setExtraHeadersJson(value: String) { context.dataStore.edit { it[Keys.EXTRA_HEADERS] = value } }
    suspend fun setNetworkBases(m3u: String, epg: String, ua: String, referer: String) {
        context.dataStore.edit {
            it[Keys.M3U_URL] = m3u
            it[Keys.EPG_URL] = epg
            it[Keys.USER_AGENT] = ua
            it[Keys.REFERER] = referer
        }
    }
    suspend fun setXtHost(value: String) { context.dataStore.edit { it[Keys.XT_HOST] = value } }
    suspend fun setXtPort(value: Int) { context.dataStore.edit { it[Keys.XT_PORT] = value } }
    suspend fun setXtUser(value: String) { context.dataStore.edit { it[Keys.XT_USER] = value } }
    suspend fun setXtPass(value: String) { context.dataStore.edit { it[Keys.XT_PASS] = Crypto.encrypt(value) } }
    suspend fun setXtOutput(value: String) { context.dataStore.edit { it[Keys.XT_OUTPUT] = value } }
    suspend fun setXtPortVerified(v: Boolean) { context.dataStore.edit { it[Keys.XT_PORT_VERIFIED] = v } }
    suspend fun setXtOutputVerified(v: Boolean) { context.dataStore.edit { it[Keys.XT_OUTPUT_VERIFIED] = v } }
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
    suspend fun setSeedPrefixesCsv(csv: String) { context.dataStore.edit { it[Keys.SEED_PREFIXES_GLOBAL_CSV] = csv } }
    // Library sort setters
    suspend fun setLibVodSortNewest(value: Boolean) { context.dataStore.edit { it[Keys.LIB_VOD_SORT_NEWEST] = value } }
    suspend fun setLibSeriesSortNewest(value: Boolean) { context.dataStore.edit { it[Keys.LIB_SERIES_SORT_NEWEST] = value } }
    // Import diagnostics setters
    suspend fun setLastImportAtMs(value: Long) { context.dataStore.edit { it[Keys.LAST_IMPORT_AT_MS] = value } }
    suspend fun setLastSeedCounts(live: Int, vod: Int, series: Int) {
        context.dataStore.edit {
            it[Keys.LAST_SEED_LIVE] = live
            it[Keys.LAST_SEED_VOD] = vod
            it[Keys.LAST_SEED_SERIES] = series
        }
    }
    suspend fun setLastDeltaCounts(live: Int, vod: Int, series: Int) {
        context.dataStore.edit {
            it[Keys.LAST_DELTA_LIVE] = live
            it[Keys.LAST_DELTA_VOD] = vod
            it[Keys.LAST_DELTA_SERIES] = series
        }
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
                    else -> {
                        if (name == Keys.XT_PASS.name) prefs[Keys.XT_PASS] = Crypto.encrypt(s) else prefs[stringPreferencesKey(name)] = s
                    }
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

    // Batch setters to avoid multiple edit blocks
    suspend fun setXtream(host: String, port: Int, user: String, pass: String, output: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.XT_HOST] = host
            prefs[Keys.XT_PORT] = port
            prefs[Keys.XT_USER] = user
            prefs[Keys.XT_PASS] = pass
            prefs[Keys.XT_OUTPUT] = output
        }
    }

    suspend fun setSources(m3u: String, epg: String, ua: String, referer: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.M3U_URL] = m3u
            prefs[Keys.EPG_URL] = epg
            prefs[Keys.USER_AGENT] = ua
            prefs[Keys.REFERER] = referer
        }
    }

    // Telegram setters
    suspend fun setTelegramEnabled(value: Boolean) { context.dataStore.edit { it[Keys.TG_ENABLED] = value } }
    suspend fun setTelegramSelectedChatsCsv(value: String) { context.dataStore.edit { it[Keys.TG_SELECTED_CHATS_CSV] = value } }
    /** Einmalige Migration: vereinige alte getrennte CSVs (vod/series) in das neue Feld. */
    suspend fun migrateTelegramSelectedChatsIfNeeded() {
        val current = tgSelectedChatsCsv.first()
        if (current.isNotBlank()) return
        val vod = tgSelectedVodChatsCsv.first()
        val ser = tgSelectedSeriesChatsCsv.first()
        val merged = (vod.split(',') + ser.split(','))
            .mapNotNull { it.trim().toLongOrNull() }
            .distinct()
            .joinToString(",")
        if (merged.isNotBlank()) setTelegramSelectedChatsCsv(merged)
    }
    suspend fun setTelegramCacheLimitGb(value: Int) { context.dataStore.edit { it[Keys.TG_CACHE_LIMIT_GB] = value } }
    suspend fun setTelegramSelectedVodChatsCsv(value: String) { context.dataStore.edit { it[Keys.TG_SELECTED_VOD_CHATS_CSV] = value } }
    suspend fun setTelegramSelectedSeriesChatsCsv(value: String) { context.dataStore.edit { it[Keys.TG_SELECTED_SERIES_CHATS_CSV] = value } }
    suspend fun setTelegramApiId(value: Int) { context.dataStore.edit { it[Keys.TG_API_ID] = value } }
    suspend fun setTelegramApiHash(value: String) { context.dataStore.edit { it[Keys.TG_API_HASH] = value } }
    suspend fun setTelegramPreferIpv6(value: Boolean) { context.dataStore.edit { it[Keys.TG_PREFER_IPV6] = value } }
    suspend fun setTelegramStayOnline(value: Boolean) { context.dataStore.edit { it[Keys.TG_STAY_ONLINE] = value } }
    suspend fun setTelegramProxyType(value: String) { context.dataStore.edit { it[Keys.TG_PROXY_TYPE] = value } }
    suspend fun setTelegramProxyHost(value: String) { context.dataStore.edit { it[Keys.TG_PROXY_HOST] = value } }
    suspend fun setTelegramProxyPort(value: Int) { context.dataStore.edit { it[Keys.TG_PROXY_PORT] = value } }
    suspend fun setTelegramProxyUsername(value: String) { context.dataStore.edit { it[Keys.TG_PROXY_USERNAME] = value } }
    suspend fun setTelegramProxyPassword(value: String) { context.dataStore.edit { it[Keys.TG_PROXY_PASSWORD] = Crypto.encrypt(value) } }
    suspend fun setTelegramProxySecret(value: String) { context.dataStore.edit { it[Keys.TG_PROXY_SECRET] = Crypto.encrypt(value) } }
    suspend fun setTelegramProxyEnabled(value: Boolean) { context.dataStore.edit { it[Keys.TG_PROXY_ENABLED] = value } }
    suspend fun setTgProxyType(value: String) = setTelegramProxyType(value)
    suspend fun setTgProxyHost(value: String) = setTelegramProxyHost(value)
    suspend fun setTgProxyPort(value: Int) = setTelegramProxyPort(value)
    suspend fun setTgProxyUsername(value: String) = setTelegramProxyUsername(value)
    suspend fun setTgProxyPassword(value: String) = setTelegramProxyPassword(value)
    suspend fun setTgProxySecret(value: String) = setTelegramProxySecret(value)
    suspend fun setTgProxyEnabled(value: Boolean) = setTelegramProxyEnabled(value)
    suspend fun setTelegramLogVerbosity(value: Int) { context.dataStore.edit { it[Keys.TG_LOG_VERBOSITY] = value } }
    suspend fun setTelegramPrefetchWindowMb(value: Int) { context.dataStore.edit { it[Keys.TG_PREFETCH_WINDOW_MB] = value } }
    suspend fun setTelegramSeekBoostEnabled(value: Boolean) { context.dataStore.edit { it[Keys.TG_SEEK_BOOST_ENABLED] = value } }
    suspend fun setTelegramMaxParallelDownloads(value: Int) { context.dataStore.edit { it[Keys.TG_MAX_PARALLEL_DOWNLOADS] = value } }
    suspend fun setTelegramStorageOptimizerEnabled(value: Boolean) { context.dataStore.edit { it[Keys.TG_STORAGE_OPTIMIZER] = value } }
    suspend fun setTelegramIgnoreFileNames(value: Boolean) { context.dataStore.edit { it[Keys.TG_IGNORE_FILE_NAMES] = value } }
    suspend fun setTelegramLogOverlayEnabled(value: Boolean) { context.dataStore.edit { it[Keys.TG_LOG_OVERLAY] = value } }
    suspend fun setTelegramAutoWifi(
        enabled: Boolean,
        preloadLarge: Boolean,
        preloadNextAudio: Boolean,
        preloadStories: Boolean,
        lessDataCalls: Boolean
    ) {
        context.dataStore.edit {
            it[Keys.TG_AUTO_WIFI_ENABLED] = enabled
            it[Keys.TG_AUTO_WIFI_PRELOAD_LARGE] = preloadLarge
            it[Keys.TG_AUTO_WIFI_PRELOAD_NEXT_AUDIO] = preloadNextAudio
            it[Keys.TG_AUTO_WIFI_PRELOAD_STORIES] = preloadStories
            it[Keys.TG_AUTO_WIFI_LESS_DATA_CALLS] = lessDataCalls
        }
    }
    suspend fun setTelegramAutoMobile(
        enabled: Boolean,
        preloadLarge: Boolean,
        preloadNextAudio: Boolean,
        preloadStories: Boolean,
        lessDataCalls: Boolean
    ) {
        context.dataStore.edit {
            it[Keys.TG_AUTO_MOBILE_ENABLED] = enabled
            it[Keys.TG_AUTO_MOBILE_PRELOAD_LARGE] = preloadLarge
            it[Keys.TG_AUTO_MOBILE_PRELOAD_NEXT_AUDIO] = preloadNextAudio
            it[Keys.TG_AUTO_MOBILE_PRELOAD_STORIES] = preloadStories
            it[Keys.TG_AUTO_MOBILE_LESS_DATA_CALLS] = lessDataCalls
        }
    }
    suspend fun setTelegramAutoRoaming(
        enabled: Boolean,
        preloadLarge: Boolean,
        preloadNextAudio: Boolean,
        preloadStories: Boolean,
        lessDataCalls: Boolean
    ) {
        context.dataStore.edit {
            it[Keys.TG_AUTO_ROAM_ENABLED] = enabled
            it[Keys.TG_AUTO_ROAM_PRELOAD_LARGE] = preloadLarge
            it[Keys.TG_AUTO_ROAM_PRELOAD_NEXT_AUDIO] = preloadNextAudio
            it[Keys.TG_AUTO_ROAM_PRELOAD_STORIES] = preloadStories
            it[Keys.TG_AUTO_ROAM_LESS_DATA_CALLS] = lessDataCalls
        }
    }
    suspend fun setTgAutoWifiEnabled(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_WIFI_ENABLED] = value } }
    suspend fun setTgAutoWifiPreloadLarge(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_WIFI_PRELOAD_LARGE] = value } }
    suspend fun setTgAutoWifiPreloadNextAudio(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_WIFI_PRELOAD_NEXT_AUDIO] = value } }
    suspend fun setTgAutoWifiPreloadStories(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_WIFI_PRELOAD_STORIES] = value } }
    suspend fun setTgAutoWifiLessDataCalls(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_WIFI_LESS_DATA_CALLS] = value } }
    suspend fun setTgAutoMobileEnabled(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_MOBILE_ENABLED] = value } }
    suspend fun setTgAutoMobilePreloadLarge(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_MOBILE_PRELOAD_LARGE] = value } }
    suspend fun setTgAutoMobilePreloadNextAudio(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_MOBILE_PRELOAD_NEXT_AUDIO] = value } }
    suspend fun setTgAutoMobilePreloadStories(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_MOBILE_PRELOAD_STORIES] = value } }
    suspend fun setTgAutoMobileLessDataCalls(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_MOBILE_LESS_DATA_CALLS] = value } }
    suspend fun setTgAutoRoamingEnabled(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_ROAM_ENABLED] = value } }
    suspend fun setTgAutoRoamingPreloadLarge(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_ROAM_PRELOAD_LARGE] = value } }
    suspend fun setTgAutoRoamingPreloadNextAudio(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_ROAM_PRELOAD_NEXT_AUDIO] = value } }
    suspend fun setTgAutoRoamingPreloadStories(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_ROAM_PRELOAD_STORIES] = value } }
    suspend fun setTgAutoRoamingLessDataCalls(value: Boolean) { context.dataStore.edit { it[Keys.TG_AUTO_ROAM_LESS_DATA_CALLS] = value } }
    suspend fun setTgEnabled(value: Boolean) = setTelegramEnabled(value)
    suspend fun setTgLogVerbosity(value: Int) = setTelegramLogVerbosity(value)
    suspend fun setLogDirTreeUri(value: String) { context.dataStore.edit { it[Keys.LOG_DIR_TREE_URI] = value } }

    data class Snapshot(
        val m3uUrl: String,
        val epgUrl: String,
        val userAgent: String,
        val referer: String,
        val extraHeadersJson: String,
        val xtHost: String,
        val xtPort: Int,
        val xtUser: String,
        val xtPass: String,
        val xtOutput: String,
        val epgFavUseXtream: Boolean,
        val epgFavSkipXmltvIfXtreamOk: Boolean,
        val tgEnabled: Boolean,
        val tgSelectedChatsCsv: String,
        val tgCacheLimitGb: Int
    )

    suspend fun snapshot(): Snapshot {
        val prefs = context.dataStore.data.first()
        fun <T> get(k: Preferences.Key<T>, def: T): T = prefs[k] ?: def
        return Snapshot(
            m3uUrl = get(Keys.M3U_URL, ""),
            epgUrl = get(Keys.EPG_URL, ""),
            userAgent = get(Keys.USER_AGENT, "IBOPlayer/1.4 (Android)"),
            referer = get(Keys.REFERER, ""),
            extraHeadersJson = get(Keys.EXTRA_HEADERS, ""),
            xtHost = get(Keys.XT_HOST, ""),
            xtPort = get(Keys.XT_PORT, 80),
            xtUser = get(Keys.XT_USER, ""),
            xtPass = Crypto.decrypt(get(Keys.XT_PASS, "")),
            xtOutput = get(Keys.XT_OUTPUT, "m3u8"),
            epgFavUseXtream = get(Keys.EPG_FAV_USE_XTREAM, true),
            epgFavSkipXmltvIfXtreamOk = get(Keys.EPG_FAV_SKIP_XMLTV_IF_X_OK, false),
            tgEnabled = get(Keys.TG_ENABLED, false),
            tgSelectedChatsCsv = get(Keys.TG_SELECTED_CHATS_CSV, ""),
            tgCacheLimitGb = get(Keys.TG_CACHE_LIMIT_GB, 2)
        )
    }

    // Live category rows state setters
    suspend fun setLiveCatCollapsedCsv(value: String) { context.dataStore.edit { it[Keys.LIVE_CAT_COLLAPSED_CSV] = value } }
    suspend fun setLiveCatExpandedOrderCsv(value: String) { context.dataStore.edit { it[Keys.LIVE_CAT_EXPANDED_ORDER_CSV] = value } }

    // VOD/Series category rows state setters
    suspend fun setVodCatCollapsedCsv(value: String) { context.dataStore.edit { it[Keys.VOD_CAT_COLLAPSED_CSV] = value } }
    suspend fun setVodCatExpandedOrderCsv(value: String) { context.dataStore.edit { it[Keys.VOD_CAT_EXPANDED_ORDER_CSV] = value } }
    suspend fun setSeriesCatCollapsedCsv(value: String) { context.dataStore.edit { it[Keys.SERIES_CAT_COLLAPSED_CSV] = value } }
    suspend fun setSeriesCatExpandedOrderCsv(value: String) { context.dataStore.edit { it[Keys.SERIES_CAT_EXPANDED_ORDER_CSV] = value } }
    // Library grouping setters
    suspend fun setLibGroupByGenreLive(value: Boolean) { context.dataStore.edit { it[Keys.LIB_GROUP_BY_GENRE_LIVE] = value } }
    suspend fun setLibGroupByGenreVod(value: Boolean) { context.dataStore.edit { it[Keys.LIB_GROUP_BY_GENRE_VOD] = value } }
    suspend fun setLibGroupByGenreSeries(value: Boolean) { context.dataStore.edit { it[Keys.LIB_GROUP_BY_GENRE_SERIES] = value } }

    // Logging
    suspend fun setHttpLogEnabled(value: Boolean) { context.dataStore.edit { it[Keys.HTTP_LOG_ENABLED] = value } }
    suspend fun setGlobalDebugEnabled(value: Boolean) { context.dataStore.edit { it[Keys.GLOBAL_DEBUG_ENABLED] = value } }
    // Feature gates setters
    suspend fun setRoomEnabled(value: Boolean) { context.dataStore.edit { it[Keys.ROOM_ENABLED] = value } }
    suspend fun setM3uWorkersEnabled(value: Boolean) { context.dataStore.edit { it[Keys.M3U_WORKERS_ENABLED] = value } }
    suspend fun setShowAdults(value: Boolean) { context.dataStore.edit { it[Keys.SHOW_ADULTS] = value } }

}
