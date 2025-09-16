package com.chris.m3usuite

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.prefs.Keys
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
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    @OptIn(UnstableApi::class)
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

                // Kick fish spin on every route change (single fast spin), lifecycle-aware
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                LaunchedEffect(nav, lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        var last: String? = null
                        nav.currentBackStackEntryFlow.collect { entry ->
                            val route = entry.destination.route
                            if (route != null && route != last) {
                                com.chris.m3usuite.ui.fx.FishSpin.kickOnce()
                                last = route
                            }
                        }
                    }
                }

                val ctx = LocalContext.current
                val store = remember(ctx) { SettingsStore(ctx) }
                // React to Xtream creds becoming available at runtime (Settings/Setup)
                val xtHost by store.xtHost.collectAsStateWithLifecycle(initialValue = "")
                val xtUser by store.xtUser.collectAsStateWithLifecycle(initialValue = "")
                val xtPass by store.xtPass.collectAsStateWithLifecycle(initialValue = "")
                // Keep HTTP header snapshot updated globally for OkHttp/Coil, lifecycle-aware
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        com.chris.m3usuite.core.http.RequestHeadersProvider.collect(store)
                    }
                }
                
                // Track app start time for new-episode detection
                LaunchedEffect(Unit) { store.setLastAppStartMs(System.currentTimeMillis()) }

                // DataStore-Flow beobachten
                val m3uUrl = store.m3uUrl.collectAsStateWithLifecycle(initialValue = "").value

                // Startziel: auch ohne M3U kann die App starten (Einstellungen später setzen)
                val startDestination = "gate"
                // Appstart-Loading nur, wenn Quellen vorhanden sind und Import angestoßen wird
                LaunchedEffect(m3uUrl) {
                    if (m3uUrl.isNotBlank()) com.chris.m3usuite.ui.fx.FishSpin.setLoading(true) else com.chris.m3usuite.ui.fx.FishSpin.setLoading(false)
                }

                // Wenn M3U vorhanden aber Xtream noch nicht konfiguriert (z. B. nach App-Reinstall via Backup):
                // automatisch aus der M3U ableiten und direkt die Worker planen.
                LaunchedEffect(m3uUrl) {
                    if (m3uUrl.isNotBlank()) {
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
                        SchedulingGateway.scheduleAll(this@MainActivity)
                        // Sofortigen Refresh als OneTime-Work einreihen
                        SchedulingGateway.triggerXtreamRefreshNow(this@MainActivity)
                        // Zusätzlich: Sofortiges Seeding der Listen (schnell, ohne Details) für direkte UI-Sichtbarkeit
                        runCatching {
                            withContext(Dispatchers.IO) {
                                com.chris.m3usuite.data.repo.XtreamObxRepository(this@MainActivity, store)
                                    .seedListsQuick(limitPerKind = 200, forceRefreshDiscovery = true)
                            }
                        }
                        // Immediate once
                        runCatching {
                            val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
                            SchedulingGateway.refreshFavoritesEpgNow(this@MainActivity, aggressive = aggressive)
                        }
                    }
                }

                // Xtream-only onboarding (no M3U required): if creds already present, kick Discovery→Fetch immediately
                LaunchedEffect(Unit) {
                    val hasXt = withContext(Dispatchers.IO) { store.hasXtream() }
                    if (hasXt) {
                        SchedulingGateway.scheduleAll(this@MainActivity)
                        SchedulingGateway.triggerXtreamRefreshNow(this@MainActivity)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                com.chris.m3usuite.data.repo.XtreamObxRepository(this@MainActivity, store)
                                    .seedListsQuick(limitPerKind = 200, forceRefreshDiscovery = true)
                            }
                        }
                    }
                }

                // Also run onboarding hook whenever Xtream creds get populated later
                LaunchedEffect(xtHost, xtUser, xtPass) {
                    if (xtHost.isNotBlank() && xtUser.isNotBlank() && xtPass.isNotBlank()) {
                        SchedulingGateway.scheduleAll(this@MainActivity)
                        SchedulingGateway.triggerXtreamRefreshNow(this@MainActivity)
                        runCatching {
                            withContext(Dispatchers.IO) {
                                com.chris.m3usuite.data.repo.XtreamObxRepository(this@MainActivity, store)
                                    .seedListsQuick(limitPerKind = 200, forceRefreshDiscovery = true)
                            }
                        }
                    }
                }

                // EPG periodic refresh removed; lazy on-demand prefetch keeps visible/favorites fresh

                NavHost(navController = nav, startDestination = startDestination) {
                    composable("setup") {
                        PlaylistSetupScreen(
                            onDone = {
                                nav.navigate("library") {
                                    popUpTo("setup") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("gate") {
                        ProfileGate(onEnter = {
                            nav.navigate("library") {
                                popUpTo("gate") { inclusive = true }
                            }
                        })
                    }

                    composable("library") {
                        StartScreen(
                            navController = nav,
                            openLive   = { id -> nav.navigate("live/$id") },
                            openVod    = { id -> nav.navigate("vod/$id") },
                            openSeries = { id -> nav.navigate("series/$id") }
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
                                nav.navigate("library") { launchSingleTop = true }
                            }
                        })
                    }

                    // VOD-Details – mit Lambda für internen Player
                    composable("vod/{id}") { back ->
                        val id = back.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                        VodDetailScreen(
                            id = id,
                            openInternal = { url, startMs ->
                                val encoded = Uri.encode(url)
                                val start   = startMs ?: -1L
                                nav.navigate("player?url=$encoded&type=vod&mediaId=$id&startMs=$start")
                            },
                            onLogo = {
                                val current = nav.currentBackStackEntry?.destination?.route
                                if (current != "library") {
                                    nav.navigate("library") { launchSingleTop = true }
                                }
                            }
                        )
                    }

                    // Serien-Details – mit Lambda für internen Player (Episoden)
                    composable("series/{id}") { back ->
                        val id = back.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                        SeriesDetailScreen(
                            id = id,
                            // new: pass series composite keys instead of episodeId
                            openInternal = { playUrl, startMs, seriesId, season, episodeNum ->
                                val encoded = Uri.encode(playUrl)
                                val start   = startMs ?: -1L
                                nav.navigate("player?url=$encoded&type=series&seriesId=$seriesId&season=$season&episodeNum=$episodeNum&startMs=$start")
                            },
                            onLogo = {
                                val current = nav.currentBackStackEntry?.destination?.route
                                if (current != "library") {
                                    nav.navigate("library") { launchSingleTop = true }
                                }
                            }
                        )
                    }

                    // Interner ExoPlayer (Media3)
                    composable(
                        route = "player?url={url}&type={type}&mediaId={mediaId}&episodeId={episodeId}&seriesId={seriesId}&season={season}&episodeNum={episodeNum}&startMs={startMs}",
                        arguments = listOf(
                            navArgument("url")       { type = NavType.StringType },
                            navArgument("type")      { type = NavType.StringType; defaultValue = "vod" },
                            navArgument("mediaId")   { type = NavType.LongType;   defaultValue = -1L },
                            navArgument("episodeId") { type = NavType.IntType;    defaultValue = -1 },
                            navArgument("seriesId")  { type = NavType.IntType;    defaultValue = -1 },
                            navArgument("season")    { type = NavType.IntType;    defaultValue = -1 },
                            navArgument("episodeNum"){ type = NavType.IntType;    defaultValue = -1 },
                            navArgument("startMs")   { type = NavType.LongType;   defaultValue = -1L }
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

                        InternalPlayerScreen(
                            url = url,
                            type = type,
                            mediaId = mediaId,
                            episodeId = episodeId,
                            seriesId = seriesId,
                            season = season,
                            episodeNum = episodeNum,
                            startPositionMs = startMs,
                            onExit = { nav.popBackStack() }
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
                            onBack = {
                                val current = nav.currentBackStackEntry?.destination?.route
                                if (current != "library") {
                                    nav.navigate("library") { launchSingleTop = true }
                                }
                            },
                            onOpenProfiles = { nav.navigate("profiles") },
                            onOpenGate = {
                                nav.navigate("gate") {
                                    popUpTo("library") { inclusive = false }
                                }
                            },
                            onOpenXtreamCfCheck = { nav.navigate("xt_cfcheck") }
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
                                    nav.navigate("library") { launchSingleTop = true }
                                }
                            }
                        )
                    }

                    // Xtream Cloudflare portal check (WebView)
                    composable("xt_cfcheck") {
                        XtreamPortalCheckScreen(onDone = { nav.popBackStack() })
                    }
                }

                // Global back handling: pop if possible, otherwise move task to back
                BackHandler {
                    val popped = nav.popBackStack()
                    if (!popped) {
                        (ctx as? Activity)?.moveTaskToBack(false)
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
