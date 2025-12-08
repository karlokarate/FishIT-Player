package com.fishit.player.pipeline.telegram.mapper

import dev.g000sha256.tdl.dto.Audio
import dev.g000sha256.tdl.dto.Document
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.Photo
import dev.g000sha256.tdl.dto.PhotoSize
import dev.g000sha256.tdl.dto.RemoteFile
import dev.g000sha256.tdl.dto.Video

/**
 * Test fixtures for g000sha256 TDLib DTOs.
 *
 * Creates REAL DTO instances instead of mocks, since g000sha256 DTOs are final data classes
 * that cannot be mocked by MockK without special configuration.
 *
 * Based on real Telegram export data from legacy/docs/telegram/exports/exports/.
 */
object TdlibTestFixtures {

    // ========== Core File DTOs ==========

    fun createLocalFile(
        path: String = "",
        canBeDownloaded: Boolean = true,
        canBeDeleted: Boolean = false,
        isDownloadingActive: Boolean = false,
        isDownloadingCompleted: Boolean = false,
        downloadOffset: Long = 0L,
        downloadedPrefixSize: Long = 0L,
        downloadedSize: Long = 0L
    ): LocalFile = LocalFile(
        path,
        canBeDownloaded,
        canBeDeleted,
        isDownloadingActive,
        isDownloadingCompleted,
        downloadOffset,
        downloadedPrefixSize,
        downloadedSize
    )

    fun createRemoteFile(
        id: String = "BAACAgQAAx0CQ4c-xwACBUZpKbKk",
        uniqueId: String = "AgADdhYAAmObuFA",
        isUploadingActive: Boolean = false,
        isUploadingCompleted: Boolean = true,
        uploadedSize: Long = 0L
    ): RemoteFile = RemoteFile(
        id,
        uniqueId,
        isUploadingActive,
        isUploadingCompleted,
        uploadedSize
    )

    fun createFile(
        id: Int = 5255,
        size: Long = 3737448228L,
        expectedSize: Long = 3737448228L,
        local: LocalFile = createLocalFile(),
        remote: RemoteFile = createRemoteFile()
    ): File = File(id, size, expectedSize, local, remote)

    // ========== Content DTOs ==========

    /**
     * Create a Video DTO.
     *
     * Constructor: (duration, width, height, fileName, mimeType, hasStickers, supportsStreaming,
     *               minithumbnail, thumbnail, video)
     */
    fun createVideo(
        duration: Int = 4696,
        width: Int = 1920,
        height: Int = 1080,
        fileName: String = "Die Olsenbande in feiner Gesellschaft 3D - 2010.mp4",
        mimeType: String = "video/mp4",
        hasStickers: Boolean = false,
        supportsStreaming: Boolean = true,
        file: File = createFile()
    ): Video = Video(
        duration,
        width,
        height,
        fileName,
        mimeType,
        hasStickers,
        supportsStreaming,
        null, // minithumbnail
        null, // thumbnail
        file
    )

    /**
     * Create an Audio DTO.
     *
     * Constructor: (duration, title, performer, fileName, mimeType,
     *               albumCoverMinithumbnail, albumCoverThumbnail, externalAlbumCovers, audio)
     */
    fun createAudio(
        duration: Int = 186,
        title: String = "Hamsterrad Tristesse",
        performer: String = "ZOË MË",
        fileName: String = "ZOË MË - Hamsterrad Tristesse.mp3",
        mimeType: String = "audio/mpeg",
        file: File = createFile(id = 4859, size = 7506591L, expectedSize = 7506591L)
    ): Audio = Audio(
        duration,
        title,
        performer,
        fileName,
        mimeType,
        null, // albumCoverMinithumbnail
        null, // albumCoverThumbnail
        emptyArray(), // externalAlbumCovers
        file
    )

