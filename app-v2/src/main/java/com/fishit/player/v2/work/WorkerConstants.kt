package com.fishit.player.v2.work

/**
 * Constants for catalog sync workers.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-12: Tags (MANDATORY)
 * - W-13: Common InputData (MANDATORY)
 * - W-14: Source InputData (MANDATORY)
 */
object WorkerConstants {
    // =========================================================================
    // Work Names
    // =========================================================================

    /** SSOT unique work name for global catalog sync */
    const val WORK_NAME_CATALOG_SYNC = "catalog_sync_global"

    /** SSOT unique work name for TMDB enrichment (W-21, W-22) */
    const val WORK_NAME_TMDB_ENRICHMENT = "tmdb_enrichment_global"

    // =========================================================================
    // Tags (W-12)
    // =========================================================================

    /** Base tag for all catalog sync workers */
    const val TAG_CATALOG_SYNC = "catalog_sync"

    // Source tags
    const val TAG_SOURCE_XTREAM = "source_xtream"
    const val TAG_SOURCE_TELEGRAM = "source_telegram"
    const val TAG_SOURCE_IO = "source_io"
    const val TAG_SOURCE_TMDB = "source_tmdb"

    // Mode tags
    const val TAG_MODE_AUTO = "mode_auto"
    const val TAG_MODE_EXPERT_NOW = "mode_expert_sync_now"
    const val TAG_MODE_FORCE_RESCAN = "mode_expert_force_rescan"

    // Worker tags (format: worker/<ClassName>)
    const val TAG_WORKER_ORCHESTRATOR = "worker/CatalogSyncOrchestratorWorker"
    const val TAG_WORKER_XTREAM_PREFLIGHT = "worker/XtreamPreflightWorker"
    const val TAG_WORKER_XTREAM_SCAN = "worker/XtreamCatalogScanWorker"
    const val TAG_WORKER_TELEGRAM_AUTH = "worker/TelegramAuthPreflightWorker"
    const val TAG_WORKER_TELEGRAM_FULL = "worker/TelegramFullHistoryScanWorker"
    const val TAG_WORKER_TELEGRAM_INCREMENTAL = "worker/TelegramIncrementalScanWorker"
    const val TAG_WORKER_IO_QUICK = "worker/IoQuickScanWorker"

    // TMDB Worker tags
    const val TAG_WORKER_TMDB_ORCHESTRATOR = "worker/TmdbEnrichmentOrchestratorWorker"
    const val TAG_WORKER_TMDB_BATCH = "worker/TmdbEnrichmentBatchWorker"
    const val TAG_WORKER_TMDB_CONTINUATION = "worker/TmdbEnrichmentContinuationWorker"

    // Canonical Linking Worker tags (Task 2: Hot Path Entlastung)
    const val TAG_WORKER_CANONICAL_LINKING = "worker/CanonicalLinkingBacklogWorker"

    // =========================================================================
    // Common InputData Keys (W-13)
    // =========================================================================

    const val KEY_SYNC_RUN_ID = "sync_run_id"
    const val KEY_SYNC_MODE = "sync_mode"
    const val KEY_ACTIVE_SOURCES = "active_sources"
    const val KEY_WIFI_ONLY = "wifi_only"
    const val KEY_MAX_RUNTIME_MS = "max_runtime_ms"
    const val KEY_DEVICE_CLASS = "device_class"

    // =========================================================================
    // Source InputData Keys (W-14)
    // =========================================================================

    // Xtream
    const val KEY_XTREAM_SYNC_SCOPE = "xtream_sync_scope"
    const val KEY_XTREAM_USE_ENHANCED_SYNC = "xtream_use_enhanced_sync"
    const val KEY_XTREAM_INFO_BACKFILL_CONCURRENCY = "xtream_info_backfill_concurrency"

    // Telegram
    const val KEY_TELEGRAM_SYNC_KIND = "telegram_sync_kind"

    // IO
    const val KEY_IO_SYNC_SCOPE = "io_sync_scope"

