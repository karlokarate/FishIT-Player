package com.chris.m3usuite.tv.input

import androidx.navigation.NavHostController
import com.chris.m3usuite.player.miniplayer.MiniPlayerManager

/**
 * Navigator that handles EXIT_TO_HOME action from the TV input system.
 *
 * This component bridges the TV input system with the navigation layer,
 * implementing the "double BACK → Exit to Home" behavior specified in
 * GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Group 3: Navigation & Backstack Stability
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * **EXIT_TO_HOME Behavior with MiniPlayer:**
 * - When EXIT_TO_HOME is triggered (double BACK):
 *   - Navigate to the Start/Home route
 *   - Clear the back stack
 *   - MiniPlayer remains visible if playback is active (per contract)
 *
 * This ensures users can quickly return home while playback continues in MiniPlayer.
 *
 * ## Usage
 *
 * ```kotlin
 * val navigator = DoubleBackNavigator(
 *     navController = navController,
 *     startRoute = "start",
 *     miniPlayerManager = DefaultMiniPlayerManager
 * )
 *
 * // Wire to TvInputController action listener
 * controller.actionListener = TvActionListener { action ->
 *     when (action) {
 *         TvAction.EXIT_TO_HOME -> navigator.navigateToHome()
 *         else -> // handle other actions
 *     }
 * }
 * ```
 *
 * Phase 6 Task 7:
 * - Implement actual navigation behavior for EXIT_TO_HOME (double BACK)
 *
 * Phase 8 Task 4:
 * - MiniPlayer awareness: MiniPlayer remains visible during EXIT_TO_HOME
 * - Backstack cleanup: Ensures no ghost player routes
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 5
 * - GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md
 *
 * @param navController The NavHostController for navigation
 * @param startRoute The route name for the home/start screen (default: "library?q=&qs=")
 * @param miniPlayerManager Optional MiniPlayerManager for visibility awareness
 */
class DoubleBackNavigator(
    private val navController: NavHostController,
    private val startRoute: String = DEFAULT_START_ROUTE,
    private val miniPlayerManager: MiniPlayerManager? = null,
) {
    companion object {
        /**
         * Default route for the start/home screen.
         *
         * Phase 8: Updated to use the library route which is the actual home screen
         * in the current navigation graph.
         */
        const val DEFAULT_START_ROUTE = "library?q=&qs="

        /** Legacy route name (for compatibility) */
        const val LEGACY_START_ROUTE = "start"
    }

    /**
     * Navigate to the home/start screen.
     *
     * This clears the back stack and navigates to the start route,
     * implementing the EXIT_TO_HOME behavior for double-BACK.
     *
     * ════════════════════════════════════════════════════════════════════════════════
     * PHASE 8 – MiniPlayer Behavior on EXIT_TO_HOME
     * ════════════════════════════════════════════════════════════════════════════════
     *
     * When EXIT_TO_HOME is triggered:
     * - Navigate to the Start/Home route
     * - Clear the back stack (no ghost player routes)
     * - **MiniPlayer REMAINS VISIBLE** if playback is active
     *
     * This matches the contract behavior where MiniPlayer continues to show
     * ongoing playback even when returning to home.
     *
     * **Contract Reference:**
     * - INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 5.1
     *
     * @return True if navigation was successful, false otherwise
     */
    fun navigateToHome(): Boolean =
        try {
            navController.navigate(startRoute) {
                // Clear the entire back stack up to and including start
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                }
                // Avoid multiple copies of home on the stack
                launchSingleTop = true
            }
            // NOTE: MiniPlayer visibility is NOT modified here.
            // If playback is active, MiniPlayer remains visible per contract.
            // This is intentional - users can continue watching while at home.
            true
        } catch (e: Exception) {
            // Navigation failed - likely no valid route
            false
        }

    /**
     * Check if the current destination is the home screen.
     *
     * @return True if already at home, false otherwise
     */
    fun isAtHome(): Boolean {
        val currentRoute = navController.currentDestination?.route
        return currentRoute == startRoute ||
            currentRoute?.startsWith("library") == true ||
            currentRoute == LEGACY_START_ROUTE
    }

    /**
     * Check if MiniPlayer is currently visible.
     *
     * @return True if MiniPlayer is visible, false otherwise or if manager is not set
     */
    fun isMiniPlayerVisible(): Boolean = miniPlayerManager?.state?.value?.visible == true
}

/**
 * Extension function to handle EXIT_TO_HOME action.
 *
 * Call this from the TvActionListener when EXIT_TO_HOME is dispatched.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – EXIT_TO_HOME with MiniPlayer Awareness
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * **Behavior:**
 * - Single BACK: Normal behavior (close overlay/navigate up) - NOT handled here
 * - Double BACK within threshold: EXIT_TO_HOME → home route
 * - MiniPlayer remains visible if playback is active
 * - No duplicate player routes after navigation
 *
 * **Contract Reference:**
 * - GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md: "Global double BACK = Exit to Home"
 * - INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 5.1
 *
 * @param navigator The DoubleBackNavigator to use
 * @return True if handled (navigated to home), false if already at home or not EXIT_TO_HOME
 */
fun TvAction.handleExitToHome(navigator: DoubleBackNavigator): Boolean {
    if (this != TvAction.EXIT_TO_HOME) return false

    // Don't navigate if already at home
    if (navigator.isAtHome()) return false

    return navigator.navigateToHome()
}
