package com.fishit.player.core.model

/**
 * Utility for determining media kind from MIME type and filename.
 *
 * Used by pipelines to classify documents as video/audio/other without importing heavy
 * dependencies.
 */
object MimeDecider {
    /** Video file extensions (lowercase). */
    private val videoExtensions =
        setOf(
            "mp4",
            "mkv",
            "avi",
            "mov",
            "wmv",
            "flv",
            "webm",
            "m4v",
            "mpg",
            "mpeg",
            "3gp",
            "3g2",
            "ts",
            "mts",
            "m2ts",
            "vob",
            "ogv",
            "divx",
            "xvid",
            "rm",
            "rmvb",
        )

    /** Audio file extensions (lowercase). */
    private val audioExtensions =
        setOf(
            "mp3",
            "m4a",
            "aac",
            "flac",
            "ogg",
            "opus",
            "wav",
            "wma",
            "alac",
            "aiff",
            "ape",
            "mka",
            "ac3",
            "dts",
            "m4b",
            "m4p",
            "mid",
            "midi",
        )

    /**
     * Infer media kind from MIME type and/or filename.
     *
     * @param mimeType MIME type (e.g., "video/mp4", "audio/mpeg")
     * @param fileName Filename with extension (e.g., "movie.mkv")
     * @return "video", "audio", or null if not media
     */
    fun inferKind(
        mimeType: String?,
        fileName: String?,
    ): MimeMediaKind? {
        // First check MIME type (most reliable)
        mimeType?.lowercase()?.let { mime ->
            when {
                mime.startsWith("video/") -> return MimeMediaKind.VIDEO
                mime.startsWith("audio/") -> return MimeMediaKind.AUDIO
            }
        }

        // Fallback to extension-based detection
        fileName?.let { name ->
            val ext = name.substringAfterLast('.', "").lowercase()
            when {
                ext in videoExtensions -> return MimeMediaKind.VIDEO
                ext in audioExtensions -> return MimeMediaKind.AUDIO
            }
        }

        return null
    }

    /**
     * Check if a file is playable media.
     *
     * @param mimeType MIME type
     * @param fileName Filename
     * @return true if video or audio
     */
    fun isPlayableMedia(
        mimeType: String?,
        fileName: String?,
    ): Boolean = inferKind(mimeType, fileName) != null
}

/**
 * Basic media kind for MIME-based document classification.
 *
 * Note: This is distinct from [MediaKind] in CanonicalMediaId.kt which is for
 * canonical identity (MOVIE vs EPISODE). This enum is for file type classification.
 */
enum class MimeMediaKind {
    VIDEO,
    AUDIO,
}
