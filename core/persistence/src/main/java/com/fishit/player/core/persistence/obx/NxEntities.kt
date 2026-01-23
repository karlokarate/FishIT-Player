package com.fishit.player.core.persistence.obx

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.persistence.obx.converter.ImageRefConverter
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne

/**
 * NX_* Entity Definitions for OBX PLATIN Refactor (Issue #621)
 *
 * This file defines the 16 NX_* ObjectBox entities that form the SSOT Work Graph.
 *
 * **SSOT Contract:** docs/v2/NX_SSOT_CONTRACT.md
 * **Roadmap:** docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
 *
 * ## Entity Overview (16 total)
 *
 * ### Work Graph (Core)
 * - [NX_Work] - Central UI SSOT for canonical media works
 * - [NX_WorkSourceRef] - Links works to pipeline sources (multi-account)
 * - [NX_WorkVariant] - Playback variants (quality/encoding/language)
 * - [NX_WorkRelation] - Series ↔ Episode relationships
 *
 * ### User State
 * - [NX_WorkUserState] - Per-work user state (resume, watched, etc.)
 * - [NX_WorkRuntimeState] - Transient runtime state (buffering, errors)
 *
 * ### Ingest & Audit
 * - [NX_IngestLedger] - Audit trail for all ingest decisions
 *
 * ### Profile System
 * - [NX_Profile] - User profiles (main, kids, guest)
 * - [NX_ProfileRule] - Content filtering rules per profile
 * - [NX_ProfileUsage] - Profile usage tracking (screen time, etc.)
 *
 * ### Source Management
 * - [NX_SourceAccount] - Multi-account credentials per source
 *
 * ### Cloud Sync (Preparation)
 * - [NX_CloudOutboxEvent] - Pending cloud sync events
 *
 * ### Content Discovery
 * - [NX_WorkEmbedding] - Vector embeddings for semantic search
 *
 * ### Migration Support
 * - [NX_WorkRedirect] - Canonical merge redirects
 *
 * ## Key Formats (per NX_SSOT_CONTRACT.md)
 *
 * - **workKey:** `<workType>:<canonicalSlug>:<year|LIVE>`
 * - **authorityKey:** `<authority>:<type>:<id>`
 * - **sourceKey:** `<sourceType>:<accountKey>:<sourceId>`
 * - **variantKey:** `<sourceKey>#<qualityTag>:<languageTag>`
 *
 * ## Invariants (BINDING)
 *
 * - INV-01: Every ingest creates exactly one NX_IngestLedger entry
 * - INV-04: sourceKey is globally unique across all accounts
 * - INV-10: Every NX_Work has ≥1 NX_WorkSourceRef
 * - INV-12: workKey is globally unique
 * - INV-13: accountKey is mandatory in all NX_WorkSourceRef
 */

// =============================================================================
// 1. NX_Work - Central UI SSOT
// =============================================================================

/**
 * Central entity for canonical media works.
 *
 * This is the **only** entity that UI should read from (after Phase 4).
 * Created via deterministic "link-or-create" resolution from pipeline ingest.
 *
 * **Key format:** `<workType>:<canonicalSlug>:<year|LIVE>`
 *
 * @property workKey Globally unique canonical key
 * @property workType MOVIE, EPISODE, SERIES, CLIP, LIVE, AUDIOBOOK, UNKNOWN
 * @property canonicalTitle Normalized title (cleaned by normalizer)
 * @property canonicalTitleLower Lowercase for case-insensitive search
 * @property year Release year (null for LIVE)
 * @property season Season number (EPISODE only)
 * @property episode Episode number (EPISODE only)
 * @property needsReview True if classification was UNKNOWN
 */
