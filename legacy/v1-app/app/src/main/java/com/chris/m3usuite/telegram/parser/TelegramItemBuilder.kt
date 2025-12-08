package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.domain.MessageBlock
import com.chris.m3usuite.telegram.domain.TelegramDocumentRef
import com.chris.m3usuite.telegram.domain.TelegramImageRef
import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.domain.TelegramMediaRef
import com.chris.m3usuite.telegram.domain.TelegramMetadata
import com.chris.m3usuite.core.logging.UnifiedLog

/**
 * Builds TelegramItem domain objects from MessageBlocks.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Sections 5.3 and 6.2:
 *
 * Anchor selection:
 * - If any ExportVideo exists → anchor = best video (largest resolution or longest duration)
 * - Else if photo+text exists → POSTER_ONLY
 * - Else if RAR/AUDIOBOOK extensions → documentRef
 *
 * Poster/backdrop selection:
 * - Poster: aspect ratio ≤ 0.85 (portrait)
 * - Backdrop: aspect ratio ≥ 1.6 (landscape)
 * - Fallback: highest resolution photo
 * - If no photo → poster = video thumbnail; backdrop may be null
 *
 * IDs:
 * - Use nested remoteId/uniqueId/fileId from the ExportMessage structure
 */
object TelegramItemBuilder {
    private const val TAG = "TelegramItemBuilder"

    /** Aspect ratio threshold for poster (portrait) images */
    private const val POSTER_ASPECT_RATIO_MAX = 0.85

    /** Aspect ratio threshold for backdrop (landscape) images */
    private const val BACKDROP_ASPECT_RATIO_MIN = 1.6

    /** File extensions that indicate RAR/archive content */
    private val ARCHIVE_EXTENSIONS = setOf("rar", "zip", "7z", "tar", "gz", "bz2")

    /** File extensions that indicate audiobook content */
    private val AUDIOBOOK_EXTENSIONS = setOf("mp3", "m4b", "aac", "flac", "ogg", "wma")

    /** Chat title indicators for audiobook content */
    private val AUDIOBOOK_TITLE_INDICATORS = setOf("hörbuch", "audiobook")

    /**
     * Debug chat IDs for verbose logging.
     * When a block belongs to one of these chats, detailed build decisions are logged.
     * Set via [enableDebugLogging] for runtime diagnostics.
     */
    @Volatile
    private var debugChatIds: Set<Long> = emptySet()

    /**
     * Enable debug logging for specific chat IDs.
     * Call this from debug builds to get verbose logging for problematic chats.
     *
     * @param chatIds Set of chat IDs to log debug info for
     */
    fun enableDebugLogging(chatIds: Set<Long>) {
        debugChatIds = chatIds
        UnifiedLog.info(TAG, "Debug logging enabled for chats: $chatIds")
    }

    /**
     * Disable debug logging.
     */
    fun disableDebugLogging() {
        debugChatIds = emptySet()
        UnifiedLog.info(TAG, "Debug logging disabled")
    }

    /**
     * Check if debug logging is enabled for a chat.
     */
    private fun shouldDebugLog(chatId: Long): Boolean = chatId in debugChatIds

