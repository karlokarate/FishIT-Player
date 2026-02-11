package com.fishit.player.core.model

import com.fishit.player.core.model.ids.PipelineItemId

/**
 * Reference to a specific media source (version) linked to a canonical media work.
 *
 * This model enables cross-pipeline unification by linking multiple sources (Telegram, Xtream, IO,
 * etc.) to the same canonical media identity.
 *
 * IMPORTANT: Different sources of the SAME movie/episode are NOT identical:
 * - Different file sizes (encoding, quality, extras)
 * - Different durations (cuts, frame rates, credits)
 * - Different formats (container, codecs, languages)
 * - Different quality (resolution, HDR, audio)
 *
 * The media represents the SAME WORK (movie, episode), but each source is a DIFFERENT FILE. Resume
 * positions use percentage-based conversion when switching between sources.
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md:
 * - Each canonical media can have multiple sources
 * - Sources represent different versions/qualities of the same content
 * - Used for version selection, resume sync, and unified detail screens
 *
 * @property sourceType Pipeline identifier (XTREAM, TELEGRAM, IO, etc.)
 * @property sourceId Stable unique identifier within pipeline (e.g., "telegram:123:456")
 * @property sourceLabel Human-readable label for UI (e.g., "Telegram: X-Men Group")
 * @property quality Video quality information (resolution, codec, HDR, etc.)
 * @property languages Available audio/subtitle languages
 * @property format Container format and codec details
 * @property sizeBytes File size in bytes (varies per source!)
 * @property durationMs Source-specific duration in milliseconds (varies per source!)
 * @property addedAt Timestamp when this source was linked
 * @property priority Ordering priority for source selection (higher = preferred)
 */
data class MediaSourceRef(
    val sourceType: SourceType,
    val sourceId: PipelineItemId,
    val sourceLabel: String,
    val quality: MediaQuality? = null,
    val languages: LanguageInfo? = null,
    val format: MediaFormat? = null,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null, // Source-specific duration!
    val addedAt: Long = System.currentTimeMillis(),
    val priority: Int = 0,
    /**
     * Source-specific hints required for playback.
     *
     * Copied from [RawMediaMetadata.playbackHints] during source linking.
     * Used by PlaybackSourceFactory to build playback URL/source.
     *
     * @see PlaybackHintKeys
     */
    val playbackHints: Map<String, String> = emptyMap(),
) {
    /** Display badge for this source type. */
    val badge: SourceBadge
        get() = SourceBadge.fromSourceType(sourceType)

    /**
     * Create a display label combining quality and language info. e.g., "1080p • German/English •
     * MKV • 4.5 GB • 2h 15m"
     */
    fun toDisplayLabel(): String =
        buildString {
            quality?.let { q -> append(q.toDisplayLabel()) }
            languages?.let { l ->
                if (isNotEmpty()) append(" • ")
                append(l.toDisplayLabel())
            }
            format?.let { f ->
                if (isNotEmpty()) append(" • ")
                append(f.container.uppercase())
            }
            sizeBytes?.let { s ->
                if (isNotEmpty()) append(" • ")
                append(formatFileSize(s))
            }
            durationMs?.let { d ->
                if (isNotEmpty()) append(" • ")
                append(formatDuration(d))
            }
        }

    /**
     * Create a compact comparison label for source selection. Shows key differentiators: "TG •
     * 1080p • 4.5 GB • 2:15:30"
     */
    fun toComparisonLabel(): String =
        buildString {
            append(badge.displayText)
            quality?.resolutionLabel?.let {
                append(" • ")
                append(it)
            }
            sizeBytes?.let {
                append(" • ")
                append(formatFileSize(it))
            }
            durationMs?.let {
                append(" • ")
                append(formatDurationPrecise(it))
            }
        }

    /**
     * Calculate resume position in this source from a percentage. Used when switching from another
     * source with different duration.
     *
     * @param percent Resume position as percentage (0.0 - 1.0)
     * @return Position in milliseconds for this source, or null if duration unknown
     */
    fun positionFromPercent(percent: Float): Long? = durationMs?.let { (percent * it).toLong() }

    /**
     * Calculate percentage position from milliseconds in this source.
     *
     * @param positionMs Position in milliseconds
     * @return Percentage (0.0 - 1.0), or null if duration unknown
     */
    fun percentFromPosition(positionMs: Long): Float? = durationMs?.takeIf { it > 0 }?.let { positionMs.toFloat() / it }

    private fun formatFileSize(bytes: Long): String =
        com.fishit.player.core.model.util.FileSizeFormatter.format(bytes)

    private fun formatDuration(ms: Long): String {
        val totalMinutes = ms / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    private fun formatDurationPrecise(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%d:%02d", minutes, seconds)
        }
    }
}

/**
 * Video quality metadata for a media source.
 *
 * @property resolution Display resolution (e.g., 1080, 2160 for 4K)
 * @property resolutionLabel Human-readable label (e.g., "1080p", "4K", "SD")
 * @property codec Video codec (e.g., "H.264", "H.265/HEVC", "AV1")
 * @property hdr HDR format if present (e.g., "HDR10", "Dolby Vision", "HDR10+")
 * @property bitrate Video bitrate in kbps if known
 */
