package com.fishit.player.core.persistence.obx

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

// =============================================================================
// Remaining Obx Entities (pending migration to NX layer — see P2 roadmap)
// Legacy Xtream/Profile entities have been retired (P1 cleanup).
// Canonical entities → ObxCanonicalEntities.kt
// =============================================================================

/**
 * ObjectBox entity for Telegram media items.
 *
 * ## remoteId-First Architecture (v2)
 *
 * This entity follows the **remoteId-first design** defined in
 * `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`.
 *
 * ### Persisted IDs (stable across sessions):
 * - `chatId` – for chat lookups and history API
 * - `messageId` – for message reload and pagination
 * - `remoteId` – for video/media file (resolved via `getRemoteFile()` at runtime)
 * - `thumbRemoteId` – for thumbnail file (resolved via `getRemoteFile()` at runtime)
 * - `posterRemoteId` – for poster image (resolved via `getRemoteFile()` at runtime)
 *
 * ### NOT Persisted (volatile/redundant):
 * - `fileId` – session-local integer, becomes stale after Telegram API cache changes
 * - `uniqueId` – no Telegram API API to resolve back to file
 *
 * ### Runtime Resolution:
 * ```kotlin
 * val fileId = telegramClient.getRemoteFile(remoteId)?.id
 * telegramClient.downloadFile(fileId, priority)
 * ```
 *
 * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
 */
@Entity
data class ObxTelegramMessage(
    @Id var id: Long = 0,
    // === Stable Identifiers (ALWAYS persist) ===
    @Index var chatId: Long = 0,
    @Index var messageId: Long = 0,
    // === File References (remoteId only - resolve fileId at runtime) ===
    /** Stable remote ID for video/media file. Use getRemoteFile(remoteId) to get fileId. */
    @Index var remoteId: String? = null,
    /** Stable remote ID for thumbnail. Use getRemoteFile(thumbRemoteId) to get fileId. */
    var thumbRemoteId: String? = null,
    /** Stable remote ID for poster image. Use getRemoteFile(posterRemoteId) to get fileId. */
    var posterRemoteId: String? = null,
    // === Media Properties ===
    var supportsStreaming: Boolean? = null,
    var caption: String? = null,
    @Index var captionLower: String? = null,
    var date: Long? = null,
    var fileName: String? = null,
    // === Enriched Metadata (from Telegram API) ===
    var durationSecs: Int? = null,
    var mimeType: String? = null,
    @Index var sizeBytes: Long? = null,
    var width: Int? = null,
    var height: Int? = null,
    @Index var language: String? = null,
    // === Movie Metadata (from structured 3-message pattern) ===
    var title: String? = null,
    var year: Int? = null,
    var genres: String? = null, // Comma-separated
    var fsk: Int? = null,
    var description: String? = null,
    // === Series Metadata (for episode grouping) ===
    @Index var isSeries: Boolean = false,
    @Index var seriesName: String? = null,
    var seriesNameNormalized: String? = null, // For grouping
    var seasonNumber: Int? = null,
    var episodeNumber: Int? = null,
    var episodeTitle: String? = null,
    // === External IDs (for canonical unification) ===
    /** TMDB ID for cross-pipeline canonical identity (e.g., "550" for Fight Club) */
    @Index var tmdbId: String? = null,
    /** IMDB ID (e.g., "tt0137523") */
    @Index var imdbId: String? = null,
)
