package com.fishit.player.pipeline.telegram.model

/**
 * Represents a photo size variant from Telegram.
 *
 * Based on analysis of real Telegram export JSONs, photos come in multiple sizes:
 * - Original/largest size (e.g., 1707x2560)
 * - Medium sizes (e.g., 853x1280, 533x800)
 * - Thumbnail size (e.g., 213x320)
 *
 * Example from exports: content.photo.sizes array
 *
 * @property width Width in pixels
 * @property height Height in pixels
 * @property fileId Telegram file ID for this size
 * @property fileUniqueId Telegram unique file ID
 * @property sizeBytes File size in bytes (optional)
 */
data class TelegramPhotoSize(
    val width: Int,
    val height: Int,
    val fileId: String,
    val fileUniqueId: String,
    val sizeBytes: Long? = null,
)
