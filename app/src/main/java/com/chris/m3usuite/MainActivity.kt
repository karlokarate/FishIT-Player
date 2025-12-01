package com.chris.m3usuite

import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.metrics.performance.JankStats
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chris.m3usuite.core.logging.AppLog
import com.chris.m3usuite.logs.ui.LogViewerScreen
import com.chris.m3usuite.navigation.navigateTopLevel
import com.chris.m3usuite.playback.PlaybackSession
import com.chris.m3usuite.player.InternalPlayerEntry
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.miniplayer.DefaultMiniPlayerManager
import com.chris.m3usuite.prefs.Keys
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.ui.TelegramLogScreen
import com.chris.m3usuite.telegram.ui.feed.TelegramActivityFeedScreen
import com.chris.m3usuite.ui.auth.ProfileGate
import com.chris.m3usuite.ui.focus.isTvDevice
import com.chris.m3usuite.ui.home.LocalMiniPlayerResume
import com.chris.m3usuite.ui.home.MiniPlayerSnapshot
import com.chris.m3usuite.ui.home.MiniPlayerState
import com.chris.m3usuite.ui.home.StartScreen
import com.chris.m3usuite.ui.home.buildRoute
import com.chris.m3usuite.ui.profile.ProfileManagerScreen
import com.chris.m3usuite.ui.screens.LibraryScreen
import com.chris.m3usuite.ui.screens.LiveDetailScreen
import com.chris.m3usuite.ui.screens.PlaylistSetupScreen
import com.chris.m3usuite.ui.screens.SeriesDetailScreen
import com.chris.m3usuite.ui.screens.SettingsScreen
import com.chris.m3usuite.ui.screens.TelegramDetailScreen
import com.chris.m3usuite.ui.screens.TelegramItemDetailScreen
import com.chris.m3usuite.ui.screens.VodDetailScreen
import com.chris.m3usuite.ui.screens.XtreamPortalCheckScreen
import com.chris.m3usuite.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Vollbild / Immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        // JankStats: lightweight performance tracking with per-route counters
        runCatching {
            JankStats.createAndTrack(window) { frameData ->
                val route = com.chris.m3usuite.metrics.RouteTag.current
                com.chris.m3usuite.metrics.JankReporter
                    .record(route, frameData.isJank, frameData.frameDurationUiNanos)
            }
        }

        setContent {
            AppTheme {
                val nav = rememberNavController()
                // Global nav debug listener (switchable via Settings)
                androidx.compose.runtime.DisposableEffect(nav) {
                    val listener =
                        androidx.navigation.NavController.OnDestinationChangedListener { controller, destination, arguments ->
                            val from =
                                controller.previousBackStackEntry?.destination?.route
                                    ?: controller.previousBackStackEntry?.destination?.displayName
                            val to = destination.route ?: destination.displayName
                            com.chris.m3usuite.core.debug.GlobalDebug
                                .logNavigation(from, to)
                        }
                    nav.addOnDestinationChangedListener(listener)
                    onDispose { nav.removeOnDestinationChangedListener(listener) }
                }

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                val ctx = LocalContext.current
                val store = remember(ctx) { SettingsStore(ctx) }
                // React to Xtream creds becoming available at runtime (Settings/Setup)
                val xtHost by store.xtHost.collectAsStateWithLifecycle(initialValue = "")
                val xtUser by store.xtUser.collectAsStateWithLifecycle(initialValue = "")
                val xtPass by store.xtPass.collectAsStateWithLifecycle(initialValue = "")
                val xtPort by store.xtPort.collectAsStateWithLifecycle(initialValue = 0)
                // Keep HTTP header snapshot updated globally for OkHttp/Coil, lifecycle-aware
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        com.chris.m3usuite.core.http.RequestHeadersProvider
                            .collect(store)
                    }
                }
                // Keep TrafficLogger state in sync with Settings (persists across restarts)
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        store.httpLogEnabled.collect { enabled ->
                            com.chris.m3usuite.core.http.TrafficLogger
                                .setEnabled(enabled)
                        }
                    }
                }
                // Keep GlobalDebug in sync with Settings
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        store.globalDebugEnabled.collect { on ->
                            com.chris.m3usuite.core.debug.GlobalDebug
                                .setEnabled(on)
                        }
                    }
                }
                // Keep AppLog switches in sync with Settings
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        launch {
                            store.logMasterEnabled.collectLatest { enabled ->
                                AppLog.setMasterEnabled(enabled)
                            }
                        }
                        launch {
                            store.logCategories.collectLatest { cats ->
                                AppLog.setCategoriesEnabled(cats)
                            }
                        }
                    }
                }

                // Track app start time for new-episode detection
                LaunchedEffect(Unit) { store.setLastAppStartMs(System.currentTimeMillis()) }

                // DataStore-Flow beobachten
                val m3uUrl = store.m3uUrl.collectAsStateWithLifecycle(initialValue = "").value
                val apiEnabled = store.m3uWorkersEnabled.collectAsStateWithLifecycle(initialValue = true).value

                // Startziel: auch ohne M3U kann die App starten (Einstellungen später setzen)
                val startDestination = "gate"
                // Wenn M3U vorhanden aber Xtream noch nicht konfiguriert (z. B. nach App-Reinstall via Backup):
                // automatisch aus der M3U ableiten und direkt die Worker planen.
                LaunchedEffect(m3uUrl) {
                    if (m3uUrl.isNotBlank() && apiEnabled) {
                        if (!store.hasXtream()) {
                            runCatching {
                                val cfg =
                                    com.chris.m3usuite.core.xtream.XtreamConfig
                                        .fromM3uUrl(m3uUrl)
                                if (cfg != null) {
                                    store.setXtHost(cfg.host)
                                    store.setXtPort(cfg.port)
                                    store.setXtUser(cfg.username)
                                    store.setXtPass(cfg.password)
                                    cfg.liveExtPrefs.firstOrNull()?.let { store.setXtOutput(it) }
                                    store.setXtPortVerified(true)
                                    if (store.epgUrl.first().isBlank()) {
                                        store.set(
                                            Keys.EPG_URL,
                                            "${cfg.portalBase}/xmltv.php?username=${cfg.username}&password=${cfg.password}",
                                        )
                                    }
                                } else {
                                    // Kein Port in M3U: Discovery verwenden (Xtream bevorzugen)
                                    val u = Uri.parse(m3uUrl)
                                    val scheme = (u.scheme ?: "http").lowercase()
                                    val host = (u.host ?: "")
                                    val user = u.getQueryParameter("username")
                                    val pass = u.getQueryParameter("password")
                                    if (host.isNotBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank()) {
                                        val http =
                                            com.chris.m3usuite.core.http.HttpClientFactory
                                                .create(this@MainActivity, store)
                                        val capStore =
                                            com.chris.m3usuite.core.xtream
                                                .ProviderCapabilityStore(this@MainActivity)
                                        val portStore =
                                            com.chris.m3usuite.core.xtream
                                                .EndpointPortStore(this@MainActivity)
                                        val discoverer =
                                            com.chris.m3usuite.core.xtream
                                                .CapabilityDiscoverer(http, capStore, portStore)
                                        val caps = discoverer.discoverAuto(scheme, host, user, pass, null, forceRefresh = false)
                                        val bu = Uri.parse(caps.baseUrl)
                                        val rs = (bu.scheme ?: scheme).lowercase()
                                        val rh = bu.host ?: host
                                        val rp = bu.port
                                        if (rp > 0) {
                                            store.setXtHost(rh)
                                            store.setXtPort(rp)
                                            store.setXtUser(user)
                                            store.setXtPass(pass)
                                            store.setXtPortVerified(true)
                                            if (store.epgUrl.first().isBlank()) {
                                                store.set(Keys.EPG_URL, "$rs://$rh:$rp/xmltv.php?username=$user&password=$pass")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                /**
                 * BUG 4 fix: Debounced and validated Xtream auto-import.
                 *
                 * This LaunchedEffect now:
                 * 1. Validates that config is complete (host, user, pass all non-blank, port > 0)
                 * 2. Adds 750ms debounce to avoid triggering on every keystroke
                 * 3. Wraps import in try/catch with proper error handling
                 *
                 * This prevents the crash that occurred when:
                 * - User enters credentials for first time
                 * - Auto-import triggers with partial/incomplete state
                 * - Back navigation during import causes race condition
                 */
                LaunchedEffect(xtHost, xtUser, xtPass, xtPort) {
                    // Validation: All required fields must be non-blank
                    if (xtHost.isBlank() || xtUser.isBlank() || xtPass.isBlank()) return@LaunchedEffect
                    // Validation: Port must be positive
                    if (xtPort <= 0) return@LaunchedEffect
                    if (!apiEnabled) return@LaunchedEffect

                    // BUG 4 fix: Debounce to avoid triggering on every keystroke
                    delay(750)

                    // Start background import so index builds even if the UI recomposes or route changes.
                    // Immediately ensure full header lists (heads-only delta) for VOD/Series at app start; skip Live to stay light.
                    withContext(Dispatchers.IO) {
                        try {
                            com.chris.m3usuite.data.repo
                                .XtreamObxRepository(
                                    ctx,
                                    store,
                                ).importDelta(deleteOrphans = false, includeLive = false)
                        } catch (e: Exception) {
                            // BUG 4 fix: Log error instead of crashing
                            AppLog.log(
                                category = "xtream",
                                level = AppLog.Level.ERROR,
                                message = "Delta import failed: ${e.message}",
                                extras = mapOf(
                                    "host" to xtHost,
                                    "port" to xtPort.toString(),
                                    "error" to (e.javaClass.simpleName),
                                ),
                            )
                        }
                    }
                    // Also schedule a background one-shot (heads-only again if needed; Live remains off here)
                    com.chris.m3usuite.work.XtreamDeltaImportWorker
                        .triggerOnce(ctx, includeLive = false, vodLimit = 0, seriesLimit = 0)
                    // Schedule Live heads-only to come in later (delayed), so startup stays light
                    com.chris.m3usuite.work.XtreamDeltaImportWorker
                        .triggerOnceDelayedLive(ctx, delayMinutes = 5)
                }

                // EPG periodic refresh removed; lazy on-demand prefetch keeps visible/favorites fresh

                CompositionLocalProvider(
                    LocalMiniPlayerResume provides { snapshot: MiniPlayerSnapshot ->
                        val route = snapshot.descriptor.buildRoute(snapshot.positionMs)
                        MiniPlayerState.hide()
                        nav.navigate(route) { launchSingleTop = true }
                    },
                ) {
                    NavHost(navController = nav, startDestination = startDestination) {
                        composable("setup") {
                            PlaylistSetupScreen(
                                onDone = {
                                    nav.navigate("library?q=&qs=") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                },
                            )
                        }

                        composable("gate") {
                            ProfileGate(onEnter = {
                                nav.navigate("library?q=&qs=") {
                                    popUpTo("gate") { inclusive = true }
                                }
                            })
                        }

                        composable(
                            route = "library?q={q}&qs={qs}",
                            arguments =
                                listOf(
                                    navArgument("q") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    },
                                    navArgument("qs") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    },
                                ),
                        ) { back ->
                            val q = back.arguments?.getString("q").orEmpty()
                            val qs = back.arguments?.getString("qs").orEmpty()
                            val openDlg = qs == "show"
                            StartScreen(
                                navController = nav,
                                openLive = { id -> nav.navigate("live/$id") },
                                openVod = { id -> nav.navigate("vod/$id") },
                                openSeries = { id -> nav.navigate("series/$id") },
                                openTelegram = { id -> nav.navigate("telegram/$id") },
                                // Phase D: New TelegramItem navigation with (chatId, anchorMessageId)
                                openTelegramItem = { chatId, anchorMessageId ->
                                    nav.navigate("telegram_item/$chatId/$anchorMessageId")
                                },
                                initialSearch = q.ifBlank { null },
                                openSearchOnStart = openDlg,
                            )
                        }

                        // Full browser (previous LibraryScreen)
                        composable("browse") {
                            LibraryScreen(
                                navController = nav,
                                openLive = { id -> nav.navigate("live/$id") },
                                openVod = { id -> nav.navigate("vod/$id") },
                                openSeries = { id -> nav.navigate("series/$id") },
                                openTelegram = { id -> nav.navigate("telegram/$id") },
                                // Phase D: New TelegramItem navigation with (chatId, anchorMessageId)
                                openTelegramItem = { chatId, anchorMessageId ->
                                    nav.navigate("telegram_item/$chatId/$anchorMessageId")
                                },
                            )
                        }

                        composable("live/{id}") { back ->
                            val id = back.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                            LiveDetailScreen(id, onLogo = {
                                val current = nav.currentBackStackEntry?.destination?.route
                                if (current != "library") {
                                    nav.navigateTopLevel("library?q=&qs=")
                                }
                            }, openLive = { target ->
                                nav.navigate("live/$target")
                            }, onGlobalSearch = {
                                nav.navigateTopLevel("library?qs=show")
                            }, onOpenSettings = {
                                val current = nav.currentBackStackEntry?.destination?.route
                                if (current != "settings") {
                                    nav.navigate("settings") { launchSingleTop = true }
                                }
                            })
                        }

                        // VOD-Details – mit Lambda für internen Player
                        composable("vod/{id}") { back ->
                            val id = back.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                            VodDetailScreen(
                                id = id,
                                openInternal = { url, startMs, mime ->
                                    val encoded = Uri.encode(url)
                                    val start = startMs ?: -1L
                                    val mimeArg = mime?.let { Uri.encode(it) } ?: ""
                                    nav.navigate("player?url=$encoded&type=vod&mediaId=$id&startMs=$start&mime=$mimeArg")
                                },
                                openVod = { target -> nav.navigate("vod/$target") },
                                onLogo = {
                                    val current = nav.currentBackStackEntry?.destination?.route
                                    if (current != "library") {
                                        nav.navigateTopLevel("library?q=&qs=")
                                    }
                                },
                                onGlobalSearch = {
                                    nav.navigateTopLevel("library?qs=show")
                                },
                                onOpenSettings = {
                                    val current = nav.currentBackStackEntry?.destination?.route
                                    if (current != "settings") {
                                        nav.navigate("settings") { launchSingleTop = true }
                                    }
                                },
                            )
                        }

                        // Telegram-Details
                        composable("telegram/{id}") { back ->
                            val id = back.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                            TelegramDetailScreen(
                                id = id,
                                openInternal = { url, startMs, mime ->
                                    val encoded = Uri.encode(url)
                                    val start = startMs ?: -1L
                                    val mimeArg = mime?.let { Uri.encode(it) } ?: ""
                                    nav.navigate("player?url=$encoded&type=vod&mediaId=$id&startMs=$start&mime=$mimeArg")
                                },
                                onLogo = {
                                    val current = nav.currentBackStackEntry?.destination?.route
                                    if (current != "library") {
                                        nav.navigateTopLevel("library?q=&qs=")
                                    }
                                },
                                onGlobalSearch = {
                                    nav.navigateTopLevel("library?qs=show")
                                },
                                onOpenSettings = {
                                    val current = nav.currentBackStackEntry?.destination?.route
                                    if (current != "settings") {
                                        nav.navigate("settings") { launchSingleTop = true }
                                    }
                                },
                            )
                        }

                        // Phase D: TelegramItem-Details using (chatId, anchorMessageId)
                        composable(
                            route = "telegram_item/{chatId}/{anchorMessageId}",
                            arguments =
                                listOf(
                                    navArgument("chatId") { type = NavType.LongType },
                                    navArgument("anchorMessageId") { type = NavType.LongType },
                                ),
                        ) { back ->
                            val chatId = back.arguments?.getLong("chatId") ?: return@composable
                            val anchorMessageId = back.arguments?.getLong("anchorMessageId") ?: return@composable
                            TelegramItemDetailScreen(
                                chatId = chatId,
                                anchorMessageId = anchorMessageId,
                                openInternal = { url, startMs, mime ->
                                    val encoded = Uri.encode(url)
                                    val start = startMs ?: -1L
                                    val mimeArg = mime?.let { Uri.encode(it) } ?: ""
                                    // Use legacy mediaId encoding for resume position compatibility
                                    val legacyId =
                                        com.chris.m3usuite.telegram.util.TelegramPlayUrl
                                            .TELEGRAM_MEDIA_ID_OFFSET + anchorMessageId
                                    nav.navigate("player?url=$encoded&type=vod&mediaId=$legacyId&startMs=$start&mime=$mimeArg")
                                },
                                onLogo = {
                                    val current = nav.currentBackStackEntry?.destination?.route
                                    if (current != "library") {
                                        nav.navigateTopLevel("library?q=&qs=")
                                    }
                                },
                                onGlobalSearch = {
                                    nav.navigateTopLevel("library?qs=show")
                                },
                                onOpenSettings = {
                                    val current = nav.currentBackStackEntry?.destination?.route
                                    if (current != "settings") {
                                        nav.navigate("settings") { launchSingleTop = true }
                                    }
                                },
                            )
                        }

                        // Serien-Details – mit Lambda für internen Player (Episoden)
                        composable("series/{id}") { back ->
                            val id = back.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                            SeriesDetailScreen(
                                id = id,
                                openInternal = { playUrl, startMs, seriesId, season, episodeNum, episodeId, mime ->
                                    val encoded = Uri.encode(playUrl)
                                    val start = startMs ?: -1L
                                    val epId = episodeId ?: -1
                                    val mimeArg = mime?.let { Uri.encode(it) } ?: ""
                                    nav.navigate(
                                        "player?url=$encoded&type=series&seriesId=$seriesId&season=$season&episodeNum=$episodeNum&episodeId=$epId&startMs=$start&mime=$mimeArg",
                                    )
                                },
                                onLogo = {
                                    val current = nav.currentBackStackEntry?.destination?.route
                                    if (current != "library") {
                                        nav.navigateTopLevel("library?q=&qs=")
                                    }
                                },
                                onOpenSettings = {
                                    val current = nav.currentBackStackEntry?.destination?.route
                                    if (current != "settings") {
                                        nav.navigate("settings") { launchSingleTop = true }
                                    }
                                },
                            )
                        }

                        // Interner ExoPlayer (Media3)
                        composable(
                            route = "player?url={url}&type={type}&mediaId={mediaId}&episodeId={episodeId}&seriesId={seriesId}&season={season}&episodeNum={episodeNum}&startMs={startMs}&mime={mime}&origin={origin}&cat={cat}&prov={prov}",
                            arguments =
                                listOf(
                                    navArgument("url") { type = NavType.StringType },
                                    navArgument("type") {
                                        type = NavType.StringType
                                        defaultValue = "vod"
                                    },
                                    navArgument("mediaId") {
                                        type = NavType.LongType
                                        defaultValue = -1L
                                    },
                                    navArgument("episodeId") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("seriesId") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("season") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("episodeNum") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("startMs") {
                                        type = NavType.LongType
                                        defaultValue = -1L
                                    },
                                    navArgument("mime") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    },
                                    navArgument("origin") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    },
                                    navArgument("cat") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    },
                                    navArgument("prov") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    },
                                ),
                        ) { back ->
                            val rawUrl = back.arguments?.getString("url").orEmpty()
                            val url = URLDecoder.decode(rawUrl, StandardCharsets.UTF_8.name())
                            val type = back.arguments?.getString("type") ?: "vod"
                            val mediaId = back.arguments?.getLong("mediaId")?.takeIf { it >= 0 }
                            val episodeId = back.arguments?.getInt("episodeId")?.takeIf { it >= 0 }
                            val seriesId = back.arguments?.getInt("seriesId")?.takeIf { it >= 0 }
                            val season = back.arguments?.getInt("season")?.takeIf { it >= 0 }
                            val episodeNum = back.arguments?.getInt("episodeNum")?.takeIf { it >= 0 }
                            val startMs = back.arguments?.getLong("startMs")?.takeIf { it >= 0 }
                            val rawMime = back.arguments?.getString("mime").orEmpty()
                            val mime = rawMime.takeIf { it.isNotEmpty() }?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
                            val origin = back.arguments?.getString("origin").orEmpty()
                            val cat = back.arguments?.getString("cat").orEmpty()
                            val prov = back.arguments?.getString("prov").orEmpty()

                            // Resolve Telegram MediaItem before launching player (moves repository lookup out of UI)
                            var preparedMediaItem by remember { mutableStateOf<com.chris.m3usuite.model.MediaItem?>(null) }
                            LaunchedEffect(url) {
                                if (url.startsWith("tg://", ignoreCase = true)) {
                                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                                        try {
                                            val parsed = Uri.parse(url)
                                            val messageId = parsed.getQueryParameter("messageId")?.toLongOrNull()
                                            val chatId = parsed.getQueryParameter("chatId")?.toLongOrNull()
                                            if (messageId != null && chatId != null) {
                                                val tgRepo =
                                                    com.chris.m3usuite.data.repo.TelegramContentRepository(
                                                        this@MainActivity,
                                                        store,
                                                    )
                                                // Use withTimeoutOrNull to avoid blocking indefinitely
                                                val mediaItems =
                                                    kotlinx.coroutines.withTimeoutOrNull(5000) {
                                                        tgRepo.getTelegramContentByChat(chatId).first()
                                                    }
                                                preparedMediaItem = mediaItems?.find { it.tgMessageId == messageId }
                                            }
                                        } catch (e: Exception) {
                                            com.chris.m3usuite.core.logging.AppLog.log(
                                                category = "player",
                                                level = com.chris.m3usuite.core.logging.AppLog.Level.ERROR,
                                                message = "Failed to resolve Telegram MediaItem: ${e.message}",
                                                extras = mapOf("url" to url),
                                            )
                                        }
                                    }
                                }
                            }

                            // Build PlaybackContext based on type
                            val playbackContext =
                                when (type) {
                                    "series" ->
                                        PlaybackContext(
                                            type = PlaybackType.SERIES,
                                            mediaId = null,
                                            seriesId = seriesId,
                                            season = season,
                                            episodeNumber = episodeNum,
                                            episodeId = episodeId,
                                            kidProfileId = null, // Will be derived from SettingsStore
                                        )
                                    "live" ->
                                        PlaybackContext(
                                            type = PlaybackType.LIVE,
                                            mediaId = mediaId,
                                            liveCategoryHint = cat.ifBlank { null },
                                            liveProviderHint = prov.ifBlank { null },
                                            kidProfileId = null, // Will be derived from SettingsStore
                                        )
                                    else ->
                                        PlaybackContext( // "vod" or default
                                            type = PlaybackType.VOD,
                                            mediaId = mediaId,
                                            kidProfileId = null, // Will be derived from SettingsStore
                                        )
                                }

                            InternalPlayerEntry(
                                url = url,
                                startMs = startMs,
                                mimeType = mime,
                                headers = emptyMap(),
                                mediaItem = preparedMediaItem,
                                playbackContext = playbackContext,
                                onExit = { nav.popBackStack() },
                            )
                        }

                        // Settings (permissions)
                        composable("settings") {
                            val settingsScope = androidx.compose.runtime.rememberCoroutineScope()
                            val profileId = store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L).value
                            LaunchedEffect(profileId) {
                                val perms =
                                    com.chris.m3usuite.data.repo
                                        .PermissionRepository(this@MainActivity, store)
                                        .current()
                                if (!perms.canOpenSettings) {
                                    nav.popBackStack()
                                }
                            }
                            SettingsScreen(
                                store = store,
                                onBack = { nav.popBackStack() },
                                onOpenProfiles = { nav.navigate("profiles") },
                                onOpenGate = {
                                    nav.navigate("gate") {
                                        popUpTo("library") { inclusive = false }
                                    }
                                },
                                onOpenXtreamCfCheck = { nav.navigate("xt_cfcheck") },
                                onGlobalSearch = {
                                    nav.navigateTopLevel("library?qs=show")
                                },
                                onOpenPortalCheck = { nav.navigate("xt_cfcheck") },
                                onOpenTelegramLog = { nav.navigate("telegram_log") },
                                onOpenTelegramFeed = { nav.navigate("telegram_feed") },
                                onOpenLogViewer = { nav.navigate("log_viewer") },
                                runtimeLoggingEnabled = store.logMasterEnabled.collectAsStateWithLifecycle(initialValue = false).value,
                                onToggleRuntimeLogging = { enabled ->
                                    settingsScope.launch { store.setLogMasterEnabled(enabled) }
                                },
                                telemetryForwardingEnabled =
                                    store.logTelemetryEnabled
                                        .collectAsStateWithLifecycle(
                                            initialValue = false,
                                        ).value,
                                onToggleTelemetryForwarding = { enabled ->
                                    settingsScope.launch { store.setLogTelemetryEnabled(enabled) }
                                },
                                logCategories = store.logCategories.collectAsStateWithLifecycle(initialValue = emptySet()).value,
                                onUpdateLogCategories = { cats ->
                                    settingsScope.launch { store.setLogCategories(cats) }
                                },
                            )
                        }

                        composable("profiles") {
                            // Require adult to open profile manager. Wait for a valid profileId before deciding.
                            val profileId = store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L).value
                            var allow: Boolean? by remember { mutableStateOf(null) }
                            LaunchedEffect(profileId) {
                                if (profileId <= 0) {
                                    allow = null
                                    return@LaunchedEffect
                                }
                                val prof =
                                    withContext(Dispatchers.IO) {
                                        com.chris.m3usuite.data.obx.ObxStore
                                            .get(
                                                ctx,
                                            ).boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java)
                                            .get(profileId)
                                    }
                                allow = (prof?.type == "adult")
                                if (allow == false) nav.popBackStack()
                            }
                            if (allow == true) {
                                ProfileManagerScreen(
                                    onBack = { nav.popBackStack() },
                                    onLogo = {
                                        val current = nav.currentBackStackEntry?.destination?.route
                                        if (current != "library") {
                                            nav.navigateTopLevel("library?q=&qs=")
                                        }
                                    },
                                    onGlobalSearch = {
                                        nav.navigateTopLevel("library?qs=show")
                                    },
                                    onOpenSettings = {
                                        val current = nav.currentBackStackEntry?.destination?.route
                                        if (current != "settings") {
                                            nav.navigate("settings") { launchSingleTop = true }
                                        }
                                    },
                                )
                            }
                        }

                        // Xtream Cloudflare portal check (WebView)
                        composable("xt_cfcheck") {
                            XtreamPortalCheckScreen(onDone = { nav.popBackStack() })
                        }

                        // Telegram Log Screen
                        composable("telegram_log") {
                            TelegramLogScreen(
                                onBack = { nav.popBackStack() },
                            )
                        }

                        // Telegram Activity Feed Screen
                        composable("telegram_feed") {
                            TelegramActivityFeedScreen(
                                onItemClick = { mediaItem ->
                                    // Navigate to appropriate detail screen based on media type
                                    when (mediaItem.type) {
                                        "series" -> nav.navigate("series/${mediaItem.id}")
                                        "vod" -> nav.navigate("vod/${mediaItem.id}")
                                        "live" -> nav.navigate("live/${mediaItem.id}")
                                        else -> nav.navigate("vod/${mediaItem.id}")
                                    }
                                },
                                onBack = { nav.popBackStack() },
                            )
                        }

                        // Log Viewer Screen
                        composable("log_viewer") {
                            LogViewerScreen(
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }

                // Global back handling: pop if possible; otherwise consume (never close app via BACK)
                BackHandler {
                    val popped = nav.popBackStack()
                    if (!popped) {
                        // Do nothing – consume BACK at root. Home button remains the only way to leave the app.
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * Called when the user is about to leave the app (Home button, Recents, etc.).
     *
     * ════════════════════════════════════════════════════════════════════════════════
     * PHASE 7 – System PiP on Phone/Tablet
     * ════════════════════════════════════════════════════════════════════════════════
     *
     * This method triggers system PiP when:
     * - The device is NOT a TV (phones/tablets only)
     * - PlaybackSession is currently playing
     * - MiniPlayer is NOT visible (in-app mini player takes precedence)
     *
     * On TV devices:
     * - Do NOT call enterPictureInPictureMode()
     * - Let FireOS handle Home/Recents as it does today
     *
     * **Contract Reference:**
     * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 4.3
     */
    @Suppress("DEPRECATION")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Only trigger system PiP on non-TV devices (API < 31)
        // API >= 31 uses auto-enter PiP via setPictureInPictureParams
        if (Build.VERSION.SDK_INT < 31) {
            tryEnterSystemPip()
        }
    }

    /**
     * Attempt to enter system PiP mode if conditions are met.
     *
     * Conditions:
     * - NOT a TV device
     * - PlaybackSession is playing
     * - In-app MiniPlayer is NOT visible
     */
    private fun tryEnterSystemPip() {
        // Do NOT enter PiP on TV devices
        if (isTvDevice(this)) {
            return
        }

        // Check if playback is active
        val isPlaying = PlaybackSession.isPlaying.value
        if (!isPlaying) {
            return
        }

        // Check if in-app MiniPlayer is visible (takes precedence)
        val miniPlayerVisible = DefaultMiniPlayerManager.state.value.visible
        if (miniPlayerVisible) {
            return
        }

        // Enter system PiP mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = buildPictureInPictureParams()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                // PiP not supported or failed - ignore silently
                AppLog.log(
                    category = "pip",
                    level = AppLog.Level.WARN,
                    message = "Failed to enter system PiP: ${e.message}",
                )
            }
        }
    }

    /**
     * Build PictureInPictureParams for system PiP entry.
     *
     * For API >= 31, enables auto-enter PiP when leaving the app.
     * Uses 16:9 aspect ratio by default for video content.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun buildPictureInPictureParams(): PictureInPictureParams {
        val builder =
            PictureInPictureParams
                .Builder()
                .setAspectRatio(Rational(16, 9))

        // API 31+ supports auto-enter PiP
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(shouldAutoEnterPip())
        }

        return builder.build()
    }

    /**
     * Determine if auto-enter PiP should be enabled.
     *
     * Auto-enter is enabled when:
     * - NOT a TV device
     * - PlaybackSession is playing
     * - In-app MiniPlayer is NOT visible
     */
    private fun shouldAutoEnterPip(): Boolean {
        // Do NOT auto-enter PiP on TV devices
        if (isTvDevice(this)) {
            return false
        }

        // Only auto-enter if playing
        val isPlaying = PlaybackSession.isPlaying.value
        if (!isPlaying) {
            return false
        }

        // Do NOT auto-enter if in-app MiniPlayer is visible
        val miniPlayerVisible = DefaultMiniPlayerManager.state.value.visible
        if (miniPlayerVisible) {
            return false
        }

        return true
    }

    /**
     * Update PiP params when playback state changes.
     *
     * ════════════════════════════════════════════════════════════════════════════════
     * When to Call This Method
     * ════════════════════════════════════════════════════════════════════════════════
     *
     * This method should be called when playback state changes that affect PiP behavior:
     * - When playback starts/stops (`PlaybackSession.isPlaying` changes)
     * - When MiniPlayer visibility changes (`MiniPlayerState.visible`)
     * - Before entering/leaving activities that may trigger PiP
     *
     * **Why it's needed:**
     * On API 31+, `setAutoEnterEnabled(true)` only takes effect when the params are
     * actively set via `setPictureInPictureParams()`. Calling this method ensures
     * the system has the latest state for auto-enter decisions.
     *
     * **Callers:**
     * - Compose layer via `LaunchedEffect` observing playback state
     * - Activity lifecycle hooks if needed
     *
     * **Contract Reference:**
     * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 4.3
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = buildPictureInPictureParams()
                setPictureInPictureParams(params)
            } catch (_: Exception) {
                // Ignore if PiP not supported
            }
        }
    }

    /**
     * BUG 5 FIX: Handle PiP mode changes.
     *
     * ════════════════════════════════════════════════════════════════════════════════
     * This callback is invoked when the activity enters or exits PiP mode.
     * ════════════════════════════════════════════════════════════════════════════════
     *
     * On entering PiP:
     * - The video continues playing via PlaybackSession
     * - UI controls are hidden automatically by the system
     *
     * On leaving PiP:
     * - System bars are hidden again for immersive playback
     * - Player surface remains bound to PlaybackSession
     */
    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)

        if (!isInPictureInPictureMode) {
            // Exiting PiP: Restore immersive mode
            hideSystemBars()
        }

        AppLog.log(
            category = "pip",
            level = AppLog.Level.DEBUG,
            message = if (isInPictureInPictureMode) "Entered PiP mode" else "Exited PiP mode",
        )
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars()) // <- Tippfehler behoben
        c.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
