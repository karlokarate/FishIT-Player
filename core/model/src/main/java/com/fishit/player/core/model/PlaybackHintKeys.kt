package com.fishit.player.core.model

/**
 * Type-safe keys for PlaybackHints map.
 *
 * PlaybackHints carry source-specific data required for playback but NOT part of media identity.
 * These include:
 * - Resolution IDs (episode ID, stream ID)
 * - Container formats (container extension)
 * - Content type indicators
 *
 * **Contract:**
 * - Keys are stable strings for serialization compatibility
 * - Values are always strings (caller converts as needed)
 * - NEVER store secrets (credentials, tokens) in PlaybackHints
 * - PlaybackHints flow: Pipeline → RawMediaMetadata → MediaSourceRef → PlaybackContext.extras
 */
object PlaybackHintKeys {

    /**
     * Xtream-specific playback hints.
     *
     * These enable XtreamPlaybackSourceFactory to build correct playback URLs without additional
     * network calls.
     */
    object Xtream {
        // Content type indicators
        const val CONTENT_TYPE = "xtream.contentType"
        const val CONTENT_LIVE = "live"
        const val CONTENT_VOD = "vod"
        const val CONTENT_SERIES = "series"

        // Stream/Content IDs
        /** Live channel stream ID */
        const val STREAM_ID = "xtream.streamId"

        /** VOD stream ID */
        const val VOD_ID = "xtream.vodId"

        /** Parent series ID */
        const val SERIES_ID = "xtream.seriesId"

        /** Season number */
        const val SEASON_NUMBER = "xtream.seasonNumber"

        /** Episode number within season */
        const val EPISODE_NUMBER = "xtream.episodeNumber"

        /**
         * Episode stream ID (resolvedEpisodeId).
         *
         * **This is the critical field for episode playback.**
         *
         * Many Xtream panels require the actual episode stream ID (different from episode number)
         * to build the correct playback URL. Without this, playback fails.
         *
         * Source: XtreamEpisode.id from Xtream API get_series_info response.
         */
        const val EPISODE_ID = "xtream.episodeId"

        /**
         * Container extension (e.g., "mp4", "mkv", "ts").
         *
         * Used for URL building. If not present, factory uses default ("ts" for live, "mp4" for
         * VOD).
         */
        const val CONTAINER_EXT = "xtream.containerExtension"

        /**
         * Allowed output formats from server (comma-separated, e.g., "m3u8,ts").
         *
         * Source: XtreamServerInfo.userInfo.allowedOutputFormats from Xtream API. Used by
         * XtreamPlaybackSourceFactory to select the correct stream format.
         *
         * Policy priority: m3u8 > ts > mp4 (unless server restricts)
         */
        const val ALLOWED_OUTPUT_FORMATS = "xtream.allowedOutputFormats"
    }

    /** Telegram-specific playback hints. */
    object Telegram {
        /** Chat ID containing the media */
        const val CHAT_ID = "telegram.chatId"

        /** Message ID within the chat */
        const val MESSAGE_ID = "telegram.messageId"

        /**
         * Stable remote ID for the media file (v2 SSOT).
         *
         * TDLib resolves this to a session-local fileId via `getRemoteFile(remoteId)`.
         */
        const val REMOTE_ID = "telegram.remoteId"

        /** MIME type hint (e.g., "video/mp4") */
        const val MIME_TYPE = "telegram.mimeType"

        /**
         * Legacy session-local file ID.
         *
         * Keep for backward compatibility only; prefer [REMOTE_ID].
         */
        const val FILE_ID = "telegram.fileId"

        /**
         * Playback attempt mode (SSOT for Telegram playback strategy).
         *
         * Values:
         * - [ATTEMPT_MODE_DIRECT_FIRST]: Try playback directly without blocking on MP4 moov checks
         * - [ATTEMPT_MODE_BUFFERED_5MB]: Wait for 5MB download before starting playback
         *
         * Strategy:
         * 1. First attempt uses DIRECT_FIRST for fastest possible start
         * 2. On player error during DIRECT_FIRST, retry with BUFFERED_5MB
         * 3. BUFFERED_5MB provides deterministic fallback with reasonable latency
         */
        const val PLAYBACK_ATTEMPT_MODE = "telegram.playbackAttemptMode"

        /** Direct playback attempt (no buffering wait, fastest start) */
        const val ATTEMPT_MODE_DIRECT_FIRST = "DIRECT_FIRST"

        /** Buffered playback attempt (wait for 5MB before start) */
        const val ATTEMPT_MODE_BUFFERED_5MB = "BUFFERED_5MB"
    }
}