    // =========================================================================
    // TMDB InputData Keys (W-14)
    // =========================================================================

    const val KEY_TMDB_SCOPE = "tmdb_scope"
    const val KEY_TMDB_FORCE_REFRESH = "tmdb_force_refresh"
    const val KEY_TMDB_BATCH_SIZE_HINT = "tmdb_batch_size_hint"
    const val KEY_TMDB_BATCH_CURSOR = "tmdb_batch_cursor"

    // =========================================================================
    // Output Data Keys
    // =========================================================================

    const val KEY_ITEMS_PERSISTED = "items_persisted"
    const val KEY_DURATION_MS = "duration_ms"
    const val KEY_FAILURE_REASON = "failure_reason"
    const val KEY_FAILURE_DETAILS = "failure_details"
    const val KEY_CHECKPOINT_CURSOR = "checkpoint_cursor"

    // =========================================================================
    // Device Classes (W-17)
    // =========================================================================

    const val DEVICE_CLASS_FIRETV_LOW_RAM = "FIRETV_LOW_RAM"
    const val DEVICE_CLASS_ANDROID_PHONE_TABLET = "ANDROID_PHONE_TABLET"

    // =========================================================================
    // Sync Modes
    // =========================================================================

    const val SYNC_MODE_AUTO = "AUTO"
    const val SYNC_MODE_EXPERT_NOW = "EXPERT_SYNC_NOW"
    const val SYNC_MODE_FORCE_RESCAN = "EXPERT_FORCE_RESCAN"

    // =========================================================================
    // Sync Scopes
    // =========================================================================

    const val XTREAM_SCOPE_INCREMENTAL = "INCREMENTAL"
    const val XTREAM_SCOPE_FULL = "FULL"

    const val TELEGRAM_KIND_INCREMENTAL = "INCREMENTAL"
    const val TELEGRAM_KIND_FULL_HISTORY = "FULL_HISTORY"

    const val IO_SCOPE_QUICK = "QUICK"

    // =========================================================================
    // TMDB Scopes (W-22)
    // =========================================================================

    /** Priority 1: Items with TmdbRef but missing SSOT data */
    const val TMDB_SCOPE_DETAILS_BY_ID = "DETAILS_BY_ID"

    /** Priority 2: Items without TmdbRef eligible for search */
    const val TMDB_SCOPE_RESOLVE_MISSING_IDS = "RESOLVE_MISSING_IDS"

    /** Both scopes: DETAILS_BY_ID first, then RESOLVE_MISSING_IDS */
    const val TMDB_SCOPE_BOTH = "BOTH"

    /** Refresh all SSOT data (expert mode) */
    const val TMDB_SCOPE_REFRESH_SSOT = "REFRESH_SSOT"

    // =========================================================================
    // Failure Reasons (Non-Retryable - W-20)
    // =========================================================================

    const val FAILURE_TELEGRAM_NOT_AUTHORIZED = "TELEGRAM_NOT_AUTHORIZED"
    const val FAILURE_XTREAM_INVALID_CREDENTIALS = "XTREAM_INVALID_CREDENTIALS"
    const val FAILURE_XTREAM_NOT_CONFIGURED = "XTREAM_NOT_CONFIGURED"
    const val FAILURE_IO_PERMISSION_MISSING = "IO_PERMISSION_MISSING"
    const val FAILURE_TMDB_API_KEY_MISSING = "TMDB_API_KEY_MISSING"

    /**
     * Non-retryable: Feature not yet implemented.
     *
     * Per TODO_AUDIT_BLOCKING_ISSUES.md: Workers must not pretend to work
     * when they are stubs. Return failure to indicate feature is unavailable.
     */
    const val FAILURE_NOT_IMPLEMENTED = "NOT_IMPLEMENTED"

    // =========================================================================
    // Runtime Configuration (W-16, W-17, W-18)
    // =========================================================================

    /** Default max runtime for workers (15 min) */
    const val DEFAULT_MAX_RUNTIME_MS = 15L * 60 * 1000

