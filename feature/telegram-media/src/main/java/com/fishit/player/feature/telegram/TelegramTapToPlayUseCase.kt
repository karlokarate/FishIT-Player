package com.fishit.player.feature.telegram

import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.internal.session.InternalPlayerSession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for initiating playback of Telegram media items.
 *
 * This use case converts a [RawMediaMetadata] from the Telegram pipeline
 * into a [PlaybackContext] with [SourceType.TELEGRAM] and delegates to
 * the Internal Player (SIP) for playback.
 *
 * **Architecture Compliance:**
 * - UI layer calls this use case with a selected item
 * - Use case builds PlaybackContext (source-agnostic)
 * - Delegates to InternalPlayerSession for playback
 * - Never constructs tg:// URIs (that's playback/telegram's job)
 *
 * **Layer Boundaries:**
 * - Feature → Use Case → Player → PlaybackSourceFactory
 * - UI does NOT import playback/telegram or transport
 * - Telegram-specific URI building happens in TelegramPlaybackSourceFactoryImpl
 */
@Singleton
class TelegramTapToPlayUseCase @Inject constructor(
    private val playerSession: InternalPlayerSession,
) {

    /**
     * Initiates playback for a Telegram media item.
     *
     * Converts [RawMediaMetadata] to [PlaybackContext] and starts playback.
     *
     * @param item The Telegram media item to play
     * @throws IllegalArgumentException if item is not from Telegram source
     */
    suspend fun play(item: RawMediaMetadata) {
        UnifiedLog.d(TAG) { "telegram.tap_to_play.requested: ${item.sourceId}" }

        // Validate source type
        if (item.sourceType != SourceType.TELEGRAM) {
            val error = "Item is not from Telegram source: ${item.sourceType}"
            UnifiedLog.e(TAG) { "telegram.tap_to_play.failed: $error" }
            throw IllegalArgumentException(error)
        }

        try {
            // Build PlaybackContext with SourceType.TELEGRAM
            val context = buildPlaybackContext(item)

            UnifiedLog.d(TAG) { "telegram.tap_to_play.started: canonicalId=${context.canonicalId}" }

            // Delegate to player session
            playerSession.play(context)

        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "telegram.tap_to_play.failed: ${e.message}" }
            throw e
        }
    }

    /**
     * Builds a [PlaybackContext] from Telegram [RawMediaMetadata].
     *
     * The context contains only non-secret identifiers.
     * Actual tg:// URI construction happens in TelegramPlaybackSourceFactoryImpl.
     *
     * **Important:** We extract sourceKey from sourceId.
     * Expected sourceId format: "msg:chatId:messageId" or "tg:chatId:messageId:fileId"
     */
    private fun buildPlaybackContext(item: RawMediaMetadata): PlaybackContext {
        // Extract identifiers from sourceId
        // sourceId format: "msg:chatId:messageId" or similar
        val parts = item.sourceId.split(":")
        val chatId = parts.getOrNull(1)?.toLongOrNull()
        val messageId = parts.getOrNull(2)?.toLongOrNull()
        val fileId = parts.getOrNull(3)?.toIntOrNull()

        // Build extras map with non-secret identifiers
        val extras = buildMap<String, String> {
            chatId?.let { put("chatId", it.toString()) }
            messageId?.let { put("messageId", it.toString()) }
            fileId?.let { put("fileId", it.toString()) }
            
            // Add mime type if available from metadata
            item.thumbnail?.mimeType?.let { put("mimeType", it) }
        }

        return PlaybackContext(
            canonicalId = item.sourceId,
            sourceType = com.fishit.player.core.playermodel.SourceType.TELEGRAM,
            sourceKey = item.sourceId, // Pass full sourceId for factory resolution
            title = item.originalTitle,
            subtitle = item.sourceLabel,
            posterUrl = item.poster?.uri,
            startPositionMs = 0L, // TODO: Add resume support later
            isLive = false, // Telegram media is not live
            isSeekable = true,
            extras = extras,
        )
    }

    companion object {
        private const val TAG = "TelegramTapToPlayUseCase"
    }
}
