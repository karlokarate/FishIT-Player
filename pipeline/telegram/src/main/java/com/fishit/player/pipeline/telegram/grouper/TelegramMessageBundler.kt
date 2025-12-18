package com.fishit.player.pipeline.telegram.grouper

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.api.TgContent
import com.fishit.player.infra.transport.telegram.api.TgMessage
import com.fishit.player.pipeline.telegram.model.TelegramBundleType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Groups Telegram messages by identical timestamp into bundles.
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md:
 * - Messages with identical `date` (Unix timestamp) are grouped as BundleCandidate
 * - Cohesion Gate (Contract R1b) decides acceptance:
 * - Primary: albumId from TDLib (if available)
 * - Fallback: messageId span ≤ 3×2²⁰ OR step-pattern 2²⁰
 * - Rejected candidates are split into SINGLE units
 * - Order in bundles: PHOTO (lowest msgId) → TEXT → VIDEO (highest msgId)
 *
 * Per MEDIA_NORMALIZATION_CONTRACT: No normalization here. Bundle fields are RAW extracted and
 * passed to TelegramMediaItem.
 *
 * @see TelegramMessageBundle for bundle structure
 * @see TelegramBundleType for bundle classifications
 */
@Singleton
class TelegramMessageBundler @Inject constructor() {

    companion object {
        private const val TAG = "TelegramMessageBundler"

        /**
         * Maximum messageId span for cohesion (3 × 2²⁰ = 3,145,728).
         *
         * Per Contract R1b Cohesion Gate: Messages must have IDs within this span to be considered
         * part of the same bundle when albumId is not available.
         */
        const val MAX_MESSAGE_ID_SPAN = 3 * 1_048_576L

        /**
         * Expected step between messages in a bundle (2²⁰ = 1,048,576).
         *
         * Per Contract Section 1.3: Message IDs differ by exactly 1,048,576 (2²⁰) within a cluster
         * when posted by Telegram's bulk upload.
         */
        const val EXPECTED_MESSAGE_ID_STEP = 1_048_576L
    }

    /**
     * Groups messages by timestamp and applies Cohesion Gate.
     *
     * Process:
     * 1. Group all messages by identical timestamp → BundleCandidates
     * 2. For each candidate with 2+ messages, apply Cohesion Gate
     * 3. Accepted candidates → TelegramMessageBundle with proper type
     * 4. Rejected candidates → Split into SINGLE bundles
     * 5. Single-message groups → SINGLE bundles
     *
     * @param messages Unsorted list of TgMessage from transport layer
     * @return List of TelegramMessageBundle (sorted by newest timestamp first)
     */
    fun groupByTimestamp(messages: List<TgMessage>): List<TelegramMessageBundle> {
        if (messages.isEmpty()) return emptyList()

        // Step 1: Group by identical timestamp
        val timestampGroups = messages.groupBy { it.date }

        val bundles = mutableListOf<TelegramMessageBundle>()
        var acceptedBundleCount = 0
        var rejectedCandidateCount = 0
        var singleCount = 0

        for ((timestamp, group) in timestampGroups) {
            when {
                // Single message → SINGLE bundle
                group.size == 1 -> {
                    bundles.add(TelegramMessageBundle.single(group.first()))
                    singleCount++
                }
                // Multiple messages → Apply Cohesion Gate
                else -> {
                    val sortedGroup = group.sortedBy { it.messageId }

                    if (checkCohesion(sortedGroup)) {
                        // Cohesion passed → Create structured bundle
                        val bundle = createBundle(timestamp, sortedGroup)
                        bundles.add(bundle)
                        acceptedBundleCount++

                        UnifiedLog.d(TAG) {
                            "Bundle detected: chatId=${bundle.chatId}, " +
                                    "bundleType=${bundle.bundleType}, " +
                                    "videoCount=${bundle.videoCount}, " +
                                    "timestamp=$timestamp"
                        }
                    } else {
                        // Cohesion failed → Split into SINGLE bundles
                        val videoMessages = sortedGroup.filter { isVideo(it) }
                        videoMessages.forEach { msg ->
                            bundles.add(TelegramMessageBundle.single(msg))
                        }
                        rejectedCandidateCount++
                        singleCount += videoMessages.size

                        UnifiedLog.w(TAG) {
                            "Cohesion Gate rejected: timestamp=$timestamp, " +
                                    "messageCount=${group.size}, " +
                                    "span=${sortedGroup.last().messageId - sortedGroup.first().messageId}"
                        }
                    }
                }
            }
        }

        // Log chat statistics
        val chatId = messages.firstOrNull()?.chatId ?: 0L
        UnifiedLog.i(TAG) {
            "Chat stats: chatId=$chatId, " +
                    "bundles=$acceptedBundleCount, " +
                    "rejected=$rejectedCandidateCount, " +
                    "singles=$singleCount, " +
                    "emittedItems=${bundles.sumOf { it.videoCount }}"
        }

        // Sort by newest timestamp first
        return bundles.sortedByDescending { it.timestamp }
    }