    /**
     * FireTV low RAM batch size (smaller for memory safety, tuned Dec 2025).
     *
     * @deprecated Use [com.fishit.player.core.persistence.config.ObxWriteConfig.FIRETV_BATCH_CAP] instead.
     * This constant will be removed in a future release. For device-aware batch sizing,
     * use [com.fishit.player.core.persistence.config.ObxWriteConfig.getBatchSize].
     *
     * Migration: Replace direct usage with ObxWriteConfig.FIRETV_BATCH_CAP or
     * use device-aware accessors like ObxWriteConfig.getBatchSize(context).
     */
    @Deprecated(
        message = "Use ObxWriteConfig.FIRETV_BATCH_CAP or ObxWriteConfig.getBatchSize(context)",
        replaceWith = ReplaceWith(
            "ObxWriteConfig.FIRETV_BATCH_CAP",
            "com.fishit.player.core.persistence.config.ObxWriteConfig"
        ),
        level = DeprecationLevel.WARNING
    )
    const val FIRETV_BATCH_SIZE = 35

    /**
     * Normal device batch size (optimized Dec 2025).
     *
     * @deprecated Use [com.fishit.player.core.persistence.config.ObxWriteConfig.NORMAL_BATCH_SIZE] instead.
     * This constant will be removed in a future release. For device-aware batch sizing,
     * use [com.fishit.player.core.persistence.config.ObxWriteConfig.getBatchSize].
     *
     * Migration: Replace direct usage with ObxWriteConfig.NORMAL_BATCH_SIZE or
     * use device-aware accessors like ObxWriteConfig.getBatchSize(context).
     */
    @Deprecated(
        message = "Use ObxWriteConfig.NORMAL_BATCH_SIZE or ObxWriteConfig.getBatchSize(context)",
        replaceWith = ReplaceWith(
            "ObxWriteConfig.NORMAL_BATCH_SIZE",
            "com.fishit.player.core.persistence.config.ObxWriteConfig"
        ),
        level = DeprecationLevel.WARNING
    )
    const val NORMAL_BATCH_SIZE = 100

    /** Exponential backoff initial delay (W-18) */
    const val BACKOFF_INITIAL_SECONDS = 30L

    /** Exponential backoff max delay (W-18) */
    const val BACKOFF_MAX_SECONDS = 15L * 60

    /** Retry limit for AUTO mode (W-19) */
    const val RETRY_LIMIT_AUTO = 3

    /** Retry limit for EXPERT modes (W-19) */
    const val RETRY_LIMIT_EXPERT = 5

    // =========================================================================
    // TMDB-specific Configuration
    // =========================================================================

    /** TMDB cooldown between retry attempts (24 hours) */
    const val TMDB_COOLDOWN_MS = 24L * 60 * 60 * 1000

    /** TMDB max attempts before marking UNRESOLVABLE_PERMANENT */
    const val TMDB_MAX_ATTEMPTS = 3

    /** TMDB batch size for FireTV low-RAM devices (10-25) */
    const val TMDB_FIRETV_BATCH_SIZE_MIN = 10
    const val TMDB_FIRETV_BATCH_SIZE_MAX = 25
    const val TMDB_FIRETV_BATCH_SIZE_DEFAULT = 15

    /** TMDB batch size for normal devices (50-150) */
    const val TMDB_NORMAL_BATCH_SIZE_MIN = 50
    const val TMDB_NORMAL_BATCH_SIZE_MAX = 150
    const val TMDB_NORMAL_BATCH_SIZE_DEFAULT = 75

    // =========================================================================
    // Xtream Info Backfill Configuration
    // =========================================================================

    /** Info backfill concurrency for normal devices (6-12) */
    const val INFO_BACKFILL_CONCURRENCY_NORMAL = 8

    /** Info backfill concurrency for FireTV low-RAM devices (2-4) */
    const val INFO_BACKFILL_CONCURRENCY_FIRETV = 3
}
