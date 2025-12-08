package com.fishit.player.core.feature

/**
 * Defines the lifecycle scope of a feature.
 *
 * The scope determines when a feature's resources are created and destroyed.
 */
enum class FeatureScope {
    /**
     * App-wide scope. Lives for the entire app lifecycle.
     * Used for core infrastructure features like logging, caching.
     */
    APP,

    /**
     * Pipeline-scoped. Lives per data source connection.
     * Used for Telegram, Xtream, and other pipeline features.
     */
    PIPELINE,

    /**
     * Player session scope. Lives per playback session.
     * Used for player-specific features like resume tracking.
     */
    PLAYER_SESSION,

    /**
     * Screen scope. Lives per navigation destination.
     * Used for UI screen features.
     */
    UI_SCREEN,

    /**
     * Request scope. Lives per single operation/request.
     * Used for one-shot operations like TMDB resolution.
     */
    REQUEST,
}
