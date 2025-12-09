package com.fishit.player.playback.domain

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType

/**
 * Factory interface for creating playback sources.
 *
 * Each source type (Telegram, Xtream, File, etc.) provides an implementation
 * that knows how to convert a [PlaybackContext] into a [PlaybackSource]
 * that the internal player can use.
 *
 * **Architecture:**
 * - Implementations live in `playback/telegram`, `playback/xtream`, etc.
 * - `InternalPlaybackSourceResolver` holds all factories (via DI)
 * - Resolver selects factory based on [PlaybackContext.sourceType]
 *
 * **Example Implementation:**
 * ```kotlin
 * class TelegramPlaybackSourceFactoryImpl @Inject constructor(
 *     private val transportClient: TelegramTransportClient
 * ) : PlaybackSourceFactory {
 *     override fun supports(sourceType: SourceType) = sourceType == SourceType.TELEGRAM
 *
 *     override suspend fun createSource(context: PlaybackContext): PlaybackSource {
 *         // Build MediaItem with TelegramFileDataSource
 *     }
 * }
 * ```
 */
interface PlaybackSourceFactory {

    /**
     * Checks if this factory can handle the given source type.
     *
     * @param sourceType The source type to check
     * @return true if this factory supports the source type
     */
    fun supports(sourceType: SourceType): Boolean

    /**
     * Creates a playback source for the given context.
     *
     * This method may perform async operations like:
     * - Resolving file locations (Telegram)
     * - Building authenticated URLs (Xtream)
     * - Validating file access (File)
     *
     * @param context The playback context
     * @return A playback source ready for the player
     * @throws PlaybackSourceException if source creation fails
     */
    suspend fun createSource(context: PlaybackContext): PlaybackSource
}

/**
 * Exception thrown when playback source creation fails.
 */
class PlaybackSourceException(
    message: String,
    val sourceType: SourceType? = null,
    cause: Throwable? = null
) : Exception(message, cause)
