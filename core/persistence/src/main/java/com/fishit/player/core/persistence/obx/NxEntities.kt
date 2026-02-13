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
 * This file defines the 17 NX_* ObjectBox entities that form the SSOT Work Graph.
 *
 * **SSOT Contract:** docs/v2/NX_SSOT_CONTRACT.md
 * **Roadmap:** docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
 *
 * ## Entity Overview (17 total)
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
 * - [NX_Category] - Content categories
 * - [NX_WorkCategoryRef] - Work ↔ Category links
 *
 * ### EPG (Live TV)
 * - [NX_EpgEntry] - EPG program guide entries
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
    /**
     * Recognition state: CONFIRMED | HEURISTIC | NEEDS_REVIEW | UNPLAYABLE.
     *
     * SSOT for classification confidence — replaces legacy `needsReview: Boolean`.
     * Mapped via [MappingUtils.safeEnumFromString] to `RecognitionState` enum.
     *
     * **NX_CONSOLIDATION_PLAN Phase 3**
     */
    @Index var recognitionState: String = "HEURISTIC",
    /** @deprecated Use [recognitionState] instead. Kept for migration compatibility. */
    @Deprecated("Use recognitionState field. Will be removed after migration.")
    var needsReview: Boolean = false,
    /** Adult content flag */
    @Index var isAdult: Boolean = false,
    // === Timestamps ===
    /** Creation timestamp - indexed for "Recently Added" queries */
    @Index var createdAt: Long = System.currentTimeMillis(),
    /** Update timestamp - indexed for "Recently Updated" queries */
    @Index var updatedAt: Long = System.currentTimeMillis(),
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
    // === Live Channel Specific (EPG/Catchup) ===
    /** EPG channel ID for program guide integration */
    var epgChannelId: String? = null,
    /** TV archive flag: 0=no catchup, 1=catchup available */
    var tvArchive: Int = 0,
    /** TV archive duration in days (catchup window) */
    var tvArchiveDuration: Int = 0,
    // === Denormalized FK ===
    /**
     * Denormalized work key for indexed batch lookups via `oneOf()`.
     * Mirrors `work.target.workKey` — set by write operations.
     * Avoids `box.all` full table scans in `findByWorkKeysBatch()`.
     */
    @Index var workKey: String = "",
    // === Timestamps ===
    var discoveredAt: Long = System.currentTimeMillis(),
    var lastSeenAt: Long = System.currentTimeMillis(),
    /**
     * Source-reported last modification timestamp (ms).
     * For incremental sync/"new episodes" detection.
     * Indexed for efficient queries in findSeriesUpdatedSince().
     */
    @Index var sourceLastModifiedMs: Long? = null,
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
    // === Playback Hints JSON ===
    /**
     * JSON-serialized playback hints map for source-specific playback data.
     *
     * Contains keys like:
     * - Xtream: xtream.vodId, xtream.episodeId, xtream.contentType, xtream.containerExtension
     * - Telegram: telegram.chatId, telegram.messageId, telegram.remoteId
     *
     * These hints are passed through to PlaybackContext.extras for PlaybackSourceFactory consumption.
     *
     * Format: JSON object `{"key1":"value1","key2":"value2"}`
     * Empty map is stored as null (space optimization).
     */
    var playbackHintsJson: String? = null,
    // === Source Link ===
    /** Source reference key */
    @Index var sourceKey: String = "",
    // === Denormalized FK ===
    /**
     * Denormalized work key for indexed batch lookups via `oneOf()`.
     * Mirrors `work.target.workKey` — set by write operations.
     * Avoids `box.all` full table scans in `findByWorkKeysBatch()`.
     */
    @Index var workKey: String = "",
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
    /**
     * Denormalized parent workKey for indexed queries.
     * Populated from parentWork.target.workKey on write.
     * INV-PERF: Enables B+ tree lookup instead of box.all full scan.
     */
    @Index var parentWorkKey: String = "",
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

// =============================================================================
// 17. NX_EpgEntry - EPG Program Guide Entries
// =============================================================================

/**
 * EPG (Electronic Program Guide) entry for live TV channels.
 *
 * Stores program schedule data for live streams. Multiple entries per channel
 * represent the program schedule over time.
 *
 * **Replaces:** `ObxEpgNowNext` (legacy entity with limited now/next only)
 *
 * **Key format:** `<channelWorkKey>:<startMs>` (unique per program)
 *
 * **Design decisions:**
 * - Stores individual program entries (not just now/next) for richer EPG UI
 * - Links to NX_Work via channelWorkKey (the LIVE work)
 * - Uses epgChannelId for external EPG provider matching
 * - Supports full program metadata (title, description, category, icon)
 *
 * @property epgEntryKey Unique key: `<channelWorkKey>:<startMs>`
 * @property channelWorkKey Work key of the LIVE channel (FK to NX_Work)
 * @property epgChannelId External EPG provider channel ID
 * @property title Program title
 * @property startMs Program start time in milliseconds (epoch)
 * @property endMs Program end time in milliseconds (epoch)
 * @property description Program description/plot
 * @property category Program category (e.g., "Movie", "News", "Sports")
 * @property iconUrl Program icon/poster URL
 * @property isNow True if this is the currently airing program (computed at query time)
 */
