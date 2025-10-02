package com.chris.m3usuite

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.metrics.performance.JankStats
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.prefs.Keys
import com.chris.m3usuite.navigation.popUpToStartDestination
import com.chris.m3usuite.navigation.navigateTopLevel
import com.chris.m3usuite.player.InternalPlayerScreen   // <- korrektes Paket (s. Schritt A)
import com.chris.m3usuite.ui.screens.LibraryScreen
import com.chris.m3usuite.ui.home.StartScreen
import com.chris.m3usuite.ui.screens.SettingsScreen
import com.chris.m3usuite.ui.screens.LiveDetailScreen
import com.chris.m3usuite.ui.screens.PlaylistSetupScreen
import com.chris.m3usuite.ui.screens.SeriesDetailScreen
import com.chris.m3usuite.ui.screens.XtreamPortalCheckScreen
import com.chris.m3usuite.ui.screens.VodDetailScreen
import com.chris.m3usuite.ui.auth.ProfileGate
import com.chris.m3usuite.ui.profile.ProfileManagerScreen
import com.chris.m3usuite.ui.theme.AppTheme
import com.chris.m3usuite.ui.skin.M3UTvSkin
import com.chris.m3usuite.work.SchedulingGateway
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import android.app.Activity
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.first

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
                com.chris.m3usuite.metrics.JankReporter.record(route, frameData.isJank, frameData.frameDurationUiNanos)
            }
        }

        setContent {
            M3UTvSkin {
            AppTheme {
                val nav = rememberNavController()
                // Global nav debug listener (switchable via Settings)
                androidx.compose.runtime.DisposableEffect(nav) {
                    val listener = androidx.navigation.NavController.OnDestinationChangedListener { controller, destination, arguments ->
                        val from = controller.previousBackStackEntry?.destination?.route
                            ?: controller.previousBackStackEntry?.destination?.displayName
                        val to = destination.route ?: destination.displayName
                        com.chris.m3usuite.core.debug.GlobalDebug.logNavigation(from, to)
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
                        com.chris.m3usuite.core.http.RequestHeadersProvider.collect(store)
                    }
                }
                // Keep TrafficLogger state in sync with Settings (persists across restarts)
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        store.httpLogEnabled.collect { enabled ->
                            com.chris.m3usuite.core.http.TrafficLogger.setEnabled(enabled)
                        }
                    }
                }
                // Keep GlobalDebug in sync with Settings
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        store.globalDebugEnabled.collect { on ->
                            com.chris.m3usuite.core.debug.GlobalDebug.setEnabled(on)
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
                                val cfg = com.chris.m3usuite.core.xtream.XtreamConfig.fromM3uUrl(m3uUrl)
                                if (cfg != null) {
                                    store.setXtHost(cfg.host)
                                    store.setXtPort(cfg.port)
                                    store.setXtUser(cfg.username)
                                    store.setXtPass(cfg.password)
                                    cfg.liveExtPrefs.firstOrNull()?.let { store.setXtOutput(it) }
                                    store.setXtPortVerified(true)
                                    if (store.epgUrl.first().isBlank()) {
                                        store.set(com.chris.m3usuite.prefs.Keys.EPG_URL, "${cfg.portalBase}/xmltv.php?username=${cfg.username}&password=${cfg.password}")
                                    }
                                } else {
                                    // Kein Port in M3U: Discovery verwenden (Xtream bevorzugen)
                                    val u = android.net.Uri.parse(m3uUrl)
                                    val scheme = (u.scheme ?: "http").lowercase()
                                    val host = (u.host ?: "")
                                    val user = u.getQueryParameter("username")
                                    val pass = u.getQueryParameter("password")
                                    if (host.isNotBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank()) {
                                        val http = com.chris.m3usuite.core.http.HttpClientFactory.create(this@MainActivity, store)
                                        val capStore = com.chris.m3usuite.core.xtream.ProviderCapabilityStore(this@MainActivity)
                                        val portStore = com.chris.m3usuite.core.xtream.EndpointPortStore(this@MainActivity)
                                        val discoverer = com.chris.m3usuite.core.xtream.CapabilityDiscoverer(http, capStore, portStore)
                                        val caps = discoverer.discoverAuto(scheme, host, user, pass, null, forceRefresh = false)
                                        val bu = android.net.Uri.parse(caps.baseUrl)
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
                                                store.set(com.chris.m3usuite.prefs.Keys.EPG_URL, "$rs://$rh:$rp/xmltv.php?username=$user&password=$pass")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(xtHost, xtUser, xtPass, xtPort) {
                    if (xtHost.isBlank() || xtUser.isBlank() || xtPass.isBlank()) return@LaunchedEffect
                    if (!apiEnabled) return@LaunchedEffect
                    // Start background import so index builds even if the UI recomposes or route changes.
                    // Immediately ensure full header lists (heads-only delta) for VOD/Series at app start; skip Live to stay light.
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store).importDelta(deleteOrphans = false, includeLive = false) }
                    }
                    // Also schedule a background one-shot (heads-only again if needed; Live remains off here)
                    com.chris.m3usuite.work.XtreamDeltaImportWorker.triggerOnce(ctx, includeLive = false, vodLimit = 0, seriesLimit = 0)
                    // Schedule Live heads-only to come in later (delayed), so startup stays light
                    com.chris.m3usuite.work.XtreamDeltaImportWorker.triggerOnceDelayedLive(ctx, delayMinutes = 5)
                }

                // EPG periodic refresh removed; lazy on-demand prefetch keeps visible/favorites fresh

                NavHost(navController = nav, startDestination = startDestination) {
                    composable("setup") {
                        PlaylistSetupScreen(
                            onDone = {
                                nav.navigate("library?q=&qs=") {
                                    popUpTo("setup") { inclusive = true }
                                }
                            }
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
                        arguments = listOf(
                            navArgument("q") { type = NavType.StringType; defaultValue = "" },
                            navArgument("qs") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { back ->
                        val q = back.arguments?.getString("q").orEmpty()
                        val qs = back.arguments?.getString("qs").orEmpty()
                        val openDlg = qs == "show"
                        StartScreen(
                            navController = nav,
                            openLive   = { id -> nav.navigate("live/$id") },
                            openVod    = { id -> nav.navigate("vod/$id") },
                            openSeries = { id -> nav.navigate("series/$id") },
                            initialSearch = q.ifBlank { null },
                            openSearchOnStart = openDlg
                        )
                    }

                    // Full browser (previous LibraryScreen)
                    composable("browse") {
                        LibraryScreen(
                            navController = nav,
                            openLive   = { id -> nav.navigate("live/$id") },
                            openVod    = { id -> nav.navigate("vod/$id") },
                            openSeries = { id -> nav.navigate("series/$id") }
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
                                val start   = startMs ?: -1L
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
                            }
                        )
                    }

                    // Serien-Details – mit Lambda für internen Player (Episoden)
                    composable("series/{id}") { back ->
                        val id = back.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                        SeriesDetailScreen(
                            id = id,
                            openInternal = { playUrl, startMs, seriesId, season, episodeNum, episodeId, mime ->
                                val encoded = Uri.encode(playUrl)
                                val start   = startMs ?: -1L
                                val epId    = episodeId ?: -1
                                val mimeArg = mime?.let { Uri.encode(it) } ?: ""
                                nav.navigate("player?url=$encoded&type=series&seriesId=$seriesId&season=$season&episodeNum=$episodeNum&episodeId=$epId&startMs=$start&mime=$mimeArg")
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
                            }
                        )
                    }

                    // Interner ExoPlayer (Media3)
                    composable(
                        route = "player?url={url}&type={type}&mediaId={mediaId}&episodeId={episodeId}&seriesId={seriesId}&season={season}&episodeNum={episodeNum}&startMs={startMs}&mime={mime}&origin={origin}&cat={cat}&prov={prov}",
                        arguments = listOf(
                            navArgument("url")       { type = NavType.StringType },
                            navArgument("type")      { type = NavType.StringType; defaultValue = "vod" },
                            navArgument("mediaId")   { type = NavType.LongType;   defaultValue = -1L },
                            navArgument("episodeId") { type = NavType.IntType;    defaultValue = -1 },
                            navArgument("seriesId")  { type = NavType.IntType;    defaultValue = -1 },
                            navArgument("season")    { type = NavType.IntType;    defaultValue = -1 },
                            navArgument("episodeNum"){ type = NavType.IntType;    defaultValue = -1 },
                            navArgument("startMs")   { type = NavType.LongType;   defaultValue = -1L },
                            navArgument("mime")      { type = NavType.StringType; defaultValue = "" },
                            navArgument("origin")    { type = NavType.StringType; defaultValue = "" },
                            navArgument("cat")       { type = NavType.StringType; defaultValue = "" },
                            navArgument("prov")      { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { back ->
                        val rawUrl   = back.arguments?.getString("url").orEmpty()
                        val url      = URLDecoder.decode(rawUrl, StandardCharsets.UTF_8.name())
                        val type     = back.arguments?.getString("type") ?: "vod"
                        val mediaId  = back.arguments?.getLong("mediaId")?.takeIf { it >= 0 }
                        val episodeId= back.arguments?.getInt("episodeId")?.takeIf { it >= 0 }
                        val seriesId = back.arguments?.getInt("seriesId")?.takeIf { it >= 0 }
                        val season   = back.arguments?.getInt("season")?.takeIf { it >= 0 }
                        val episodeNum = back.arguments?.getInt("episodeNum")?.takeIf { it >= 0 }
                        val startMs  = back.arguments?.getLong("startMs")?.takeIf { it >= 0 }
                        val rawMime  = back.arguments?.getString("mime").orEmpty()
                        val mime     = rawMime.takeIf { it.isNotEmpty() }?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
                        val origin   = back.arguments?.getString("origin").orEmpty()
                        val cat      = back.arguments?.getString("cat").orEmpty()
                        val prov     = back.arguments?.getString("prov").orEmpty()

                        InternalPlayerScreen(
                            url = url,
                            type = type,
                            mediaId = mediaId,
                            episodeId = episodeId,
                            seriesId = seriesId,
                            season = season,
                            episodeNum = episodeNum,
                            startPositionMs = startMs,
                            mimeType = mime,
                            onExit = { nav.popBackStack() },
                            originLiveLibrary = (origin == "lib"),
                            liveCategoryHint = cat.ifBlank { null },
                            liveProviderHint = prov.ifBlank { null }
                        )
                    }

                    // Settings (permissions)
                    composable("settings") {
                        val profileId = store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L).value
                        LaunchedEffect(profileId) {
                            val perms = com.chris.m3usuite.data.repo.PermissionRepository(this@MainActivity, store).current()
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
                            }
                        )
                    }

                    composable("profiles") {
                        // Require adult to open profile manager. Wait for a valid profileId before deciding.
                        val profileId = store.currentProfileId.collectAsStateWithLifecycle(initialValue = -1L).value
                        var allow: Boolean? by remember { mutableStateOf(null) }
                        LaunchedEffect(profileId) {
                            if (profileId <= 0) { allow = null; return@LaunchedEffect }
                            val prof = withContext(Dispatchers.IO) { com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java).get(profileId) }
                            allow = (prof?.type == "adult")
                            if (allow == false) nav.popBackStack()
                        }
                        if (allow == true) ProfileManagerScreen(
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
                            }
                        )
                    }

                    // Xtream Cloudflare portal check (WebView)
                    composable("xt_cfcheck") {
                        XtreamPortalCheckScreen(onDone = { nav.popBackStack() })
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
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())   // <- Tippfehler behoben
        c.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
