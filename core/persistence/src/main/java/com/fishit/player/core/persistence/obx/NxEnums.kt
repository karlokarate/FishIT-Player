package com.fishit.player.core.persistence.obx

/**
 * Enums for NX_* Entities (OBX PLATIN Refactor)
 *
 * These enums define the valid values for string-typed fields in NX_* entities.
 * ObjectBox stores these as strings for compatibility and migration flexibility.
 *
 * **SSOT Contract:** contracts/NX_SSOT_CONTRACT.md
 *
 * ## Removed Enums (NX_CONSOLIDATION_PLAN Phase 6):
 * - `WorkType` → SSOT: `NxWorkRepository.WorkType` in core:model, mapped by `WorkTypeMapper`
 * - `SourceType` → SSOT: `NxWorkSourceRefRepository.SourceType` in core:model, mapped by `SourceTypeMapper`
 *
 * Remaining enums are valid-value documentation for entity string fields.
 * They have no production consumers and exist solely for reference.
 */

// =============================================================================
// IngestDecision - Ingest outcome
// =============================================================================

/**
 * Outcome of an ingest decision.
 *
 * Per NX_SSOT_CONTRACT.md INV-01, INV-02
 */
enum class IngestDecision {
    /** Item accepted and added to catalog */
    ACCEPTED,

    /** Item explicitly rejected */
    REJECTED,

    /** Item skipped (not rejected, but not processed) */
    SKIPPED,

    ;

    companion object {
        fun fromString(value: String): IngestDecision = entries.find { it.name.equals(value, ignoreCase = true) } ?: SKIPPED
    }
}

// =============================================================================
// IngestReasonCode - Detailed ingest reason
// =============================================================================

/**
 * Detailed reason code for ingest decisions.
 *
 * Per NX_SSOT_CONTRACT.md Section 4.1
 */
enum class IngestReasonCode {
    // === ACCEPTED reasons ===
    /** Created new work */
    CREATED_NEW,

    /** Linked to existing work via canonical match */
    LINKED_CANONICAL,

    /** Linked to existing work via authority ID */
    LINKED_AUTHORITY,

    /** Linked to existing work via source merge */
    LINKED_MERGED,

    // === REJECTED reasons ===
    /** Invalid or empty title */
    INVALID_TITLE,

    /** Unsupported media format */
    UNSUPPORTED_FORMAT,

    /** File too small (likely corrupt) */
    FILE_TOO_SMALL,

    /** File too large */
    FILE_TOO_LARGE,

    /** Blocked by content filter */
    BLOCKED_CONTENT,

    /** Duplicate source key already exists */
    DUPLICATE_SOURCE,

    /** Parse error during processing */
    PARSE_ERROR,

    /** Network error during fetch */
    NETWORK_ERROR,

    /** Unknown error */
    UNKNOWN_ERROR,

    // === SKIPPED reasons ===
    /** Already processed (same source seen before) */
    ALREADY_PROCESSED,

    /** Source disabled by user */
    SOURCE_DISABLED,

    /** Category excluded by filter */
    CATEGORY_EXCLUDED,

    /** Not media content (text message, etc.) */
    NOT_MEDIA,

    /** Rate limited, will retry later */
    RATE_LIMITED,

    ;

    companion object {
        fun fromString(value: String): IngestReasonCode = entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN_ERROR
    }
}

// =============================================================================
// ProfileType - User profile types
// =============================================================================

/**
 * Type of user profile.
 */
enum class ProfileType {
    /** Main adult profile */
    MAIN,

    /** Kids profile with content restrictions */
    KIDS,

    /** Guest profile with limited features */
    GUEST,

    ;

    companion object {
        fun fromString(value: String): ProfileType = entries.find { it.name.equals(value, ignoreCase = true) } ?: MAIN
    }
}

// =============================================================================
// ProfileRuleType - Content filtering rule types
// =============================================================================

/**
 * Type of profile content filtering rule.
 */
enum class ProfileRuleType {
    /** Maximum content rating allowed */
    MAX_RATING,

    /** Block specific genre */
    BLOCK_GENRE,

    /** Allow specific category */
    ALLOW_CATEGORY,

    /** Block specific category */
    BLOCK_CATEGORY,

    /** Maximum duration in minutes */
    MAX_DURATION,

    /** Block adult content */
    BLOCK_ADULT,

    /** Block specific work by key */
    BLOCK_WORK,

    /** Screen time limit per day (minutes) */
    SCREEN_TIME_LIMIT,

    ;

    companion object {
        fun fromString(value: String): ProfileRuleType = entries.find { it.name.equals(value, ignoreCase = true) } ?: MAX_RATING
    }
}

// =============================================================================
// RelationType - Work relationship types
// =============================================================================

/**
 * Type of relationship between works.
 */
enum class RelationType {
    /** Episode belongs to series */
    SERIES_EPISODE,

    /** Sequel to another work */
    SEQUEL,

    /** Prequel to another work */
    PREQUEL,

    /** Remake of another work */
    REMAKE,

    /** Spinoff of another work */
    SPINOFF,

    /** Alternative version (director's cut, etc.) */
    ALTERNATIVE,

    ;

    companion object {
        fun fromString(value: String): RelationType = entries.find { it.name.equals(value, ignoreCase = true) } ?: SERIES_EPISODE
    }
}

// =============================================================================
// PlaybackMethod - How to play a variant
// =============================================================================

/**
 * Method for playing a variant.
 */
enum class PlaybackMethod {
    /** Direct streaming */
    DIRECT,

    /** Progressive streaming */
    STREAMING,

    /** Must download first */
    DOWNLOAD_FIRST,

    ;

    companion object {
        fun fromString(value: String): PlaybackMethod = entries.find { it.name.equals(value, ignoreCase = true) } ?: DIRECT
    }
}

// =============================================================================
// RuntimeStateType - Transient state types
// =============================================================================

/**
 * Type of runtime state.
 */
enum class RuntimeStateType {
    /** Currently buffering */
    BUFFERING,

    /** Downloading for offline */
    DOWNLOADING,

    /** Error occurred */
    ERROR,

    /** Preparing playback */
    PREPARING,

    /** Ready to play */
    READY,

    ;

    companion object {
        fun fromString(value: String): RuntimeStateType = entries.find { it.name.equals(value, ignoreCase = true) } ?: READY
    }
}

// =============================================================================
// SyncStatus - Cloud sync status
// =============================================================================

/**
 * Status of cloud sync event.
 */
enum class SyncStatus {
    /** Pending sync */
    PENDING,

    /** Currently syncing */
    SYNCING,

    /** Successfully synced */
    SYNCED,

    /** Sync failed */
    FAILED,

    ;

    companion object {
        fun fromString(value: String): SyncStatus = entries.find { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

// =============================================================================
// CategoryType - Content category types
// =============================================================================

/**
 * Type of content category.
 */
enum class CategoryType {
    /** Video on demand */
    VOD,

    /** Series category */
    SERIES,

    /** Live TV */
    LIVE,

    /** Audiobooks */
    AUDIOBOOK,

    ;

    companion object {
        fun fromString(value: String): CategoryType = entries.find { it.name.equals(value, ignoreCase = true) } ?: VOD
    }
}