data class MediaQuality(
    val resolution: Int? = null,
    val resolutionLabel: String? = null,
    val codec: String? = null,
    val hdr: String? = null,
    val bitrate: Int? = null,
) {
    fun toDisplayLabel(): String =
        buildString {
            resolutionLabel?.let { append(it) }
                ?: com.fishit.player.core.model.util.ResolutionLabel.fromHeight(resolution)?.let { append(it) }
            hdr?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
            codec?.let { c ->
                if (c.contains("265", ignoreCase = true) || c.contains("HEVC", ignoreCase = true)) {
                    if (isNotEmpty()) append(" ")
                    append("HEVC")
                }
            }
        }

    companion object {
        /** Parse quality info from resolution dimensions. */
        fun fromDimensions(
            width: Int?,
            height: Int?,
        ): MediaQuality? {
            val resolution = height ?: return null
            return MediaQuality(
                resolution = resolution,
                resolutionLabel = com.fishit.player.core.model.util.ResolutionLabel.fromHeight(resolution),
            )
        }

        /**
         * Parse quality from filename/title patterns. e.g.,
         * "Movie.2020.2160p.UHD.BluRay.x265-GROUP"
         */
        fun fromFilename(filename: String): MediaQuality? {
            val lower = filename.lowercase()
            val resolution =
                when {
                    "2160p" in lower || "4k" in lower || "uhd" in lower -> 2160
                    "1080p" in lower || "fhd" in lower -> 1080
                    "720p" in lower || "hd" in lower -> 720
                    "480p" in lower || "sd" in lower -> 480
                    else -> null
                }
            val hdr =
                when {
                    "dolby.vision" in lower || "dv" in lower -> "Dolby Vision"
                    "hdr10+" in lower -> "HDR10+"
                    "hdr10" in lower || "hdr" in lower -> "HDR10"
                    else -> null
                }
            val codec =
                when {
                    "x265" in lower || "h.265" in lower || "hevc" in lower -> "HEVC"
                    "x264" in lower || "h.264" in lower || "avc" in lower -> "H.264"
                    "av1" in lower -> "AV1"
                    "vp9" in lower -> "VP9"
                    else -> null
                }
            return if (resolution != null || hdr != null || codec != null) {
                MediaQuality(resolution = resolution, hdr = hdr, codec = codec)
            } else {
                null
            }
        }
    }
}

/**
 * Language information for a media source.
 *
 * @property audioLanguages Available audio tracks (ISO 639-1 codes)
 * @property subtitleLanguages Available subtitle tracks (ISO 639-1 codes)
 * @property primaryAudio Primary/default audio language
 * @property isDubbed Whether this is a dubbed version
 * @property isMulti Whether this has multiple audio tracks
 */
data class LanguageInfo(
    val audioLanguages: List<String> = emptyList(),
    val subtitleLanguages: List<String> = emptyList(),
    val primaryAudio: String? = null,
    val isDubbed: Boolean = false,
    val isMulti: Boolean = false,
) {
    fun toDisplayLabel(): String =
        buildString {
            if (isMulti && audioLanguages.size > 1) {
                append("Multi (${audioLanguages.joinToString("/") { it.uppercase() }})")
            } else if (primaryAudio != null) {
                append(languageDisplayName(primaryAudio))
            } else if (audioLanguages.isNotEmpty()) {
                append(audioLanguages.joinToString("/") { languageDisplayName(it) })
            }
            if (isDubbed) {
                if (isNotEmpty()) append(" ")
                append("[Dubbed]")
            }
        }

    private fun languageDisplayName(code: String): String =
        when (code.lowercase()) {
            "de", "ger", "german" -> "German"
            "en", "eng", "english" -> "English"
            "fr", "fre", "french" -> "French"
            "es", "spa", "spanish" -> "Spanish"
            "it", "ita", "italian" -> "Italian"
            "pt", "por", "portuguese" -> "Portuguese"
            "ru", "rus", "russian" -> "Russian"
            "ja", "jpn", "japanese" -> "Japanese"
            "ko", "kor", "korean" -> "Korean"
            "zh", "chi", "chinese" -> "Chinese"
            else -> code.uppercase()
        }

    companion object {
        /** Parse language info from filename patterns. e.g., "Movie.2020.German.DL.1080p" */
        fun fromFilename(filename: String): LanguageInfo? {
            val lower = filename.lowercase()
            val audioLangs = mutableListOf<String>()
            val isDubbed = "dubbed" in lower || "dub" in lower
            val isMulti = "multi" in lower || "dl" in lower || "dual" in lower

            // Detect languages
            if ("german" in lower || ".ger." in lower || ".de." in lower) audioLangs += "de"
            if ("english" in lower || ".eng." in lower || ".en." in lower) audioLangs += "en"
            if ("french" in lower || ".fre." in lower || ".fr." in lower) audioLangs += "fr"
            if ("spanish" in lower || ".spa." in lower || ".es." in lower) audioLangs += "es"
            if ("italian" in lower || ".ita." in lower || ".it." in lower) audioLangs += "it"
            if ("russian" in lower || ".rus." in lower || ".ru." in lower) audioLangs += "ru"
            if ("japanese" in lower || ".jpn." in lower || ".ja." in lower) audioLangs += "ja"

            return if (audioLangs.isNotEmpty() || isDubbed || isMulti) {
                LanguageInfo(
                    audioLanguages = audioLangs,
                    primaryAudio = audioLangs.firstOrNull(),
                    isDubbed = isDubbed,
                    isMulti = isMulti,
                )
            } else {
                null
            }
        }
    }
}

