package com.chris.m3usuite.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

fun NavOptionsBuilder.popUpToStartDestination(
    navController: NavHostController,
    inclusive: Boolean = false,
    saveState: Boolean = false,
) {
    val startRoute = navController.graph.startDestinationRoute
    if (startRoute != null) {
        popUpTo(startRoute) {
            this.inclusive = inclusive
            this.saveState = saveState
        }
    } else {
        popUpTo(navController.graph.findStartDestination().id) {
            this.inclusive = inclusive
            this.saveState = saveState
        }
    }
}

/**
 * Top-level navigation helper that preserves state across tabs/routes and avoids
 * building deep back stacks. It pops up to the graph start destination while saving
 * state, launches singleTop, and restores previous state if available.
 */
fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpToStartDestination(this@navigateTopLevel, saveState = true)
    }
}