@Entity
data class NX_Work(
    @Id var id: Long = 0,
    // === Identity ===
    /** Unique canonical key: `<workType>:<slug>:<year>` */
    @Unique @Index var workKey: String = "",
    /** Work type: MOVIE, EPISODE, SERIES, CLIP, LIVE, AUDIOBOOK, UNKNOWN */
    @Index var workType: String = "UNKNOWN",
    // === Core Metadata ===
    /** Normalized title (cleaned by normalizer) */
    @Index var canonicalTitle: String = "",
    /** Lowercase for case-insensitive search */
    @Index var canonicalTitleLower: String = "",
    /** Release year (null for LIVE) */
    @Index var year: Int? = null,
    /** Season number (EPISODE only) */
    var season: Int? = null,
    /** Episode number (EPISODE only) */
    var episode: Int? = null,
    // === External Authority IDs ===
    /** Primary authority key: `<authority>:<type>:<id>` */
    @Index var authorityKey: String? = null,
    /** TMDB ID (numeric string) */
    @Index var tmdbId: String? = null,
    /** IMDB ID (e.g., "tt0133093") */
    @Index var imdbId: String? = null,
    /** TVDB ID */
    @Index var tvdbId: String? = null,
    // === Display Metadata ===
    @Convert(converter = ImageRefConverter::class, dbType = String::class)
    var poster: ImageRef? = null,
    @Convert(converter = ImageRefConverter::class, dbType = String::class)
    var backdrop: ImageRef? = null,
    @Convert(converter = ImageRefConverter::class, dbType = String::class)
    var thumbnail: ImageRef? = null,
    var plot: String? = null,
    var rating: Double? = null,
    var durationMs: Long? = null,
    var genres: String? = null,
    var director: String? = null,
    var cast: String? = null,
    var releaseDate: String? = null,
    /** YouTube trailer URL or ID */
    var trailer: String? = null,
    // === Classification ===
    /** True if classification was UNKNOWN and needs manual review */
    var needsReview: Boolean = false,
    /** Adult content flag */
    @Index var isAdult: Boolean = false,
    // === Timestamps ===
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
) {
    /** All source references for this work */
    @Backlink(to = "work")
    lateinit var sourceRefs: ToMany<NX_WorkSourceRef>

    /** All playback variants for this work */
    @Backlink(to = "work")
    lateinit var variants: ToMany<NX_WorkVariant>

    /** Relations where this work is the parent (e.g., series → episodes) */
    @Backlink(to = "parentWork")
    lateinit var childRelations: ToMany<NX_WorkRelation>
}

// =============================================================================
// 2. NX_WorkSourceRef - Pipeline Source Links
// =============================================================================

/**
 * Links an [NX_Work] to a pipeline source (Telegram, Xtream, Local, etc.).
 *
 * Supports multi-account by including accountKey in sourceKey.
 *
 * **Key format:** `<sourceType>:<accountKey>:<sourceId>`
 *
 * @property sourceKey Globally unique source reference
 * @property sourceType Pipeline source: telegram, xtream, local, plex
 * @property accountKey Account identifier (MANDATORY, per INV-13)
 * @property sourceId Source-specific item identifier
 */
@Entity
data class NX_WorkSourceRef(
    @Id var id: Long = 0,
    // === Identity ===
    /** Unique source key: `<sourceType>:<accountKey>:<sourceId>` */
    @Unique @Index var sourceKey: String = "",
    /** Pipeline source type */
    @Index var sourceType: String = "",
    /** Account identifier (MANDATORY) */
    @Index var accountKey: String = "",
    /** Source-specific item ID */
    var sourceId: String = "",
    // === Source Metadata ===
    /** Original raw title from source */
    var rawTitle: String? = null,
    /** Original filename (if available) */
    var fileName: String? = null,
    /** File size in bytes */
    var fileSizeBytes: Long? = null,
    /** MIME type */
    var mimeType: String? = null,
    // === Telegram-Specific ===
    /** Telegram chat ID (for telegram sources) */
    var telegramChatId: Long? = null,
    /** Telegram message ID (for telegram sources) */
    var telegramMessageId: Long? = null,
    // === Xtream-Specific ===
    /** Xtream stream ID */
    var xtreamStreamId: Int? = null,
    /** Xtream category ID */
    var xtreamCategoryId: Int? = null,
    // === Live Channel Specific (EPG/Catchup) ===
    /** EPG channel ID for program guide integration */
    var epgChannelId: String? = null,
    /** TV archive flag: 0=no catchup, 1=catchup available */
    var tvArchive: Int = 0,
    /** TV archive duration in days (catchup window) */
    var tvArchiveDuration: Int = 0,
    // === Timestamps ===
    var discoveredAt: Long = System.currentTimeMillis(),
    var lastSeenAt: Long = System.currentTimeMillis(),
) {
    /** Parent work */
    lateinit var work: ToOne<NX_Work>
}

