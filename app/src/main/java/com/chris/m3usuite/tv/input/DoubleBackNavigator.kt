package com.chris.m3usuite.tv.input

import androidx.navigation.NavHostController

/**
 * Navigator that handles EXIT_TO_HOME action from the TV input system.
 *
 * This component bridges the TV input system with the navigation layer,
 * implementing the "double BACK → Exit to Home" behavior specified in
 * GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md.
 *
 * ## Usage
 *
 * ```kotlin
 * val navigator = DoubleBackNavigator(navController, startRoute = "start")
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
 * @param navController The NavHostController for navigation
 * @param startRoute The route name for the home/start screen (default: "start")
 */
class DoubleBackNavigator(
    private val navController: NavHostController,
    private val startRoute: String = DEFAULT_START_ROUTE,
) {
    companion object {
        /** Default route for the start/home screen */
        const val DEFAULT_START_ROUTE = "start"
    }

    /**
     * Navigate to the home/start screen.
     *
     * This clears the back stack and navigates to the start route,
     * implementing the EXIT_TO_HOME behavior for double-BACK.
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
        return currentRoute == startRoute
    }
}

/**
 * Extension function to handle EXIT_TO_HOME action.
 *
 * Call this from the TvActionListener when EXIT_TO_HOME is dispatched.
 *
 * @param navigator The DoubleBackNavigator to use
 * @return True if handled (navigated to home), false if already at home
 */
fun TvAction.handleExitToHome(navigator: DoubleBackNavigator): Boolean {
    if (this != TvAction.EXIT_TO_HOME) return false

    // Don't navigate if already at home
    if (navigator.isAtHome()) return false

    return navigator.navigateToHome()
}
