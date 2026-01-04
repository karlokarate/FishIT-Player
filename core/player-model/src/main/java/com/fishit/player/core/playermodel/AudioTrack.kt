package com.fishit.player.core.playermodel

/**
 * Unique identifier for an audio track.
 *
 * Wraps a string ID that corresponds to Media3's track group/format indices.
 */
@JvmInline
value class AudioTrackId(
    val value: String,
) {
    companion object {
        /** Special ID indicating the default/first audio track. */
        val DEFAULT = AudioTrackId("DEFAULT")
    }
}

/** Audio channel layout/configuration. */
enum class AudioChannelLayout {
    /** Mono audio (1 channel). */
    MONO,

    /** Stereo audio (2 channels). */
    STEREO,

    /** 5.1 surround sound (6 channels). */
    SURROUND_5_1,

    /** 7.1 surround sound (8 channels). */
    SURROUND_7_1,

    /** Dolby Atmos or other object-based audio. */
    ATMOS,

    /** Unknown or unsupported layout. */
    UNKNOWN,

    ;

    companion object {
        /** Determines channel layout from channel count. */
        fun fromChannelCount(channelCount: Int): AudioChannelLayout =
            when (channelCount) {
                1 -> MONO
                2 -> STEREO
                6 -> SURROUND_5_1
                8 -> SURROUND_7_1
                else -> if (channelCount > 8) ATMOS else UNKNOWN
            }
    }
}

/** Audio codec type for display purposes. */
enum class AudioCodecType {
    AAC,
    AC3, // Dolby Digital
    EAC3, // Dolby Digital Plus
    DTS,
    TRUEHD, // Dolby TrueHD
    FLAC,
    OPUS,
    VORBIS,
    MP3,
    PCM,
    UNKNOWN,
    ;

    companion object {
        /**
         * Determines codec type from MIME type.
         *
         * Note: Order matters! EAC3/EC-3 must be checked before AC3 since "eac3" contains "ac3".
         */
        fun fromMimeType(mimeType: String?): AudioCodecType =
            when {
                mimeType == null -> UNKNOWN
                mimeType.contains("aac", ignoreCase = true) -> AAC
                // EAC3 must be checked BEFORE AC3 (eac3 contains ac3)
                mimeType.contains("eac3", ignoreCase = true) ||
                    mimeType.contains("ec-3", ignoreCase = true) -> EAC3
                mimeType.contains("ac3", ignoreCase = true) -> AC3
                mimeType.contains("dts", ignoreCase = true) -> DTS
                mimeType.contains("truehd", ignoreCase = true) ||
                    mimeType.contains("mlp", ignoreCase = true) -> TRUEHD
                mimeType.contains("flac", ignoreCase = true) -> FLAC
                mimeType.contains("opus", ignoreCase = true) -> OPUS
                mimeType.contains("vorbis", ignoreCase = true) -> VORBIS
                mimeType.contains("mp3", ignoreCase = true) ||
                    mimeType.contains("mpeg", ignoreCase = true) -> MP3
                mimeType.contains("pcm", ignoreCase = true) ||
                    mimeType.contains("raw", ignoreCase = true) -> PCM
                else -> UNKNOWN
            }
    }
}

/** Source type for audio tracks. */
enum class AudioSourceType {
    /** Embedded in the media container. */
    EMBEDDED,

    /** External audio file. */
    EXTERNAL,

    /** From HLS/DASH manifest. */
    MANIFEST,
}

/**
 * Represents a selectable audio track.
 *
 * This is a source-agnostic model used throughout the player stack. The player maps
 * Media3/ExoPlayer track groups to this type for consumption by UI and domain layers.
 *
 * @property id Unique identifier for track selection.
 * @property language BCP-47 language code (e.g., "en", "de", "es").
 * @property label Human-readable label (e.g., "English", "Deutsch (5.1)").
 * @property channelLayout Audio channel configuration.
 * @property codecType Audio codec type.
 * @property isDefault Whether this track is marked as default in the source.
 * @property isDescriptive Whether this is an audio description track for visually impaired.
 * @property sourceType Origin of the audio track.
 * @property bitrate Audio bitrate in bits per second (optional).
 * @property sampleRate Audio sample rate in Hz (optional).
 * @property mimeType MIME type of the audio format.
 */