    /**
     * Build a TelegramItem from a MessageBlock.
     *
     * @param block The message block to process
     * @param chatTitle Chat title for metadata extraction and adult detection
     * @return Built TelegramItem, or null if block cannot produce a valid item
     */
    fun build(
        block: MessageBlock,
        chatTitle: String?,
    ): TelegramItem? {
        if (block.messages.isEmpty()) return null

        val chatId = block.chatId
        val debug = shouldDebugLog(chatId)

        // Separate messages by type
        val videos = block.messages.filterIsInstance<ExportVideo>()
        val photos = block.messages.filterIsInstance<ExportPhoto>()
        val texts = block.messages.filterIsInstance<ExportText>()
        val documents = block.messages.filterIsInstance<ExportDocument>()
        val audios = block.messages.filterIsInstance<ExportAudio>()

        // Debug logging for block content
        if (debug) {
            val messageIds = block.messages.map { it.id }
            UnifiedLog.debug(
                TAG,
                "[DEBUG] Chat $chatId - Block content: " +
                    "${block.messages.size} messages (IDs: $messageIds), " +
                    "${videos.size} videos, ${photos.size} photos, ${texts.size} texts, " +
                    "${documents.size} documents, ${audios.size} audios",
            )
            // Log message types
            block.messages.forEach { msg ->
                UnifiedLog.debug(
                    TAG,
                    "[DEBUG] Chat $chatId - Message ${msg.id}: ${msg::class.simpleName}",
                )
            }
        }

        // Determine item type and build accordingly
        val result =
            when {
                videos.isNotEmpty() -> buildFromVideo(videos, photos, texts, chatTitle)
                documents.isNotEmpty() -> buildFromDocument(documents, photos, texts, chatTitle)
                audios.isNotEmpty() -> buildFromAudio(audios, photos, texts, chatTitle)
                photos.isNotEmpty() && texts.isNotEmpty() -> buildPosterOnly(photos, texts, chatTitle)
                else -> null
            }

        // Debug logging for build decision
        if (debug) {
            if (result != null) {
                UnifiedLog.debug(
                    TAG,
                    "[DEBUG] Chat $chatId - Built item: type=${result.type}, " +
                        "anchorMessageId=${result.anchorMessageId}, " +
                        "title=${result.metadata.title}, " +
                        "hasPoster=${result.posterRef != null}, " +
                        "hasBackdrop=${result.backdropRef != null}, " +
                        "hasVideo=${result.videoRef != null}, " +
                        "hasDocument=${result.documentRef != null}",
                )
            } else {
                val reason =
                    when {
                        videos.isEmpty() &&
                            documents.isEmpty() &&
                            audios.isEmpty() &&
                            (photos.isEmpty() || texts.isEmpty()) -> "no suitable content combination"
                        else -> "builder returned null"
                    }
                UnifiedLog.debug(
                    TAG,
                    "[DEBUG] Chat $chatId - Block skipped, no item built: $reason",
                )
            }
        }

        return result
    }

    /**
     * Build a video-based item (MOVIE, SERIES_EPISODE, or CLIP).
     */
    private fun buildFromVideo(
        videos: List<ExportVideo>,
        photos: List<ExportPhoto>,
        texts: List<ExportText>,
        chatTitle: String?,
    ): TelegramItem? {
        // Select best video (highest resolution, then longest duration)
        val bestVideo = selectBestVideo(videos) ?: return null

        // Create video ref
        val videoRef = createMediaRef(bestVideo) ?: return null

        // Find nearest text for metadata
        val nearestText = findNearestText(bestVideo, texts)

        // Extract metadata
        val metadata = extractMetadata(nearestText, bestVideo.caption, bestVideo.video.fileName, chatTitle)

        // Determine item type
        val itemType = determineVideoItemType(bestVideo, metadata)

        // Select poster and backdrop images
        val (posterRef, backdropRef) = selectPosterAndBackdrop(photos, bestVideo)

        return TelegramItem(
            chatId = bestVideo.chatId,
            anchorMessageId = bestVideo.id,
            type = itemType,
            videoRef = videoRef,
            documentRef = null,
            posterRef = posterRef,
            backdropRef = backdropRef,
            textMessageId = nearestText?.id,
            photoMessageId = photos.firstOrNull()?.id,
            createdAtIso = bestVideo.dateIso,
            metadata = metadata,
        )
    }

