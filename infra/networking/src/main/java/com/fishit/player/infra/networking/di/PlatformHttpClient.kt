package com.fishit.player.infra.networking.di

import javax.inject.Qualifier

/**
 * Qualifier for the app-wide **platform-level** OkHttpClient.
 *
 * This is the **parent** HTTP client that provides shared infrastructure:
 * - Connection pool (shared across all derived clients)
 * - Chucker HTTP Inspector (gated, debug only)
 * - User-Agent header
 * - Sensible default timeouts (connect/read/write 30s, NO callTimeout)
 *
 * Pipeline-specific clients (e.g., [@XtreamHttpClient][com.fishit.player.infra.transport.xtream.di.XtreamHttpClient])
 * are derived via [OkHttpClient.newBuilder()][okhttp3.OkHttpClient.newBuilder] and add their own
 * headers, dispatcher limits, and timeout overrides while sharing connection pool and interceptors.
 *
 * **Hierarchy:**
 * ```
 * @PlatformHttpClient  (infra/networking)     ← generic, app-wide
 *   ├── @XtreamHttpClient  (transport-xtream) ← +Accept:json, +Dispatcher, +callTimeout
 *   ├── @TelegramHttpClient (future)          ← +auth, +retry
 *   └── @M3uHttpClient (future)               ← +parser config
 * ```
 *
 * @see com.fishit.player.infra.networking.di.NetworkingModule
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlatformHttpClient
