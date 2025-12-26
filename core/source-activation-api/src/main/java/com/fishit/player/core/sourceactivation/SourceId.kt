package com.fishit.player.core.sourceactivation

/**
 * Source identifiers for the three supported pipeline sources.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 (W-1)
 * - Sources are independent: Xtream, Telegram, IO can be ACTIVE/INACTIVE separately
 * - No source is ever required
 *
 * **Note:** This identifier is for activation scope (on/off state of a data source).
 * It is NOT the same as [com.fishit.player.core.model.PipelineIdTag], which is used
 * for pipeline-level identification in RawMediaMetadata. The two may align conceptually,
 * but serve different purposes in the architecture.
 *
 * **Location:** This enum lives in `core:source-activation-api` to allow both
 * `catalog-sync` (implementation) and `data-*` modules (activation calls)
 * to share it without circular dependencies.
 */
enum class SourceId {
    XTREAM,
    TELEGRAM,
    IO,
}
