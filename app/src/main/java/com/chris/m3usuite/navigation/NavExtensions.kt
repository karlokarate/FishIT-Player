package com.chris.m3usuite.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

fun NavOptionsBuilder.popUpToStartDestination(
    navController: NavHostController,
    inclusive: Boolean = false,
    saveState: Boolean = false
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
