package com.fishit.player.core.appstartup.bootstrap

/**
 * Safe interface for triggering Xtream session bootstrap.
 *
 * **Purpose:**
 * - Exposes session initialization to app layer
 * - Hides transport implementation details
 * - Allows app to trigger bootstrap without transport dependencies
 *
 * **Architecture (Phase B2):**
 * - Interface defined in core:app-startup (safe for app-v2)
 * - Implementation in infra/transport-xtream (XtreamSessionBootstrap)
 * - Bound via Hilt DI
 */
interface XtreamBootstrapper {
    /**
     * Start the Xtream session bootstrap process.
     *
     * Reads stored credentials and initializes Xtream API client if available.
     * Idempotent - safe to call multiple times.
     */
    fun start()
}

/**
 * Safe interface for triggering catalog sync bootstrap.
 *
 * **Purpose:**
 * - Exposes sync startup to app layer
 * - Hides catalog sync implementation details
 * - Allows app to trigger sync without catalog-sync dependencies
 *
 * **Architecture (Phase B2):**
 * - Interface defined in core:app-startup (safe for app-v2)
 * - Implementation in core/catalog-sync (CatalogSyncBootstrap)
 * - Bound via Hilt DI
 */
interface CatalogSyncStarter {
    /**
     * Start observing transport state and trigger sync when ready.
     *
     * Idempotent - safe to call multiple times.
     */
    fun start()
}
