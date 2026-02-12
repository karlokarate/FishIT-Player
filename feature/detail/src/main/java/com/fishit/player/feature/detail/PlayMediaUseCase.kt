package com.fishit.player.feature.detail

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.SourceIdParser
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.playback.domain.PlayerEntryPoint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for starting playback from the unified detail screen.
 *
 * Converts [MediaSourceRef] and [CanonicalMediaId] into a [PlaybackContext] and delegates to the
 * player for playback.
 *
 * **Architecture Compliance (v2):**
 * - Detail screen calls this use case with canonical media + source reference
 * - Use case builds PlaybackContext with all necessary extras
 * - Delegates to PlayerEntryPoint (abstraction, NOT concrete player)
 * - Stream URLs are NOT stored - they are built by the PlaybackSourceFactory
 *
 * **Layer Boundaries:**
 * - Feature → Use Case → PlayerEntryPoint (interface) → Player (impl)
 * - UI does NOT import pipeline, data-*, transport, or player.internal
 * - Source-specific URL building happens in respective PlaybackSourceFactoryImpl
 *
 * **Data Flow:**
 * ```
 * DetailScreen → PlayMediaUseCase → PlayerEntryPoint → PlaybackSourceFactory → Player
 * ```
 *
 * The PlaybackContext contains:
 * - canonicalId: For resume tracking
 * - sourceType: For routing to the correct PlaybackSourceFactory
 * - sourceKey: For the factory to resolve the stream URL
 * - extras: Non-secret identifiers needed by the factory (vodId, chatId, etc.)
 */