    /**
     * Create a Document DTO.
     *
     * Constructor: (fileName, mimeType, minithumbnail, thumbnail, document)
     */
    fun createDocument(
        fileName: String = "Chain Letter - 3D.zip.008",
        mimeType: String = "application/octet-stream",
        file: File = createFile(id = 19584, size = 1030435318L, expectedSize = 1030435318L)
    ): Document = Document(
        fileName,
        mimeType,
        null, // minithumbnail
        null, // thumbnail
        file
    )

    /**
     * Create a PhotoSize DTO.
     *
     * Constructor: (type, photo, width, height, progressiveSizes)
     */
    fun createPhotoSize(
        type: String = "y",
        width: Int = 1920,
        height: Int = 1080,
        file: File = createFile()
    ): PhotoSize = PhotoSize(
        type,
        file,
        width,
        height,
        intArrayOf() // progressiveSizes
    )

    /**
     * Create a Photo DTO.
     *
     * Constructor: (hasStickers, minithumbnail, sizes)
     */
    fun createPhoto(
        hasStickers: Boolean = false,
        sizes: Array<PhotoSize> = arrayOf(createPhotoSize())
    ): Photo = Photo(
        hasStickers,
        null, // minithumbnail
        sizes
    )

    fun createFormattedText(
        text: String = "",
        entities: Array<dev.g000sha256.tdl.dto.TextEntity> = emptyArray()
    ): FormattedText = FormattedText(text, entities)

    // ========== Message Content DTOs ==========

    /**
     * Create a MessageVideo content.
     *
     * Constructor: (video, alternativeVideos, videoStoryboards, photo, 
     *               effectVideoDownloadSize, caption, showCaptionAboveMedia,
     *               hasSpoiler, isSecret)
     */
    fun createMessageVideo(
        video: Video = createVideo(),
        caption: FormattedText = createFormattedText()
    ): MessageVideo = MessageVideo(
        video,
        emptyArray(), // alternativeVideos
        emptyArray(), // videoStoryboards
        null, // photo
        0, // effectVideoDownloadSize
        caption,
        false, // showCaptionAboveMedia
        false, // hasSpoiler
        false  // isSecret
    )

    /**
     * Create a MessageAudio content.
     *
     * Constructor: (audio, caption)
     */
    fun createMessageAudio(
        audio: Audio = createAudio(),
        caption: FormattedText = createFormattedText()
    ): MessageAudio = MessageAudio(audio, caption)

    /**
     * Create a MessageDocument content.
     *
     * Constructor: (document, caption)
     */
    fun createMessageDocument(
        document: Document = createDocument(),
        caption: FormattedText = createFormattedText()
    ): MessageDocument = MessageDocument(document, caption)

    /**
     * Create a MessagePhoto content.
     *
     * Constructor: (photo, caption, showCaptionAboveMedia, hasSpoiler, isSecret)
     */
    fun createMessagePhoto(
        photo: Photo = createPhoto(),
        caption: FormattedText = createFormattedText()
    ): MessagePhoto = MessagePhoto(
        photo,
        caption,
        false, // showCaptionAboveMedia
        false, // hasSpoiler
        false  // isSecret
    )

    // ========== Message Sender ==========

    fun createMessageSenderUser(userId: Long = 123L): MessageSenderUser = MessageSenderUser(userId)

    // ========== Full Message ==========