    /**
     * Applies Cohesion Gate (Contract R1b).
     *
     * A candidate passes if:
     * 1. Primary: All messages share the same albumId (from TDLib)
     * 2. Fallback: MessageId span ≤ MAX_MESSAGE_ID_SPAN OR step-pattern matches
     * EXPECTED_MESSAGE_ID_STEP
     *
     * @param candidate Messages sorted by messageId
     * @return true if candidate is cohesive, false otherwise
     */
    fun checkCohesion(candidate: List<TgMessage>): Boolean {
        if (candidate.size < 2) return true // Single message is always cohesive

        // Primary: Check albumId (if available in transport layer)
        // Note: TgMessage doesn't currently expose albumId; using fallback only for now
        // TODO: Add albumId to TgMessage when transport-telegram exposes it

        // Fallback: Check messageId span
        val minId = candidate.first().messageId
        val maxId = candidate.last().messageId
        val span = maxId - minId

        // Pass if span is within threshold
        if (span <= MAX_MESSAGE_ID_SPAN) {
            return true
        }

        // Alternative: Check if step-pattern matches (all gaps are EXPECTED_MESSAGE_ID_STEP)
        val hasConsistentSteps =
                candidate.zipWithNext().all { (a, b) ->
                    val step = b.messageId - a.messageId
                    step == EXPECTED_MESSAGE_ID_STEP
                }

        return hasConsistentSteps
    }

    /**
     * Classifies a bundle type based on contained message types.
     *
     * Classification:
     * - FULL_3ER: Has PHOTO + TEXT + VIDEO(s)
     * - COMPACT_2ER: Has (TEXT + VIDEO) or (PHOTO + VIDEO)
     * - SINGLE: Only VIDEO (or messages don't form valid bundle)
     */
    fun classifyBundle(messages: List<TgMessage>): TelegramBundleType {
        val hasVideo = messages.any { isVideo(it) }
        val hasPhoto = messages.any { isPhoto(it) }
        val hasText = messages.any { isText(it) }

        return when {
            !hasVideo -> TelegramBundleType.SINGLE // No video = not a media bundle
            hasPhoto && hasText -> TelegramBundleType.FULL_3ER
            hasPhoto || hasText -> TelegramBundleType.COMPACT_2ER
            else -> TelegramBundleType.SINGLE
        }
    }

    /** Creates a TelegramMessageBundle from a cohesive candidate. */
    private fun createBundle(timestamp: Long, messages: List<TgMessage>): TelegramMessageBundle {
        val bundleType = classifyBundle(messages)

        // Extract messages by type
        val videoMessages = messages.filter { isVideo(it) }
        val textMessage = messages.find { isText(it) }
        val photoMessage = messages.find { isPhoto(it) }

        // If no videos, this is not a valid bundle - return SINGLE with first message
        if (videoMessages.isEmpty()) {
            val firstMessage = messages.first()
            return TelegramMessageBundle.single(firstMessage)
        }

        return TelegramMessageBundle(
                timestamp = timestamp,
                messages = messages,
                bundleType = bundleType,
                videoMessages = videoMessages,
                textMessage = textMessage,
                photoMessage = photoMessage,
        )
    }

    /** Checks if a message contains VIDEO content. */
    private fun isVideo(message: TgMessage): Boolean =
            message.content is TgContent.Video ||
                    (message.content is TgContent.Document &&
                            isVideoDocument(message.content as TgContent.Document))

    /** Checks if a Document is actually a video (by MIME type). */
    private fun isVideoDocument(doc: TgContent.Document): Boolean =
            doc.mimeType?.startsWith("video/") == true

    /** Checks if a message contains PHOTO content. */
    private fun isPhoto(message: TgMessage): Boolean = message.content is TgContent.Photo

    /**
     * Checks if a message is a TEXT message (no media content, but has caption data).
     *
     * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md:
     * - TgContent.Text is the primary TEXT indicator from transport
     * - content=null or Unknown are fallback cases
     *
     * In Telegram exports, TEXT messages with structured metadata appear as messages with explicit
     * Text content or content=null.
     */
    private fun isText(message: TgMessage): Boolean =
            message.content is TgContent.Text ||
                    message.content == null ||
                    message.content is TgContent.Unknown
}
