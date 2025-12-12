package com.fishit.player.v2.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.ui.theme.FishTheme
import com.fishit.player.feature.detail.UnifiedDetailEvent
import com.fishit.player.feature.detail.ui.DetailScreen
import com.fishit.player.feature.home.HomeScreen
import com.fishit.player.feature.home.debug.DebugPlaybackScreen
import com.fishit.player.feature.onboarding.StartScreen
import com.fishit.player.feature.settings.DebugScreen
import com.fishit.player.internal.source.PlaybackSourceResolver
import com.fishit.player.nextlib.NextlibCodecConfigurator
import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.ResumeManager
import com.fishit.player.v2.CatalogSyncBootstrap
import com.fishit.player.v2.ui.debug.DebugSkeletonScreen
import kotlinx.coroutines.flow.collectLatest

/**
 * Top-level navigation host for FishIT Player v2.
 *
 * Navigation flow:
 * Start -> Home -> Detail -> Player
 *               -> Debug -> DebugPlayback (test player)
 *               -> Settings
 */
@Composable
fun AppNavHost(
    resumeManager: ResumeManager,
    kidsPlaybackGate: KidsPlaybackGate,
    sourceResolver: PlaybackSourceResolver,
    codecConfigurator: NextlibCodecConfigurator,
    catalogSyncBootstrap: CatalogSyncBootstrap,
) {
    val navController = rememberNavController()

    FishTheme {
        LaunchedEffect(navController, catalogSyncBootstrap) {
            navController.currentBackStackEntryFlow.collectLatest { backStackEntry ->
                if (backStackEntry.destination.route == Routes.HOME) {
                    catalogSyncBootstrap.start()
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = Routes.START
        ) {
            // Start/Onboarding Screen
            composable(Routes.START) {
                StartScreen(
                    onContinue = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.START) { inclusive = true }
                        }
                    }
                )
            }

            // Home Screen
            composable(Routes.HOME) {
                HomeScreen(
                    onItemClick = { item ->
                        navController.navigate(
                            Routes.detail(
                                mediaId = item.navigationId,
                                sourceType = item.navigationSource.name
                            )
                        )
                    },
                    onSettingsClick = {
                        // TODO: Navigate to Settings
                    },
                    onDebugClick = {
                        navController.navigate(Routes.DEBUG)
                    }
                )
            }

            // Detail Screen
            composable(
                route = Routes.DETAIL_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_MEDIA_ID) { type = NavType.StringType },
                    navArgument(Routes.ARG_SOURCE_TYPE) { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getString(Routes.ARG_MEDIA_ID) ?: return@composable
                val sourceTypeName = backStackEntry.arguments?.getString(Routes.ARG_SOURCE_TYPE) ?: return@composable
                val sourceType = try {
                    SourceType.valueOf(sourceTypeName)
                } catch (e: Exception) {
                    SourceType.UNKNOWN
                }

                DetailScreen(
                    mediaId = mediaId,
                    sourceType = sourceType,
                    onBack = { navController.popBackStack() },
                    onPlayback = { event ->
                        // TODO: Navigate to player with playback context
                        // navController.navigate(Routes.player(event.canonicalId.value, event.source.sourceId))
                    }
                )
            }

            // Debug Screen (settings/diagnostics)
            composable(Routes.DEBUG) {
                DebugScreen(
                    onBack = { navController.popBackStack() },
                    onDebugPlayback = { navController.navigate(Routes.DEBUG_PLAYBACK) }
                )
            }

            // Debug Playback Screen (test player with Big Buck Bunny)
            composable(Routes.DEBUG_PLAYBACK) {
                DebugPlaybackScreen(
                    sourceResolver = sourceResolver,
                    resumeManager = resumeManager,
                    kidsPlaybackGate = kidsPlaybackGate,
                    codecConfigurator = codecConfigurator,
                    onBack = { navController.popBackStack() }
                )
            }

            // Legacy Debug Skeleton (for reference)
            composable(Routes.DEBUG_SKELETON) {
                DebugSkeletonScreen()
            }
        }
    }
}

/**
 * Route constants for navigation.
 */
object Routes {
    // Main routes
    const val START = "start"
    const val HOME = "home"
    const val DEBUG = "debug"
    const val DEBUG_PLAYBACK = "debug_playback"
    const val DEBUG_SKELETON = "debug_skeleton"

    // Detail route with arguments
    const val ARG_MEDIA_ID = "mediaId"
    const val ARG_SOURCE_TYPE = "sourceType"
    const val DETAIL_PATTERN = "detail/{$ARG_MEDIA_ID}/{$ARG_SOURCE_TYPE}"

    fun detail(mediaId: String, sourceType: String): String {
        return "detail/$mediaId/$sourceType"
    }

    // Future routes
    // const val LIBRARY = "library"
    // const val LIVE = "live"
    // const val TELEGRAM = "telegram"
    // const val AUDIOBOOKS = "audiobooks"
    // const val SETTINGS = "settings"
    // const val PLAYER = "player/{canonicalId}/{sourceId}"
}