    /**
     * Create a full Message DTO.
     *
     * This is complex with 37 parameters. We use nulls/defaults for most fields
     * and only populate what TdlibMessageMapper actually uses.
     */
    fun createMessage(
        id: Long = 1415577600L,
        chatId: Long = -1001132936903L,
        date: Int = 1721162770,
        mediaAlbumId: Long = 0L,
        content: dev.g000sha256.tdl.dto.MessageContent,
        senderId: dev.g000sha256.tdl.dto.MessageSender = createMessageSenderUser()
    ): Message = Message(
        id,
        senderId,
        chatId,
        null, // sendingState
        null, // schedulingState
        false, // isOutgoing
        false, // isPinned
        false, // isFromOffline
        true,  // canBeSaved
        true,  // hasTimestampedMedia
        true,  // isChannelPost
        false, // isPaidStarSuggestedPost
        false, // isPaidTonSuggestedPost
        false, // containsUnreadMention
        date,
        0, // editDate
        null, // forwardInfo
        null, // importInfo
        null, // interactionInfo
        emptyArray(), // unreadReactions
        null, // factCheck
        null, // suggestedPostInfo
        null, // replyTo
        null, // topic
        null, // selfDestructType
        0.0, // selfDestructIn
        0.0, // autoDeleteIn
        0L, // viaBotUserId
        0L, // senderBusinessBotUserId
        0, // senderBoostCount
        0L, // paidMessageStarCount
        "", // authorSignature
        mediaAlbumId,
        0L, // effectId
        null, // restrictionInfo
        content,
        null  // replyMarkup
    )

    // ========== Convenience Methods for Tests ==========

    /**
     * Create a complete video Message ready for mapping tests.
     * Based on real export: Die Olsenbande in feiner Gesellschaft 3D - 2010
     */
    fun createVideoMessage(
        messageId: Long = 1415577600L,
        chatId: Long = -1001132936903L,
        fileName: String = "Die Olsenbande in feiner Gesellschaft 3D - 2010.mp4",
        caption: String = "Die Olsenbande in feiner Gesellschaft 3D - 2010",
        mimeType: String = "video/mp4",
        sizeBytes: Long = 3737448228L,
        duration: Int = 4696,
        width: Int = 1920,
        height: Int = 1080,
        supportsStreaming: Boolean = true,
        remoteId: String = "BAACAgQAAx0CQ4c-xwACBUZpKbKkepQm0rwffxJ2cY-EQpz77QACdhYAAmObuFCBnr_HxAH_PzgE",
        uniqueId: String = "AgADdhYAAmObuFA",
        fileId: Int = 5255,
        mediaAlbumId: Long = 0L,
        localPath: String = "",
        isDownloadCompleted: Boolean = false
    ): Message {
        val localFile = createLocalFile(
            path = localPath,
            isDownloadingCompleted = isDownloadCompleted
        )
        val remoteFile = createRemoteFile(id = remoteId, uniqueId = uniqueId)
        val file = createFile(id = fileId, size = sizeBytes, expectedSize = sizeBytes, local = localFile, remote = remoteFile)
        val video = createVideo(
            duration = duration,
            width = width,
            height = height,
            fileName = fileName,
            mimeType = mimeType,
            supportsStreaming = supportsStreaming,
            file = file
        )
        val content = createMessageVideo(video = video, caption = createFormattedText(caption))
        return createMessage(
            id = messageId,
            chatId = chatId,
            mediaAlbumId = mediaAlbumId,
            content = content
        )
    }

    /**
     * Create a complete audio Message ready for mapping tests.
     * Based on real export: ZOË MË - Hamsterrad Tristesse
     */
    fun createAudioMessage(
        messageId: Long = 408798887936L,
        chatId: Long = -1001088043136L,
        audioTitle: String = "Hamsterrad Tristesse",
        performer: String = "ZOË MË",
        fileName: String = "ZOË MË - Hamsterrad Tristesse.mp3",
        mimeType: String = "audio/mpeg",
        sizeBytes: Long = 7506591L,
        duration: Int = 186,
        remoteId: String = "CQACAgIAAx0CQNo4gAABBfLlaSmyV_b-BRAcA5I3vIsRP4GKHuEAAnaIAALB2TFJ9VInbdfbcww4BA",
        uniqueId: String = "AgADdogAAsHZMUk",
        fileId: Int = 4859
    ): Message {
        val localFile = createLocalFile()
        val remoteFile = createRemoteFile(id = remoteId, uniqueId = uniqueId)
        val file = createFile(id = fileId, size = sizeBytes, expectedSize = sizeBytes, local = localFile, remote = remoteFile)
        val audio = createAudio(
            duration = duration,
            title = audioTitle,
            performer = performer,
            fileName = fileName,
            mimeType = mimeType,
            file = file
        )
        val content = createMessageAudio(audio = audio, caption = createFormattedText())
        return createMessage(
            id = messageId,
            chatId = chatId,
            content = content
        )
    }

