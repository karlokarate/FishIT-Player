package com.chris.m3usuite.telegram.player

import com.chris.m3usuite.telegram.domain.TelegramMediaRef

/**
 * RemoteId-first playback request model for Telegram content.
 *
 * Per Phase D+ design decision:
 * - remoteId and uniqueId are the PRIMARY identifiers (stable across sessions)
 * - fileId is an OPTIONAL volatile cache that may become stale
 * - Playback resolution uses remoteId as truth, with fileId as optional fast-path
 *
 * @property chatId Telegram chat ID containing the media
 * @property messageId Telegram message ID (anchor message)
 * @property remoteId Stable remote file identifier (REQUIRED)
 * @property uniqueId Stable unique file identifier (REQUIRED)
 * @property fileId Volatile TDLib-local file ID (OPTIONAL, may be stale)
 */
data class TelegramPlaybackRequest(
    val chatId: Long,
    val messageId: Long,
    val remoteId: String,
    val uniqueId: String,
    val fileId: Int? = null,
)

/**
 * Convert TelegramMediaRef to a TelegramPlaybackRequest.
 *
 * This helper creates a playback request from a media reference,
 * using remoteId as the primary identifier while preserving
 * fileId as an optional cache for fast-path resolution.
 *
 * @param chatId Telegram chat ID
 * @param anchorMessageId Anchor message ID for the content
 * @return TelegramPlaybackRequest with remoteId-first semantics
 */
fun TelegramMediaRef.toPlaybackRequest(
    chatId: Long,
    anchorMessageId: Long,
): TelegramPlaybackRequest =
    TelegramPlaybackRequest(
        chatId = chatId,
        messageId = anchorMessageId,
        remoteId = remoteId,
        uniqueId = uniqueId,
        fileId = fileId,
    )