    /**
     * Build a document-based item (RAR_ITEM or AUDIOBOOK).
     */
    private fun buildFromDocument(
        documents: List<ExportDocument>,
        photos: List<ExportPhoto>,
        texts: List<ExportText>,
        chatTitle: String?,
    ): TelegramItem? {
        val doc = documents.first()

        // Create document ref
        val documentRef = createDocumentRef(doc) ?: return null

        // Determine if audiobook or archive
        val isAudiobook = isAudiobookDocument(doc, chatTitle)
        val itemType = if (isAudiobook) TelegramItemType.AUDIOBOOK else TelegramItemType.RAR_ITEM

        // Find nearest text for metadata
        val nearestText = findNearestText(doc, texts)

        // Extract metadata
        val metadata = extractMetadata(nearestText, doc.caption, doc.document.fileName, chatTitle)

        // Select poster (no backdrop for documents)
        val posterRef = selectBestPhoto(photos)

        return TelegramItem(
            chatId = doc.chatId,
            anchorMessageId = doc.id,
            type = itemType,
            videoRef = null,
            documentRef = documentRef,
            posterRef = posterRef,
            backdropRef = null,
            textMessageId = nearestText?.id,
            photoMessageId = photos.firstOrNull()?.id,
            createdAtIso = doc.dateIso,
            metadata = metadata,
        )
    }

    /**
     * Build an audio-based item (AUDIOBOOK).
     */
    private fun buildFromAudio(
        audios: List<ExportAudio>,
        photos: List<ExportPhoto>,
        texts: List<ExportText>,
        chatTitle: String?,
    ): TelegramItem? {
        val audio = audios.first()

        // Create document ref from audio
        val audioFile = audio.audio.audio
        val documentRef =
            TelegramDocumentRef(
                remoteId = audioFile.getRemoteId() ?: return null,
                uniqueId = audioFile.getUniqueId() ?: return null,
                fileId = audioFile.id.takeIf { it > 0 },
                sizeBytes = audioFile.size,
                mimeType = audio.audio.mimeType,
                fileName = audio.audio.fileName,
            )

        // Find nearest text for metadata
        val nearestText = findNearestText(audio, texts)

        // Extract metadata, preferring audio title
        val baseMetadata = extractMetadata(nearestText, audio.caption, audio.audio.fileName, chatTitle)
        val audioTitle =
            listOf(audio.audio.performer, audio.audio.title)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
        val metadata =
            if (baseMetadata.title == null && audioTitle.isNotBlank()) {
                baseMetadata.copy(title = audioTitle)
            } else {
                baseMetadata
            }

        // Select poster (no backdrop for audio)
        val posterRef = selectBestPhoto(photos)

        return TelegramItem(
            chatId = audio.chatId,
            anchorMessageId = audio.id,
            type = TelegramItemType.AUDIOBOOK,
            videoRef = null,
            documentRef = documentRef,
            posterRef = posterRef,
            backdropRef = null,
            textMessageId = nearestText?.id,
            photoMessageId = photos.firstOrNull()?.id,
            createdAtIso = audio.dateIso,
            metadata = metadata,
        )
    }

    /**
     * Build a POSTER_ONLY item (photo + text but no video).
     */
    private fun buildPosterOnly(
        photos: List<ExportPhoto>,
        texts: List<ExportText>,
        chatTitle: String?,
    ): TelegramItem? {
        val photo = photos.first()
        val text = texts.first()

        // Extract metadata from text
        val metadata = TelegramMetadataExtractor.extractFromText(text, chatTitle)

        // Select poster (prefer portrait)
        val posterRef = selectBestPhoto(photos)

        return TelegramItem(
            chatId = photo.chatId,
            anchorMessageId = photo.id,
            type = TelegramItemType.POSTER_ONLY,
            videoRef = null,
            documentRef = null,
            posterRef = posterRef,
            backdropRef = null,
            textMessageId = text.id,
            photoMessageId = photo.id,
            createdAtIso = photo.dateIso,
            metadata = metadata,
        )
    }

