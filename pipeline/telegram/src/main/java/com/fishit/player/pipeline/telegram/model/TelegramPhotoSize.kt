package com.fishit.player.pipeline.telegram.model

/**
 * Represents a photo size variant from Telegram.
 *
 * Based on analysis of real Telegram export JSONs, photos come in multiple sizes:
 * - Original/largest size (e.g., 1707x2560)
 * - Medium sizes (e.g., 853x1280, 533x800)
 * - Thumbnail size (e.g., 213x320)
 *
 * ## v2 remoteId-First Architecture
 *
 * Per `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`:
 * - Only `remoteId` is stored (stable across sessions)
 * - `fileId` is resolved at runtime via `getRemoteFile(remoteId)`
 * - `uniqueId` is NOT stored (no API to resolve it back)
 *
 * Example from exports: content.photo.sizes array
 *
 * @property width Width in pixels
 * @property height Height in pixels
 * @property remoteId Stable Telegram API remote file ID (cross-session stable)
 * @property sizeBytes File size in bytes (optional)
 */
data class TelegramPhotoSize(
    val width: Int,
    val height: Int,
    val remoteId: String,
    val sizeBytes: Long? = null,
)
