package com.fishit.player.v2.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fishit.player.feature.devtools.DevToolsScreen
import com.fishit.player.feature.home.debug.DebugPlaybackScreen
import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.ResumeManager
import com.fishit.player.v2.ui.debug.DebugSkeletonScreen

/**
 * Top-level navigation host for FishIT Player v2.
 *
 * Routes will be expanded in later phases to include:
 * - Home
 * - Library
 * - Live
 * - Telegram Media
 * - Audiobooks
 * - Settings
 * - Player
 */
@Composable
fun AppNavHost(
    resumeManager: ResumeManager,
    kidsPlaybackGate: KidsPlaybackGate
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.DEVTOOLS
    ) {
        composable(Routes.DEBUG_SKELETON) {
            DebugSkeletonScreen()
        }
        
        composable(Routes.DEBUG_PLAYBACK) {
            DebugPlaybackScreen(
                resumeManager = resumeManager,
                kidsPlaybackGate = kidsPlaybackGate,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.DEVTOOLS) {
            DevToolsScreen()
        }
    }
}

/**
 * Route constants for navigation.
 */
object Routes {
    const val DEBUG_SKELETON = "debug_skeleton"
    const val DEBUG_PLAYBACK = "debug_playback"
    const val DEVTOOLS = "devtools"
    
    // Future routes (Phase 2+)
    // const val HOME = "home"
    // const val LIBRARY = "library"
    // const val LIVE = "live"
    // const val TELEGRAM = "telegram"
    // const val AUDIOBOOKS = "audiobooks"
    // const val SETTINGS = "settings"
    // const val PLAYER = "player"
}