    /**
     * Select the best video from a list based on resolution and duration.
     */
    private fun selectBestVideo(videos: List<ExportVideo>): ExportVideo? =
        videos.maxByOrNull { video ->
            // Score = resolution (width * height) + duration bonus
            val resolution = (video.video.width.toLong() * video.video.height)
            val durationBonus = video.video.duration.toLong() * 100 // Small bonus for longer videos
            resolution + durationBonus
        }

    /**
     * Create a TelegramMediaRef from an ExportVideo.
     */
    private fun createMediaRef(video: ExportVideo): TelegramMediaRef? {
        val videoFile = video.video.video
        val remoteId = videoFile.getRemoteId() ?: return null
        val uniqueId = videoFile.getUniqueId() ?: return null

        return TelegramMediaRef(
            remoteId = remoteId,
            uniqueId = uniqueId,
            fileId = videoFile.id.takeIf { it > 0 },
            sizeBytes = videoFile.size,
            mimeType = video.video.mimeType.takeIf { it.isNotBlank() },
            durationSeconds = video.video.duration.takeIf { it > 0 },
            width = video.video.width.takeIf { it > 0 },
            height = video.video.height.takeIf { it > 0 },
        )
    }

    /**
     * Create a TelegramDocumentRef from an ExportDocument.
     */
    private fun createDocumentRef(doc: ExportDocument): TelegramDocumentRef? {
        val docFile = doc.document.document
        val remoteId = docFile.getRemoteId() ?: return null
        val uniqueId = docFile.getUniqueId() ?: return null

        return TelegramDocumentRef(
            remoteId = remoteId,
            uniqueId = uniqueId,
            fileId = docFile.id.takeIf { it > 0 },
            sizeBytes = docFile.size,
            mimeType = doc.document.mimeType.takeIf { it.isNotBlank() },
            fileName = doc.document.fileName.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Find the nearest ExportText message by time.
     */
    private fun findNearestText(
        anchor: ExportMessage,
        texts: List<ExportText>,
    ): ExportText? = texts.minByOrNull { kotlin.math.abs(it.dateEpochSeconds - anchor.dateEpochSeconds) }

    /**
     * Extract metadata from available sources.
     */
    private fun extractMetadata(
        text: ExportText?,
        caption: String?,
        fileName: String?,
        chatTitle: String?,
    ): TelegramMetadata {
        // Primary: from text message
        val textMetadata = text?.let { TelegramMetadataExtractor.extractFromText(it, chatTitle) }

        // Secondary: from filename/caption
        val fileMetadata = TelegramMetadataExtractor.extractFromFilename(fileName, caption, chatTitle)

        // Merge with text taking precedence
        return textMetadata?.let { TelegramMetadataExtractor.merge(it, fileMetadata) }
            ?: fileMetadata
    }

    /**
     * Determine the video item type based on content.
     */
    private fun determineVideoItemType(
        video: ExportVideo,
        metadata: TelegramMetadata,
    ): TelegramItemType {
        // Check for series indicators in filename or title
        val fileName = video.video.fileName.lowercase()
        val title = metadata.title?.lowercase() ?: ""

        // Episode patterns: S01E01, 1x01, Episode 1, etc.
        val episodePattern = Regex("""s\d+e\d+|(\d+)x(\d+)|episode\s*\d+|ep\s*\d+|folge\s*\d+""", RegexOption.IGNORE_CASE)
        if (episodePattern.containsMatchIn(fileName) || episodePattern.containsMatchIn(title)) {
            return TelegramItemType.SERIES_EPISODE
        }

        // Short clips (< 15 minutes and no metadata suggesting movie)
        val duration = video.video.duration
        if (duration > 0 && duration < 900 && metadata.lengthMinutes == null) {
            return TelegramItemType.CLIP
        }

        // Default to movie
        return TelegramItemType.MOVIE
    }

    /**
     * Select poster and backdrop images from photos.
     *
     * Per contract:
     * - Poster: aspect ratio ≤ 0.85 (portrait)
     * - Backdrop: aspect ratio ≥ 1.6 (landscape)
     * - Fallback: highest resolution for both
     * - If no photo: use video thumbnail as poster
     */
    private fun selectPosterAndBackdrop(
        photos: List<ExportPhoto>,
        video: ExportVideo?,
    ): Pair<TelegramImageRef?, TelegramImageRef?> {
        // Collect all photo sizes
        val allSizes =
            photos.flatMap { photo ->
                photo.sizes.map { size -> size to photo }
            }

        if (allSizes.isEmpty()) {
            // Fallback to video thumbnail
            val thumbnail = video?.video?.thumbnail?.let { createImageRefFromThumbnail(it) }
            return thumbnail to null
        }

        // Find best poster (aspect ratio ≤ 0.85)
        val posterCandidate =
            allSizes
                .filter { (size, _) ->
                    val aspectRatio = size.width.toDouble() / size.height
                    aspectRatio <= POSTER_ASPECT_RATIO_MAX
                }.maxByOrNull { (size, _) -> size.width.toLong() * size.height }
                ?.let { (size, _) -> createImageRefFromSize(size) }

        // Find best backdrop (aspect ratio ≥ 1.6)
        val backdropCandidate =
            allSizes
                .filter { (size, _) ->
                    val aspectRatio = size.width.toDouble() / size.height
                    aspectRatio >= BACKDROP_ASPECT_RATIO_MIN
                }.maxByOrNull { (size, _) -> size.width.toLong() * size.height }
                ?.let { (size, _) -> createImageRefFromSize(size) }

        // Fallback: highest resolution photo
        val fallbackRef =
            allSizes
                .maxByOrNull { (size, _) -> size.width.toLong() * size.height }
                ?.let { (size, _) -> createImageRefFromSize(size) }

        val posterRef = posterCandidate ?: fallbackRef
        val backdropRef = backdropCandidate

        return posterRef to backdropRef
    }

    /**
     * Select the best photo as a simple image ref.
     */
    private fun selectBestPhoto(photos: List<ExportPhoto>): TelegramImageRef? {
        val allSizes = photos.flatMap { it.sizes }
        return allSizes
            .maxByOrNull { it.width.toLong() * it.height }
            ?.let { createImageRefFromSize(it) }
    }

    /**
     * Create an image ref from a photo size.
     */
    private fun createImageRefFromSize(size: ExportPhotoSize): TelegramImageRef? {
        val file = size.getFileRef()
        val remoteId = file.getRemoteId() ?: return null
        val uniqueId = file.getUniqueId() ?: return null

        return TelegramImageRef(
            remoteId = remoteId,
            uniqueId = uniqueId,
            fileId = file.id.takeIf { it > 0 },
            width = size.width,
            height = size.height,
            sizeBytes = file.size,
        )
    }

    /**
     * Create an image ref from a video thumbnail.
     */
    private fun createImageRefFromThumbnail(thumbnail: ExportThumbnail): TelegramImageRef? {
        val remoteId = thumbnail.file.getRemoteId() ?: return null
        val uniqueId = thumbnail.file.getUniqueId() ?: return null

        return TelegramImageRef(
            remoteId = remoteId,
            uniqueId = uniqueId,
            fileId = thumbnail.file.id.takeIf { it > 0 },
            width = thumbnail.width,
            height = thumbnail.height,
            sizeBytes = thumbnail.file.size,
        )
    }

    /**
     * Check if a document is an audiobook.
     */
    private fun isAudiobookDocument(
        doc: ExportDocument,
        chatTitle: String?,
    ): Boolean {
        // Check file extension
        val extension =
            doc.document.fileName
                .substringAfterLast('.')
                .lowercase()
        if (extension in AUDIOBOOK_EXTENSIONS) return true

        // Check chat title
        val titleLower = chatTitle?.lowercase() ?: ""
        if (AUDIOBOOK_TITLE_INDICATORS.any { titleLower.contains(it) }) return true

        return false
    }
}
