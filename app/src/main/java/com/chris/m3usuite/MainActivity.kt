package com.chris.m3usuite

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.player.InternalPlayerScreen   // <- korrektes Paket (s. Schritt A)
import com.chris.m3usuite.ui.screens.LibraryScreen
import com.chris.m3usuite.ui.screens.SettingsScreen
import com.chris.m3usuite.ui.screens.LiveDetailScreen
import com.chris.m3usuite.ui.screens.PlaylistSetupScreen
import com.chris.m3usuite.ui.screens.SeriesDetailScreen
import com.chris.m3usuite.ui.screens.VodDetailScreen
import com.chris.m3usuite.ui.auth.ProfileGate
import com.chris.m3usuite.ui.profile.ProfileManagerScreen
import com.chris.m3usuite.ui.theme.AppTheme
import com.chris.m3usuite.ui.skin.M3UTvSkin
import com.chris.m3usuite.work.XtreamEnrichmentWorker
import com.chris.m3usuite.work.XtreamRefreshWorker
import com.chris.m3usuite.work.ScreenTimeResetWorker
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import com.chris.m3usuite.data.db.DbProvider
import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Vollbild / Immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        setContent {
            M3UTvSkin {
            AppTheme {
                val nav = rememberNavController()

                val ctx = LocalContext.current
                val store = remember(ctx) { SettingsStore(ctx) }

                // DataStore-Flow beobachten
                val m3uUrl = store.m3uUrl.collectAsState(initial = "").value

                // Startziel abhängig von gespeicherter URL
                val startDestination = if (m3uUrl.isBlank()) "setup" else "gate"

                // Worker für Refresh/Enrichment planen, sobald URL vorhanden
                LaunchedEffect(m3uUrl) {
                    if (m3uUrl.isNotBlank()) {
                        XtreamRefreshWorker.schedule(this@MainActivity)
                        XtreamEnrichmentWorker.schedule(this@MainActivity)
                        ScreenTimeResetWorker.schedule(this@MainActivity)
                    }
                }

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
                        LibraryScreen(
                            navController = nav,
                            openLive   = { id -> nav.navigate("live/$id") },
                            openVod    = { id -> nav.navigate("vod/$id") },
                            openSeries = { id -> nav.navigate("series/$id") }
                        )
                    }

                    composable("live/{id}") { back ->
                        val id = back.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                        LiveDetailScreen(id)
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
                            }
                        )
                    }

                    // Serien-Details – mit Lambda für internen Player (Episoden)
                    composable("series/{id}") { back ->
                        val id = back.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                        SeriesDetailScreen(
                            id = id,
                            // Name muss zur Signatur in SeriesDetailScreen passen (openInternal)
                            openInternal = { playUrl, startMs, episodeId ->
                                val encoded = Uri.encode(playUrl)
                                val start   = startMs ?: -1L
                                nav.navigate("player?url=$encoded&type=series&episodeId=$episodeId&startMs=$start")
                            }
                        )
                    }

                    // Interner ExoPlayer (Media3)
                    composable(
                        route = "player?url={url}&type={type}&mediaId={mediaId}&episodeId={episodeId}&startMs={startMs}",
                        arguments = listOf(
                            navArgument("url")       { type = NavType.StringType },
                            navArgument("type")      { type = NavType.StringType; defaultValue = "vod" },
                            navArgument("mediaId")   { type = NavType.LongType;   defaultValue = -1L },
                            navArgument("episodeId") { type = NavType.IntType;    defaultValue = -1 },
                            navArgument("startMs")   { type = NavType.LongType;   defaultValue = -1L }
                        )
                    ) { back ->
                        val rawUrl   = back.arguments?.getString("url").orEmpty()
                        val url      = URLDecoder.decode(rawUrl, StandardCharsets.UTF_8.name())
                        val type     = back.arguments?.getString("type") ?: "vod"
                        val mediaId  = back.arguments?.getLong("mediaId")?.takeIf { it >= 0 }
                        val episodeId= back.arguments?.getInt("episodeId")?.takeIf { it >= 0 }
                        val startMs  = back.arguments?.getLong("startMs")?.takeIf { it >= 0 }

                        InternalPlayerScreen(
                            url = url,
                            type = type,
                            mediaId = mediaId,
                            episodeId = episodeId,
                            startPositionMs = startMs,
                            onExit = { nav.popBackStack() }
                        )
                    }

                    // Settings (nur Adult; Kids werden zurück navigiert)
                    composable("settings") {
                        val dbLocal = remember { DbProvider.get(ctx) }
                        val profileId = store.currentProfileId.collectAsState(initial = -1L).value
                        LaunchedEffect(profileId) {
                            val prof = if (profileId > 0) withContext(Dispatchers.IO) { dbLocal.profileDao().byId(profileId) } else null
                            val isKid = prof?.type == "kid"
                            if (isKid) {
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
                            }
                        )
                    }

                    composable("profiles") {
                        ProfileManagerScreen(onBack = { nav.popBackStack() })
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
