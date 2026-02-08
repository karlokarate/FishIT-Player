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
    // =========================================================================
    // Common codec/quality hints (source-agnostic, for UI display)
    // =========================================================================

    /** Video codec name (e.g., "h264", "hevc", "vp9") */
    const val VIDEO_CODEC = "video.codec"

    /** Video width in pixels */
    const val VIDEO_WIDTH = "video.width"

    /** Video height in pixels */
    const val VIDEO_HEIGHT = "video.height"

    /** Audio codec name (e.g., "aac", "ac3", "opus") */
    const val AUDIO_CODEC = "audio.codec"

    /** Audio channel count (e.g., "2" for stereo, "6" for 5.1) */
    const val AUDIO_CHANNELS = "audio.channels"

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

        /**
         * Video bitrate in kbps from API ffprobe data.
         *
         * Useful for quality selection in player and UI display.
         * Source: XtreamEpisodeInfoBlock.bitrate from get_series_info response.
         */
        const val BITRATE = "xtream.bitrate"

        /**
         * Direct source URL (typically HLS) for live channels.
         *
         * Some panels provide a direct HLS URL that bypasses stream ID resolution.
         * When available, can optimize playback by skipping URL construction.
         * Source: XtreamLiveStream.directSource from get_live_streams response.
         */
        const val DIRECT_SOURCE = "xtream.directSource"

        /**
         * VOD kind/alias from API stream_type field (e.g., "movie", "vod", "movies").
         *
         * Used for correct URL building and detail API calls.
         * Different panels use different aliases for the same content.
         */
        const val VOD_KIND = "xtream.vodKind"

        /**
         * Series kind/alias from API stream_type field (e.g., "series", "episodes").
         *
         * Used for correct URL building for episode playback.
         */
        const val SERIES_KIND = "xtream.seriesKind"

        /**
         * Live kind/alias from API stream_type field (typically "live").
         *
         * Used for correct URL building for live streams.
         */
        const val LIVE_KIND = "xtream.liveKind"
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

        /** File size in bytes (for download progress estimation) */
        const val FILE_SIZE = "telegram.fileSize"

        /** Original file name from Telegram media */
        const val FILE_NAME = "telegram.fileName"

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
