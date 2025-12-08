package com.chris.m3usuite.metrics

object RouteTag {
    @Volatile var current: String = "unknown"

    fun set(route: String) {
        current = route
    }
}
