package com.fishit.player.feature.detail

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.MediaSourceRef
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

        return PlaybackContext(
                canonicalId = canonicalId.key.value,
                sourceType = mapToPlayerSourceType(source.sourceType),
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
     * **Xtream Extras:**
     * - contentType: "live" | "vod" | "series"
     * - vodId / streamId / seriesId: Content identifier
     * - episodeId: For series episodes
     * - containerExtension: File extension hint
     *
     * **Telegram Extras:**
     * - chatId: Telegram chat identifier
     * - messageId: Telegram message identifier
     */
    private fun buildExtrasForSource(source: MediaSourceRef): Map<String, String> = buildMap {
        val sourceIdValue = source.sourceId.value

        when (source.sourceType) {
            SourceType.XTREAM -> {
                // Parse Xtream source ID: xtream:vod:123, xtream:series:123, xtream:live:123
                when {
                    sourceIdValue.startsWith("xtream:vod:") -> {
                        put("contentType", "vod")
                        // Accept legacy format: xtream:vod:{id}:{ext}
                        val vodId =
                                sourceIdValue
                                        .removePrefix("xtream:vod:")
                                        .split(":")
                                        .firstOrNull()
                                        .orEmpty()
                        put("vodId", vodId)
                    }
                    sourceIdValue.startsWith("xtream:series:") -> {
                        put("contentType", "series")
                        put("seriesId", sourceIdValue.removePrefix("xtream:series:"))
                    }
                    sourceIdValue.startsWith("xtream:episode:") -> {
                        // Format: xtream:episode:seriesId:season:episode
                        val parts = sourceIdValue.removePrefix("xtream:episode:").split(":")
                        if (parts.size >= 3) {
                            put("contentType", "series")
                            put("seriesId", parts[0])
                            put("seasonNumber", parts[1])
                            put("episodeNumber", parts[2])
                        }
                    }
                    sourceIdValue.startsWith("xtream:live:") -> {
                        put("contentType", "live")
                        put("streamId", sourceIdValue.removePrefix("xtream:live:"))
                    }
                }

                // Add container extension if available from format
                source.format?.container?.let { put("containerExtension", it) }
            }
            SourceType.TELEGRAM -> {
                // Parse Telegram source ID: msg:chatId:messageId
                if (sourceIdValue.startsWith("msg:")) {
                    val parts = sourceIdValue.removePrefix("msg:").split(":")
                    if (parts.size >= 2) {
                        put("chatId", parts[0])
                        put("messageId", parts[1])
                    }
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

    /**
     * Maps model SourceType to player-model SourceType.
     *
     * Note: core/model SourceType has more variants than core/player-model SourceType.
     * - IO, LOCAL → FILE (local file variants)
     * - PLEX, OTHER → UNKNOWN (unsupported sources)
     */
    private fun mapToPlayerSourceType(
            sourceType: SourceType
    ): com.fishit.player.core.playermodel.SourceType {
        return when (sourceType) {
            SourceType.TELEGRAM -> com.fishit.player.core.playermodel.SourceType.TELEGRAM
            SourceType.XTREAM -> com.fishit.player.core.playermodel.SourceType.XTREAM
            SourceType.AUDIOBOOK -> com.fishit.player.core.playermodel.SourceType.AUDIOBOOK
            // Local file variants → FILE
            SourceType.IO,
            SourceType.LOCAL -> com.fishit.player.core.playermodel.SourceType.FILE
            // Unsupported sources → UNKNOWN
            SourceType.PLEX,
            SourceType.OTHER,
            SourceType.UNKNOWN -> com.fishit.player.core.playermodel.SourceType.UNKNOWN
        }
    }

    /** Checks if source represents live content (non-seekable stream). */
    private fun isLiveContent(source: MediaSourceRef): Boolean {
        val sourceId = source.sourceId.value
        return sourceId.startsWith("xtream:live:")
    }

    /**
     * Extracts HTTP URL from ImageRef if available.
     *
     * Only [ImageRef.Http] provides a directly usable URL. Other variants (TelegramThumb,
     * LocalFile, InlineBytes) require resolution via Coil Fetchers and are not directly usable
     * here.
     */
    private fun extractHttpUrl(imageRef: com.fishit.player.core.model.ImageRef): String? {
        return when (imageRef) {
            is com.fishit.player.core.model.ImageRef.Http -> imageRef.url
            is com.fishit.player.core.model.ImageRef.TelegramThumb ->
                    null // Requires TDLib resolution
            is com.fishit.player.core.model.ImageRef.LocalFile -> null // Local path, not URL
            is com.fishit.player.core.model.ImageRef.InlineBytes -> null // Bytes, not URL
        }
    }

    companion object {
        private const val TAG = "PlayMediaUseCase"
    }
}