@Singleton
class PlayMediaUseCase
    @Inject
    constructor(
        private val playerEntry: PlayerEntryPoint,
        private val canonicalMediaRepository: CanonicalMediaRepository,
    ) {
        /**
         * Initiates playback for a canonical media with the specified source.
         *
         * @param canonicalId The canonical media identity
         * @param source The source to play (one of potentially many sources)
         * @param resumePositionMs Position to start at (0 for beginning)
         */
        suspend fun play(
            canonicalId: CanonicalMediaId,
            source: MediaSourceRef,
            resumePositionMs: Long = 0,
        ) {
            UnifiedLog.d(TAG) {
                "play.requested: canonical=${canonicalId.key}, source=${source.sourceId}"
            }

            try {
                // Load full canonical media data for metadata
                val media = canonicalMediaRepository.findByCanonicalId(canonicalId)

                // Build PlaybackContext with all necessary data
                val context =
                    buildPlaybackContext(
                        canonicalId = canonicalId,
                        source = source,
                        resumePositionMs = resumePositionMs,
                        title = media?.canonicalTitle ?: "Unknown",
                        posterUrl = media?.poster?.let { extractHttpUrl(it) },
                    )

                UnifiedLog.d(TAG) {
                    "play.started: sourceType=${context.sourceType}, sourceKey=${context.sourceKey}"
                }

                // Delegate to player entry point
                playerEntry.start(context)
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "play.failed: ${e.message}" }
                throw e
            }
        }

        /**
         * Builds a [PlaybackContext] from canonical media and source reference.
         *
         * The context contains all data needed by the PlaybackSourceFactory to:
         * 1. Identify which factory handles this source (via sourceType)
         * 2. Resolve the stream URL (via sourceKey and extras)
         * 3. Build authentication headers if needed (factory-internal)
         */
        private fun buildPlaybackContext(
            canonicalId: CanonicalMediaId,
            source: MediaSourceRef,
            resumePositionMs: Long,
            title: String,
            posterUrl: String?,
        ): PlaybackContext {
            // Build extras map with non-secret identifiers
            val extras = buildExtrasForSource(source)

            // CRITICAL FIX: Extract sourceType from sourceKey as fallback when UNKNOWN
            // This fixes the bug where legacy repositories don't convert String→Enum correctly
            val sourceType = mapToPlayerSourceType(source.sourceType).let { mappedType ->
                if (mappedType == com.fishit.player.core.playermodel.SourceType.UNKNOWN) {
                    // Fallback: Extract from sourceKey format (src:xtream:... or xtream:vod:...)
                    extractSourceTypeFromKey(source.sourceId.value) ?: mappedType
                } else {
                    mappedType
                }
            }

            return PlaybackContext(
                canonicalId = canonicalId.key.value,
                sourceType = sourceType,
                sourceKey = source.sourceId.value,
                title = title,
                subtitle = source.sourceLabel,
                posterUrl = posterUrl,
                startPositionMs = resumePositionMs,
                isLive = source.sourceType == SourceType.XTREAM && isLiveContent(source),
                isSeekable = !isLiveContent(source),
                extras = extras,
            )
        }

        /**
         * Builds extras map with non-secret identifiers for the PlaybackSourceFactory.
         *
         * The factory uses these to build the stream URL without storing credentials.
         *
         * **Primary Source: playbackHints**
         * PlaybackHints are populated by the pipeline during catalog sync and preserved
         * through the persistence layer. They contain source-specific data needed for
         * playback URL construction (e.g., episodeId, containerExtension).
         *
         * **Fallback: sourceId parsing**
         * For backwards compatibility with data created before playbackHints were introduced,
         * we also parse the sourceId to extract identifiers.
         *
         * **Xtream Extras:**
         * - contentType: "live" | "vod" | "series"
         * - vodId / streamId / seriesId: Content identifier
         * - episodeId: For series episodes (CRITICAL - from playbackHints)
         * - containerExtension: File extension hint
         *
         * **Telegram Extras:**
         * - chatId: Telegram chat identifier
         * - messageId: Telegram message identifier
         */
        private fun buildExtrasForSource(source: MediaSourceRef): Map<String, String> =
            buildMap {
                val sourceIdValue = source.sourceId.value
                val hints = source.playbackHints

                when (source.sourceType) {
                    SourceType.XTREAM -> {
                        // First, add all playbackHints (authoritative source)
                        putAll(hints)

                        // Parse sourceId via XtreamIdCodec SSOT for backwards compatibility
                        val parsed = com.fishit.player.core.model.ids.XtreamIdCodec.parse(sourceIdValue)
                        when (parsed) {
                            is com.fishit.player.core.model.ids.XtreamParsedSourceId.Vod -> {
                                putIfAbsent(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_VOD)
                                putIfAbsent(PlaybackHintKeys.Xtream.VOD_ID, parsed.vodId.toString())
                            }
                            is com.fishit.player.core.model.ids.XtreamParsedSourceId.Series -> {
                                putIfAbsent(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_SERIES)
                                putIfAbsent(PlaybackHintKeys.Xtream.SERIES_ID, parsed.seriesId.toString())
                            }
                            is com.fishit.player.core.model.ids.XtreamParsedSourceId.EpisodeComposite -> {
                                putIfAbsent(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_SERIES)
                                putIfAbsent(PlaybackHintKeys.Xtream.SERIES_ID, parsed.seriesId.toString())
                                putIfAbsent(PlaybackHintKeys.Xtream.SEASON_NUMBER, parsed.season.toString())
                                putIfAbsent(PlaybackHintKeys.Xtream.EPISODE_NUMBER, parsed.episode.toString())
                                // NOTE: episodeId is NOT in sourceId - it MUST come from playbackHints
                            }
                            is com.fishit.player.core.model.ids.XtreamParsedSourceId.Episode -> {
                                putIfAbsent(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_SERIES)
                                // Direct episode ID available
                            }
                            is com.fishit.player.core.model.ids.XtreamParsedSourceId.Live -> {
                                putIfAbsent(PlaybackHintKeys.Xtream.CONTENT_TYPE, PlaybackHintKeys.Xtream.CONTENT_LIVE)
                                putIfAbsent(PlaybackHintKeys.Xtream.STREAM_ID, parsed.channelId.toString())
                            }
                            null -> {
                                // Unknown Xtream format — no backwards compatibility extras
                            }
                        }

                        // Add container extension if available from format (lowest priority)
                        if (!containsKey(PlaybackHintKeys.Xtream.CONTAINER_EXT)) {
                            source.format?.container?.let { put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it) }
                        }
                    }
                    SourceType.TELEGRAM -> {
                        // First, add all playbackHints
                        putAll(hints)

                        // Parse Telegram source ID via SourceIdParser SSOT (handles msg:, telegram:, tg: formats)
                        SourceIdParser.parseTelegramSourceId(sourceIdValue)?.let { (chatId, messageId) ->
                            putIfAbsent(PlaybackHintKeys.Telegram.CHAT_ID, chatId.toString())
                            putIfAbsent(PlaybackHintKeys.Telegram.MESSAGE_ID, messageId.toString())
                        }
                    }
                    SourceType.IO, SourceType.LOCAL -> {
                        // Local files: sourceId contains the path
                        put("filePath", sourceIdValue.removePrefix("io:").removePrefix("local:"))
                    }
                    SourceType.AUDIOBOOK -> {
                        // Audiobook: sourceId is the book identifier
                        put("audiobookId", sourceIdValue.removePrefix("audiobook:"))
                    }
                    else -> {
                        // Unknown source types: pass sourceId as generic identifier
                        put("sourceId", sourceIdValue)
                    }
                }
            }

        /** Helper to put value only if key is absent. */
        private fun MutableMap<String, String>.putIfAbsent(
            key: String,
            value: String,
        ) {
            if (!containsKey(key)) {
                put(key, value)
            }
        }

        /**
         * Maps model SourceType to player-model SourceType.
         *
         * Note: core/model SourceType has more variants than core/player-model SourceType.
         * - IO, LOCAL → FILE (local file variants)
         * - PLEX, OTHER → UNKNOWN (unsupported sources)
         */
        private fun mapToPlayerSourceType(sourceType: SourceType): com.fishit.player.core.playermodel.SourceType =
            when (sourceType) {
                SourceType.TELEGRAM -> com.fishit.player.core.playermodel.SourceType.TELEGRAM
                SourceType.XTREAM -> com.fishit.player.core.playermodel.SourceType.XTREAM
                SourceType.AUDIOBOOK -> com.fishit.player.core.playermodel.SourceType.AUDIOBOOK
                // Local file variants → FILE
                SourceType.IO,
                SourceType.LOCAL,
                -> com.fishit.player.core.playermodel.SourceType.FILE
                // Unsupported sources → UNKNOWN
                SourceType.PLEX,
                SourceType.OTHER,
                SourceType.UNKNOWN,
                -> com.fishit.player.core.playermodel.SourceType.UNKNOWN
            }

        /** Checks if source represents live content (non-seekable stream). */
        private fun isLiveContent(source: MediaSourceRef): Boolean {
            val sourceId = source.sourceId.value
            // NX format: src:xtream:<account>:live:<id> or legacy: xtream:live:<id>
            return sourceId.contains(":live:")
        }

        /**
         * Extracts HTTP URL from ImageRef if available.
         *
         * Only [ImageRef.Http] provides a directly usable URL. Other variants (TelegramThumb,
         * LocalFile, InlineBytes) require resolution via Coil Fetchers and are not directly usable
         * here.
         */
        private fun extractHttpUrl(imageRef: com.fishit.player.core.model.ImageRef): String? =
            when (imageRef) {
                is com.fishit.player.core.model.ImageRef.Http -> imageRef.url
                is com.fishit.player.core.model.ImageRef.TelegramThumb ->
                    null // Requires Telegram API resolution
                is com.fishit.player.core.model.ImageRef.LocalFile -> null // Local path, not URL
                is com.fishit.player.core.model.ImageRef.InlineBytes -> null // Bytes, not URL
            }

        /**
         * Delegates to [SourceKeyBridge] and converts to player-model SourceType.
         */
        private fun extractSourceTypeFromKey(sourceKey: String): com.fishit.player.core.playermodel.SourceType? {
            val coreType = SourceKeyBridge.extractSourceType(sourceKey) ?: return null
            return mapToPlayerSourceType(coreType).takeIf {
                it != com.fishit.player.core.playermodel.SourceType.UNKNOWN
            }
        }

        companion object {
            private const val TAG = "PlayMediaUseCase"
        }
    }