// =============================================================================
// 3. NX_WorkVariant - Playback Variants
// =============================================================================

/**
 * Represents a playback variant for an [NX_Work].
 *
 * Multiple variants allow quality/language selection per source.
 *
 * **Key format:** `<sourceKey>#<qualityTag>:<languageTag>`
 *
 * @property variantKey Unique variant identifier
 * @property qualityTag Quality identifier (source, 1080p, 720p, etc.)
 * @property languageTag Language identifier (original, en, de, etc.)
 */
@Entity
data class NX_WorkVariant(
    @Id var id: Long = 0,
    // === Identity ===
    /** Unique variant key */
    @Unique @Index var variantKey: String = "",
    /** Quality tag: source, 1080p, 720p, 480p, 4k */
    @Index var qualityTag: String = "source",
    /** Language tag: original, en, de, es, etc. */
    @Index var languageTag: String = "original",
    // === Playback Hints ===
    /** Direct playback URL (if available) */
    var playbackUrl: String? = null,
    /** Playback method hint: DIRECT, STREAMING, DOWNLOAD_FIRST */
    var playbackMethod: String = "DIRECT",
    /** Container format: mp4, mkv, avi, etc. */
    var containerFormat: String? = null,
    /** Video codec: h264, h265, vp9, etc. */
    var videoCodec: String? = null,
    /** Audio codec: aac, ac3, dts, etc. */
    var audioCodec: String? = null,
    /** Resolution width */
    var width: Int? = null,
    /** Resolution height */
    var height: Int? = null,
    /** Bitrate in bps */
    var bitrateBps: Long? = null,
    // === Source Link ===
    /** Source reference key */
    @Index var sourceKey: String = "",
    // === Timestamps ===
    var createdAt: Long = System.currentTimeMillis(),
) {
    /** Parent work */
    lateinit var work: ToOne<NX_Work>
}

// =============================================================================
// 4. NX_WorkRelation - Series ↔ Episode Relationships
// =============================================================================

/**
 * Defines relationships between works (e.g., series contains episodes).
 *
 * @property relationType Type: SERIES_EPISODE, SEQUEL, PREQUEL, REMAKE, etc.
 * @property sortOrder Order within parent (e.g., episode order in series)
 */
@Entity
data class NX_WorkRelation(
    @Id var id: Long = 0,
    /** Relation type: SERIES_EPISODE, SEQUEL, PREQUEL, REMAKE, SPINOFF */
    @Index var relationType: String = "SERIES_EPISODE",
    /** Sort order within parent */
    var sortOrder: Int = 0,
    // === Season/Episode for SERIES_EPISODE ===
    var season: Int? = null,
    var episode: Int? = null,
    var createdAt: Long = System.currentTimeMillis(),
) {
    /** Parent work (e.g., series) */
    lateinit var parentWork: ToOne<NX_Work>

    /** Child work (e.g., episode) */
    lateinit var childWork: ToOne<NX_Work>
}

// =============================================================================
// 5. NX_WorkUserState - Per-Work User State
// =============================================================================

/**
 * User state for a work (resume position, watched status, favorites, etc.).
 *
 * Stored per profile per work.
 *
 * @property profileId Profile this state belongs to
 * @property workKey Work this state is for
 */
