package com.fishit.player.feature.telegram

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.feature.telegram.domain.TelegramMediaItem
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.playback.domain.PlayerEntryPoint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for initiating playback of Telegram media items.
 *
 * This use case converts a [TelegramMediaItem] (domain model)
 * into a [PlaybackContext] and delegates to the player for playback.
 *
 * **Architecture Compliance (v2):**
 * - UI layer calls this use case with domain model (TelegramMediaItem)
 * - Use case builds PlaybackContext (source-agnostic)
 * - Delegates to PlayerEntryPoint (abstraction, NOT concrete player)
 * - Never constructs tg:// URIs (that's playback/telegram's job)
 * - NO RawMediaMetadata in feature layer (pipeline concern)
 * - NO direct dependency on player:internal
 *
 * **Layer Boundaries:**
 * - Feature → Use Case → PlayerEntryPoint (interface) → Player (impl)
 * - UI does NOT import pipeline, data-telegram, transport, or player.internal
 * - Telegram-specific URI building happens in TelegramPlaybackSourceFactoryImpl
 */
@Singleton
class TelegramTapToPlayUseCase @Inject constructor(
    private val playerEntry: PlayerEntryPoint,
) {

    /**
     * Initiates playback for a Telegram media item.
     *
     * Converts [TelegramMediaItem] to [PlaybackContext] and starts playback.
     *
     * @param item The Telegram media item to play (domain model)
     */
    suspend fun play(item: TelegramMediaItem) {
        UnifiedLog.d(TAG) { "telegram.tap_to_play.requested: ${item.mediaId}" }

        try {
            // Build PlaybackContext with SourceType.TELEGRAM
            val context = buildPlaybackContext(item)

            UnifiedLog.d(TAG) { "telegram.tap_to_play.started: canonicalId=${context.canonicalId}" }

            // Delegate to player entry point (abstraction)
            playerEntry.start(context)

        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "telegram.tap_to_play.failed: ${e.message}" }
            throw e
        }
    }

    /**
     * Builds a [PlaybackContext] from Telegram [TelegramMediaItem].
     *
     * The context contains only non-secret identifiers.
     * Actual tg:// URI construction happens in TelegramPlaybackSourceFactoryImpl.
     */
    private fun buildPlaybackContext(item: TelegramMediaItem): PlaybackContext {
        // Build extras map with non-secret identifiers
        val extras = buildMap<String, String> {
            item.chatId?.let { put("chatId", it.toString()) }
            item.messageId?.let { put("messageId", it.toString()) }
        }

        return PlaybackContext(
            canonicalId = item.mediaId,
            sourceType = com.fishit.player.core.playermodel.SourceType.TELEGRAM,
            sourceKey = item.mediaId, // Pass mediaId for factory resolution
            title = item.title,
            subtitle = item.sourceLabel,
            posterUrl = item.posterUrl,
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