@Entity
data class NX_EpgEntry(
    @Id var id: Long = 0,
    // === Identity ===
    /** Unique EPG entry key: `<channelWorkKey>:<startMs>` */
    @Unique @Index var epgEntryKey: String = "",
    /** Work key of the LIVE channel (FK to NX_Work) */
    @Index var channelWorkKey: String = "",
    /** External EPG provider channel ID (for matching) */
    @Index var epgChannelId: String = "",
    // === Program Metadata ===
    /** Program title */
    @Index var title: String = "",
    /** Lowercase title for search */
    @Index var titleLower: String = "",
    /** Program start time in milliseconds (epoch) */
    @Index var startMs: Long = 0,
    /** Program end time in milliseconds (epoch) */
    @Index var endMs: Long = 0,
    /** Program description/plot */
    var description: String? = null,
    /** Program category (Movie, News, Sports, etc.) */
    var category: String? = null,
    /** Program icon/poster URL */
    var iconUrl: String? = null,
    // === Timestamps ===
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
) {
    /**
     * Check if this program is currently airing.
     * @param nowMs Current time in milliseconds
     * @return True if program is currently airing
     */
    fun isCurrentlyAiring(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs in startMs until endMs

    /**
     * Check if this is the next upcoming program.
     * @param nowMs Current time in milliseconds
     * @return True if program starts after now
     */
    fun isUpcoming(nowMs: Long = System.currentTimeMillis()): Boolean =
        startMs > nowMs

    /**
     * Calculate program duration in milliseconds.
     */
    val durationMs: Long get() = endMs - startMs

    /**
     * Calculate progress percentage (0.0 - 1.0) if currently airing.
     * @param nowMs Current time in milliseconds
     * @return Progress percentage or 0 if not airing
     */
    fun progressPercent(nowMs: Long = System.currentTimeMillis()): Float {
        if (!isCurrentlyAiring(nowMs)) return 0f
        val elapsed = nowMs - startMs
        return (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
    }
}

// =============================================================================
// 18. NX_XtreamCategorySelection - User Category Selections for Sync
// =============================================================================

/**
 * Tracks which Xtream categories are selected for synchronization.
 *
 * Part of Issue #669 - Sync by Category Implementation.
 *
 * **Purpose:**
 * - Allows users to select specific categories to include in catalog sync
 * - Reduces sync time by only syncing selected categories
 * - Persists selections across app restarts
 *
 * **Key format:** `xtream:<accountKey>:<categoryType>:<sourceCategoryId>`
 *
 * **Usage:**
 * - UI displays all categories from [XtreamCategoryPreloader]
 * - User toggles categories on/off
 * - Selections are saved here
 * - Catalog sync reads selections to filter categories
 *
 * @property selectionKey Unique key for this selection
 * @property accountKey Xtream account key
 * @property categoryType VOD, SERIES, or LIVE
 * @property sourceCategoryId Category ID from Xtream server
 * @property categoryName Display name (cached for UI)
 * @property isSelected Whether this category is selected for sync
 */
@Entity
data class NX_XtreamCategorySelection(
    @Id var id: Long = 0,
    /** Unique selection key: `xtream:<accountKey>:<categoryType>:<sourceCategoryId>` */
    @Unique @Index var selectionKey: String = "",
    /** Xtream account key */
    @Index var accountKey: String = "",
    /** Category type: VOD, SERIES, LIVE */
    @Index var categoryType: String = "VOD",
    /** Category ID from Xtream server */
    @Index var sourceCategoryId: String = "",
    /** Display name (cached from server) */
    var categoryName: String = "",
    /** Whether this category is selected for sync */
    @Index var isSelected: Boolean = true,
    /** Parent category ID (for hierarchy display) */
    var parentId: Int? = null,
    /** Sort order (from server) */
    var sortOrder: Int = 0,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /**
         * Generate selection key from components.
         */
        fun generateKey(accountKey: String, categoryType: String, sourceCategoryId: String): String =
            "xtream:$accountKey:$categoryType:$sourceCategoryId"
    }
}