    /**
     * Create a complete document Message ready for mapping tests.
     * Based on real export: Chain Letter - 3D.zip.008
     */
    fun createDocumentMessage(
        messageId: Long = 1412431872L,
        chatId: Long = -1001132936903L,
        fileName: String = "Chain Letter - 3D.zip.008",
        caption: String = "",
        mimeType: String = "application/octet-stream",
        sizeBytes: Long = 1030435318L,
        remoteId: String = "BQACAgIAAx0CQ4c-xwACBUNpKbKkmU4kLmvSO1-4CkxltUsznQACuUcAAovXsUjX_24OKsI_UjgE",
        uniqueId: String = "AgADuUcAAovXsUg",
        fileId: Int = 19584
    ): Message {
        val localFile = createLocalFile()
        val remoteFile = createRemoteFile(id = remoteId, uniqueId = uniqueId)
        val file = createFile(id = fileId, size = sizeBytes, expectedSize = sizeBytes, local = localFile, remote = remoteFile)
        val document = createDocument(fileName = fileName, mimeType = mimeType, file = file)
        val content = createMessageDocument(document = document, caption = createFormattedText(caption))
        return createMessage(
            id = messageId,
            chatId = chatId,
            content = content
        )
    }

    /**
     * Create a complete photo Message ready for mapping tests.
     */
    fun createPhotoMessage(
        messageId: Long = 1414529024L,
        chatId: Long = -1001132936903L,
        caption: String = "",
        largestWidth: Int = 1920,
        largestHeight: Int = 1080,
        sizeBytes: Long = 262665L,
        remoteId: String = "AgACAgIAAx0CQ4c-xwACBUVpKbKkFW3pdtzTs5O1bVCviNxoUAAC",
        uniqueId: String = "AQADhOMxGxC8uEh-",
        fileId: Int = 19583
    ): Message {
        val localFile = createLocalFile()
        val remoteFile = createRemoteFile(id = remoteId, uniqueId = uniqueId)
        val file = createFile(id = fileId, size = sizeBytes, expectedSize = sizeBytes, local = localFile, remote = remoteFile)
        val photoSize = createPhotoSize(width = largestWidth, height = largestHeight, file = file)
        val photo = createPhoto(sizes = arrayOf(photoSize))
        val content = createMessagePhoto(photo = photo, caption = createFormattedText(caption))
        return createMessage(
            id = messageId,
            chatId = chatId,
            content = content
        )
    }

    /**
     * Create a photo Message with multiple sizes.
     */
    fun createPhotoMessageWithMultipleSizes(
        messageId: Long = 1L,
        chatId: Long = 100L,
        sizes: List<Triple<Int, Int, Long>>, // width, height, sizeBytes
        remoteId: String = "photo_multi_remote",
        uniqueId: String = "photo_multi_unique"
    ): Message {
        val photoSizes = sizes.mapIndexed { index, (w, h, size) ->
            val localFile = createLocalFile()
            val remoteFile = createRemoteFile(id = remoteId, uniqueId = uniqueId)
            val file = createFile(id = 100 + index, size = size, expectedSize = size, local = localFile, remote = remoteFile)
            createPhotoSize(width = w, height = h, file = file)
        }.toTypedArray()
        val photo = createPhoto(sizes = photoSizes)
        val content = createMessagePhoto(photo = photo, caption = createFormattedText())
        return createMessage(
            id = messageId,
            chatId = chatId,
            content = content
        )
    }
}