@Entity
data class NX_WorkUserState(
    @Id var id: Long = 0,
    // === Keys ===
    /** Profile ID */
    @Index var profileId: Long = 0,
    /** Work key reference */
    @Index var workKey: String = "",
    // === Watch State ===
    /** Resume position in milliseconds */
    var resumePositionMs: Long = 0,
    /** Total duration (for percentage calculation) */
    var totalDurationMs: Long = 0,
    /** True if watched to completion (>90% or explicit mark) */
    @Index var isWatched: Boolean = false,
    /** Number of times watched */
    var watchCount: Int = 0,
    // === User Actions ===
    /** True if favorited */
    @Index var isFavorite: Boolean = false,
    /** User rating (1-5 stars, null if not rated) */
    var userRating: Int? = null,
    /** True if added to watchlist */
    @Index var inWatchlist: Boolean = false,
    /** True if hidden from recommendations */
    var isHidden: Boolean = false,
    // === Timestamps ===
    var lastWatchedAt: Long? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
)

// =============================================================================
// 6. NX_WorkRuntimeState - Transient Runtime State
// =============================================================================

/**
 * Transient runtime state (not persisted across app restarts in normal use).
 *
 * Used for buffering progress, download status, playback errors, etc.
 *
 * @property workKey Work this state is for
 * @property stateType Type of runtime state
 */
@Entity
data class NX_WorkRuntimeState(
    @Id var id: Long = 0,
    /** Work key reference */
    @Index var workKey: String = "",
    /** Variant key (if variant-specific) */
    var variantKey: String? = null,
    /** State type: BUFFERING, DOWNLOADING, ERROR, PREPARING */
    @Index var stateType: String = "",
    /** Progress percentage (0-100) */
    var progressPercent: Int = 0,
    /** Bytes downloaded (for downloads) */
    var bytesDownloaded: Long = 0,
    /** Total bytes (for downloads) */
    var totalBytes: Long = 0,
    /** Error message (for ERROR state) */
    var errorMessage: String? = null,
    /** Error code */
    var errorCode: String? = null,
    /** Last update timestamp */
    var updatedAt: Long = System.currentTimeMillis(),
)

// =============================================================================
// 7. NX_IngestLedger - Audit Trail
// =============================================================================

/**
 * Audit trail for all ingest decisions.
 *
 * **INV-01:** Every ingest candidate creates exactly one entry.
 * **INV-02:** No silent drops – REJECTED/SKIPPED must have a reason code.
 *
 * @property sourceKey Source identifier
 * @property decision ACCEPTED, REJECTED, SKIPPED
 * @property reasonCode IngestReasonCode enum value
 */
@Entity
data class NX_IngestLedger(
    @Id var id: Long = 0,
    // === Source Identification ===
    /** Source key */
    @Index var sourceKey: String = "",
    /** Source type: telegram, xtream, local, etc. */
    @Index var sourceType: String = "",
    /** Account key */
    @Index var accountKey: String = "",
    // === Decision ===
    /** Decision: ACCEPTED, REJECTED, SKIPPED */
    @Index var decision: String = "",
    /** Reason code (IngestReasonCode enum name) */
    @Index var reasonCode: String = "",
    /** Additional context (error message, etc.) */
    var reasonDetail: String? = null,
    // === Resolution Result (for ACCEPTED) ===
    /** Resulting work key (null if not ACCEPTED) */
    @Index var resultWorkKey: String? = null,
    /** True if a new work was created (false if linked to existing) */
    var createdNewWork: Boolean = false,
    // === Raw Input Snapshot ===
    /** Raw title from source */
    var rawTitle: String? = null,
    /** Classified work type */
    var classifiedWorkType: String? = null,
    // === Timestamps ===
    var processedAt: Long = System.currentTimeMillis(),
)

// =============================================================================
// 8. NX_Profile - User Profiles
// =============================================================================

/**
 * User profile (main, kids, guest).
 *
 * @property profileKey Unique profile identifier
 * @property profileType MAIN, KIDS, GUEST
 * @property name Display name
 */
