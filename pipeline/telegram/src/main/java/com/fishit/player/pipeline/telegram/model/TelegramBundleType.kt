package com.fishit.player.pipeline.telegram.model

/**
 * Bundle type classification for Telegram Structured Bundles.
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md Section 1.1:
 * - A Structured Bundle is a group of 2..N Telegram messages with identical timestamp
 * - Must pass Cohesion Gate (R1b) and contain at least one VIDEO
 *
 * Bundle types are determined by the combination of message content types:
 * - FULL_3ER: Complete 3-cluster with PHOTO + TEXT + VIDEO(s)
 * - COMPACT_2ER: Partial 2-cluster with TEXT + VIDEO(s) or PHOTO + VIDEO(s)
 * - SINGLE: Individual message (no bundle)
 *
 * @see TelegramMessageBundler for bundle detection logic
 */
enum class TelegramBundleType {
    /**
     * Complete 3-cluster: PHOTO + TEXT + VIDEO(s)
     *
     * Contains:
     * - 1 PHOTO message (poster image)
     * - 1 TEXT message (structured metadata: tmdbUrl, year, fsk, etc.)
     * - 1..N VIDEO messages (playable assets)
     *
     * Provides: Full structured metadata + poster + video(s)
     */
    FULL_3ER,

    /**
     * Compact 2-cluster: TEXT + VIDEO(s) or PHOTO + VIDEO(s)
     *
     * Contains:
     * - Either: 1 TEXT message + 1..N VIDEO messages
     * - Or: 1 PHOTO message + 1..N VIDEO messages
     *
     * Provides: Partial structured metadata or poster + video(s)
     */
    COMPACT_2ER,

    /**
     * Single message (no bundle)
     *
     * Contains:
     * - 1 VIDEO message only
     *
     * Uses fallback: Regular title parsing path (no structured metadata)
     */
    SINGLE,
}