data class AudioTrack(
    val id: AudioTrackId,
    val language: String?,
    val label: String,
    val channelLayout: AudioChannelLayout = AudioChannelLayout.UNKNOWN,
    val codecType: AudioCodecType = AudioCodecType.UNKNOWN,
    val isDefault: Boolean = false,
    val isDescriptive: Boolean = false,
    val sourceType: AudioSourceType = AudioSourceType.EMBEDDED,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val mimeType: String? = null,
) {
    companion object {
        /**
         * Creates an audio track from Media3 format data.
         *
         * @param groupIndex Track group index.
         * @param trackIndex Track index within the group.
         * @param language BCP-47 language code.
         * @param label Display label.
         * @param channelCount Number of audio channels.
         * @param isDefault Default flag.
         * @param isDescriptive Audio description flag.
         * @param bitrate Bitrate in bps.
         * @param sampleRate Sample rate in Hz.
         * @param mimeType MIME type.
         */
        fun fromMedia3(
            groupIndex: Int,
            trackIndex: Int,
            language: String?,
            label: String?,
            channelCount: Int,
            isDefault: Boolean = false,
            isDescriptive: Boolean = false,
            bitrate: Int = 0,
            sampleRate: Int = 0,
            mimeType: String? = null,
        ): AudioTrack {
            val id = AudioTrackId("$groupIndex:$trackIndex")
            val channelLayout = AudioChannelLayout.fromChannelCount(channelCount)
            val codecType = AudioCodecType.fromMimeType(mimeType)

            // Build descriptive label if not provided
            val displayLabel =
                label ?: buildLabel(language, channelLayout, codecType, isDescriptive)

            return AudioTrack(
                id = id,
                language = language,
                label = displayLabel,
                channelLayout = channelLayout,
                codecType = codecType,
                isDefault = isDefault,
                isDescriptive = isDescriptive,
                sourceType = AudioSourceType.EMBEDDED,
                bitrate = if (bitrate > 0) bitrate else null,
                sampleRate = if (sampleRate > 0) sampleRate else null,
                mimeType = mimeType,
            )
        }

        /** Builds a human-readable label from track properties. */
        private fun buildLabel(
            language: String?,
            channelLayout: AudioChannelLayout,
            codecType: AudioCodecType,
            isDescriptive: Boolean,
        ): String {
            val parts = mutableListOf<String>()

            // Language name
            language?.let { lang -> parts.add(getLanguageDisplayName(lang)) }
                ?: parts.add("Unknown")

            // Channel layout
            when (channelLayout) {
                AudioChannelLayout.MONO -> parts.add("Mono")
                AudioChannelLayout.STEREO -> parts.add("Stereo")
                AudioChannelLayout.SURROUND_5_1 -> parts.add("5.1")
                AudioChannelLayout.SURROUND_7_1 -> parts.add("7.1")
                AudioChannelLayout.ATMOS -> parts.add("Atmos")
                AudioChannelLayout.UNKNOWN -> {
                    // skip
                }
            }

            // Codec (only for special codecs)
            when (codecType) {
                AudioCodecType.AC3, AudioCodecType.EAC3 -> parts.add("Dolby")
                AudioCodecType.DTS -> parts.add("DTS")
                AudioCodecType.TRUEHD -> parts.add("TrueHD")
                else -> {
                    // skip common codecs
                }
            }

            // Audio description indicator
            if (isDescriptive) {
                parts.add("(AD)")
            }

            return parts.joinToString(" ")
        }

        /**
         * Gets a display name for a BCP-47 language code. Falls back to the code itself if not
         * recognized.
         */
        private fun getLanguageDisplayName(languageCode: String): String =
            when (languageCode.lowercase().take(2)) {
                "en" -> "English"
                "de" -> "Deutsch"
                "es" -> "Español"
                "fr" -> "Français"
                "it" -> "Italiano"
                "pt" -> "Português"
                "ru" -> "Русский"
                "ja" -> "日本語"
                "ko" -> "한국어"
                "zh" -> "中文"
                "ar" -> "العربية"
                "hi" -> "हिन्दी"
                "tr" -> "Türkçe"
                "pl" -> "Polski"
                "nl" -> "Nederlands"
                "sv" -> "Svenska"
                "da" -> "Dansk"
                "no" -> "Norsk"
                "fi" -> "Suomi"
                "cs" -> "Čeština"
                "hu" -> "Magyar"
                "el" -> "Ελληνικά"
                "he" -> "עברית"
                "th" -> "ไทย"
                "vi" -> "Tiếng Việt"
                "id" -> "Bahasa Indonesia"
                "ms" -> "Bahasa Melayu"
                "uk" -> "Українська"
                "und" -> "Unknown"
                else -> languageCode.uppercase()
            }
    }
}