@Entity
data class NX_Profile(
    @Id var id: Long = 0,
    /** Unique profile key */
    @Unique @Index var profileKey: String = "",
    /** Profile type: MAIN, KIDS, GUEST */
    @Index var profileType: String = "MAIN",
    /** Display name */
    var name: String = "",
    /** Avatar URL or asset reference */
    var avatarUrl: String? = null,
    /** True if this is the active profile */
    @Index var isActive: Boolean = false,
    /** True if PIN-protected */
    var isPinProtected: Boolean = false,
    /** PIN hash (if protected) */
    var pinHash: String? = null,
    // === Timestamps ===
    var createdAt: Long = System.currentTimeMillis(),
    var lastUsedAt: Long? = null,
)

// =============================================================================
// 9. NX_ProfileRule - Content Filtering Rules
// =============================================================================

/**
 * Content filtering rule for a profile.
 *
 * Unified replacement for multiple Kid/Permission entities.
 *
 * @property profileId Profile this rule belongs to
 * @property ruleType Type of rule
 * @property ruleValue Rule value/pattern
 */
@Entity
data class NX_ProfileRule(
    @Id var id: Long = 0,
    /** Profile ID */
    @Index var profileId: Long = 0,
    /** Rule type: MAX_RATING, BLOCK_GENRE, ALLOW_CATEGORY, MAX_DURATION, etc. */
    @Index var ruleType: String = "",
    /** Rule value (interpretation depends on ruleType) */
    var ruleValue: String = "",
    /** True if rule is active */
    var isEnabled: Boolean = true,
    var createdAt: Long = System.currentTimeMillis(),
)

// =============================================================================
// 10. NX_ProfileUsage - Profile Usage Tracking
// =============================================================================

/**
 * Profile usage tracking (screen time, content consumption, etc.).
 *
 * @property profileId Profile this usage belongs to
 * @property date Date of usage (YYYY-MM-DD format)
 */
@Entity
data class NX_ProfileUsage(
    @Id var id: Long = 0,
    /** Profile ID */
    @Index var profileId: Long = 0,
    /** Date (YYYY-MM-DD) */
    @Index var date: String = "",
    /** Total watch time in milliseconds for this date */
    var watchTimeMs: Long = 0,
    /** Number of items watched */
    var itemsWatched: Int = 0,
    /** Last activity timestamp */
    var lastActivityAt: Long = System.currentTimeMillis(),
)

// =============================================================================
// 11. NX_SourceAccount - Multi-Account Credentials
// =============================================================================

/**
 * Source account credentials for multi-account support.
 *
 * @property accountKey Unique account identifier
 * @property sourceType Source type: telegram, xtream, plex
 */
@Entity
data class NX_SourceAccount(
    @Id var id: Long = 0,
    /** Unique account key */
    @Unique @Index var accountKey: String = "",
    /** Source type */
    @Index var sourceType: String = "",
    /** Display name for account */
    var displayName: String = "",
    /** True if account is active */
    @Index var isActive: Boolean = true,
    // === Credentials (encrypted in production) ===
    /** Server URL (Xtream, Plex) */
    var serverUrl: String? = null,
    /** Username */
    var username: String? = null,
    /** Password/token (should be encrypted) */
    var credential: String? = null,
    // === Telegram-Specific ===
    /** Telegram user ID */
    var telegramUserId: Long? = null,
    /** Telegram phone number (masked) */
    var telegramPhone: String? = null,
    // === State ===
    /** Last sync timestamp */
    var lastSyncAt: Long? = null,
    /** Sync status: OK, ERROR, PENDING */
    var syncStatus: String = "PENDING",
    /** Error message if sync failed */
    var syncError: String? = null,
    // === Timestamps ===
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
)

// =============================================================================
// 12. NX_CloudOutboxEvent - Cloud Sync Queue
// =============================================================================

/**
 * Pending cloud sync event (preparation only - no cloud writes in Phase 0-6).
 *
 * @property eventType Type of event to sync
 * @property payload JSON payload
 */
