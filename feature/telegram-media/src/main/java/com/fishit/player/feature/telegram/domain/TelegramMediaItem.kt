package com.fishit.player.feature.telegram.domain

import com.fishit.player.core.model.MediaType

/**
 * Domain model for Telegram media items in the feature layer.
 *
 * This is a **feature-facing** model that decouples the UI from pipeline concerns.
 * Contains only non-secret identifiers and display fields needed for UI.
 *
 * **Architecture:**
 * - Feature layer uses TelegramMediaItem (domain model)
 * - Data layer maps RawMediaMetadata → TelegramMediaItem
 * - NO RawMediaMetadata in feature layer
 *
 * @property mediaId Stable unique identifier for this media item
 * @property title Display title
 * @property sourceLabel Human-readable source label (e.g., "Telegram Chat")
 * @property mediaType Type of media (MOVIE, SERIES_EPISODE, etc.)
 * @property durationMs Duration in milliseconds if available
 * @property posterUrl Poster/thumbnail URL if available
 * @property chatId Telegram chat ID (non-secret identifier)
 * @property messageId Telegram message ID (non-secret identifier)
 */
data class TelegramMediaItem(
    val mediaId: String,
    val title: String,
    val sourceLabel: String,
    val mediaType: MediaType,
    val durationMs: Long? = null,
    val posterUrl: String? = null,
    val chatId: Long? = null,
    val messageId: Long? = null,
)