/**
 * Media format/container information.
 *
 * @property container File container format (e.g., "mkv", "mp4", "avi")
 * @property videoCodec Video codec (e.g., "h264", "hevc")
 * @property audioCodec Audio codec (e.g., "aac", "dts", "truehd")
 * @property audioBitrate Audio bitrate in kbps
 * @property audioChannels Audio channel layout (e.g., "5.1", "7.1", "2.0")
 */
data class MediaFormat(
    val container: String,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val audioBitrate: Int? = null,
    val audioChannels: String? = null,
) {
    fun toDisplayLabel(): String =
        buildString {
            append(container.uppercase())
            audioCodec?.let { ac ->
                val label =
                    when {
                        "truehd" in ac.lowercase() -> "TrueHD"
                        "dts" in ac.lowercase() && "hd" in ac.lowercase() -> "DTS-HD"
                        "dts" in ac.lowercase() -> "DTS"
                        "eac3" in ac.lowercase() || "e-ac-3" in ac.lowercase() -> "Atmos"
                        "ac3" in ac.lowercase() || "ac-3" in ac.lowercase() -> "DD"
                        "aac" in ac.lowercase() -> "AAC"
                        "flac" in ac.lowercase() -> "FLAC"
                        else -> null
                    }
                label?.let {
                    append(" • ")
                    append(it)
                    audioChannels?.let { ch -> append(" $ch") }
                }
            }
        }

    companion object {
        /** Parse format from filename extension and patterns. */
        fun fromFilename(filename: String): MediaFormat? {
            val lower = filename.lowercase()
            val container =
                when {
                    lower.endsWith(".mkv") -> "mkv"
                    lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "mp4"
                    lower.endsWith(".avi") -> "avi"
                    lower.endsWith(".mov") -> "mov"
                    lower.endsWith(".wmv") -> "wmv"
                    lower.endsWith(".ts") -> "ts"
                    ".mkv" in lower -> "mkv"
                    ".mp4" in lower -> "mp4"
                    else -> return null
                }
            val audioCodec =
                when {
                    "truehd" in lower -> "truehd"
                    "dts-hd" in lower || "dts.hd" in lower -> "dts-hd"
                    "dts" in lower -> "dts"
                    "atmos" in lower || "eac3" in lower || "ddp" in lower -> "eac3"
                    "dd5.1" in lower || "ac3" in lower -> "ac3"
                    else -> null
                }
            val channels =
                when {
                    "7.1" in lower -> "7.1"
                    "5.1" in lower -> "5.1"
                    "2.0" in lower -> "2.0"
                    else -> null
                }
            return MediaFormat(
                container = container,
                audioCodec = audioCodec,
                audioChannels = channels,
            )
        }
    }
}

/**
 * Badge type for identifying media source origin in UI.
 *
 * Each source type gets a distinct visual badge for quick identification when multiple versions of
 * the same media are available.
 */
enum class SourceBadge(
    val displayText: String,
    val icon: String, // Material icon name or custom drawable reference
    val colorHex: String, // Primary color for the badge
) {
    /** Telegram source - blue T badge */
    TELEGRAM("TG", "telegram", "#2AABEE"),

    /** Xtream Codes source - purple X badge */
    XTREAM("XC", "stream", "#9C27B0"),

    /** Local IO source - folder icon */
    IO("Local", "folder", "#4CAF50"),

    /** Audiobook source - headphones icon */
    AUDIOBOOK("Book", "headphones", "#FF9800"),

    /** Plex server source - Plex orange */
    PLEX("Plex", "plex", "#E5A00D"),

    /** Unknown/other source */
    OTHER("?", "help", "#9E9E9E"),
    ;

    companion object {
        fun fromSourceType(sourceType: SourceType): SourceBadge =
            when (sourceType) {
                SourceType.TELEGRAM -> TELEGRAM
                SourceType.XTREAM -> XTREAM
                SourceType.IO -> IO
                SourceType.AUDIOBOOK -> AUDIOBOOK
                SourceType.LOCAL -> IO
                SourceType.PLEX -> PLEX
                SourceType.OTHER -> OTHER
                SourceType.UNKNOWN -> OTHER
            }
    }
}