@Entity
data class NX_CloudOutboxEvent(
    @Id var id: Long = 0,
    /** Event type: WORK_CREATED, STATE_UPDATED, PROFILE_CHANGED, etc. */
    @Index var eventType: String = "",
    /** Entity type: work, userState, profile, etc. */
    var entityType: String = "",
    /** Entity key */
    @Index var entityKey: String = "",
    /** JSON payload of the event */
    var payload: String = "",
    /** Sync status: PENDING, SYNCING, SYNCED, FAILED */
    @Index var syncStatus: String = "PENDING",
    /** Retry count */
    var retryCount: Int = 0,
    /** Last error message */
    var lastError: String? = null,
    /** Created timestamp */
    var createdAt: Long = System.currentTimeMillis(),
    /** Last attempt timestamp */
    var lastAttemptAt: Long? = null,
)

// =============================================================================
// 13. NX_WorkEmbedding - Vector Embeddings for Semantic Search
// =============================================================================

/**
 * Vector embedding for semantic search.
 *
 * Stored separately to avoid bloating NX_Work queries.
 *
 * @property workKey Work this embedding is for
 * @property embeddingType Type of embedding model used
 */
@Entity
data class NX_WorkEmbedding(
    @Id var id: Long = 0,
    /** Work key */
    @Unique @Index var workKey: String = "",
    /** Embedding model type: openai, sentence-transformers, etc. */
    var embeddingType: String = "",
    /** Embedding vector (stored as comma-separated floats) */
    var embeddingVector: String = "",
    /** Dimension of embedding */
    var dimension: Int = 0,
    /** Generation timestamp */
    var generatedAt: Long = System.currentTimeMillis(),
)

// =============================================================================
// 14. NX_WorkRedirect - Canonical Merge Redirects
// =============================================================================

/**
 * Redirect from old work key to new canonical key after merge.
 *
 * Used during migration when duplicate works are detected.
 *
 * @property oldWorkKey Previous work key (now deprecated)
 * @property newWorkKey Current canonical work key
 */
@Entity
data class NX_WorkRedirect(
    @Id var id: Long = 0,
    /** Old work key (deprecated) */
    @Unique @Index var oldWorkKey: String = "",
    /** New canonical work key */
    @Index var newWorkKey: String = "",
    /** Reason for redirect */
    var reason: String = "",
    /** Redirect timestamp */
    var createdAt: Long = System.currentTimeMillis(),
)

// =============================================================================
// 15. NX_Category - Content Categories
// =============================================================================

/**
 * Content category for organization.
 *
 * @property categoryKey Unique category identifier
 * @property sourceType Source this category belongs to
 */
@Entity
data class NX_Category(
    @Id var id: Long = 0,
    /** Unique category key: `<sourceType>:<accountKey>:<categoryId>` */
    @Unique @Index var categoryKey: String = "",
    /** Source type */
    @Index var sourceType: String = "",
    /** Account key */
    @Index var accountKey: String = "",
    /** Category ID from source */
    var sourceCategoryId: String = "",
    /** Display name */
    @Index var name: String = "",
    /** Lowercase name for search */
    @Index var nameLower: String = "",
    /** Parent category key (for hierarchy) */
    var parentCategoryKey: String? = null,
    /** Sort order */
    var sortOrder: Int = 0,
    /** Category type: VOD, SERIES, LIVE, AUDIOBOOK */
    @Index var categoryType: String = "VOD",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
)

// =============================================================================
// 16. NX_WorkCategoryRef - Work ↔ Category Links
// =============================================================================

/**
 * Links works to categories (many-to-many).
 *
 * @property workKey Work key
 * @property categoryKey Category key
 */
@Entity
data class NX_WorkCategoryRef(
    @Id var id: Long = 0,
    /** Work key */
    @Index var workKey: String = "",
    /** Category key */
    @Index var categoryKey: String = "",
    /** Sort order within category */
    var sortOrder: Int = 0,
    var createdAt: Long = System.currentTimeMillis(),
)
