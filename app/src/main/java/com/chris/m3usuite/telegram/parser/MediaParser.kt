package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.models.*
import dev.g000sha256.tdl.dto.*

/**
 * Parser for extracting media information from Telegram messages.
 * Supports movies, series, episodes, archives, and invite links.
 * Uses pattern matching on filenames and message captions to extract metadata.
 */
object MediaParser {
    private val reTitle =
        Regex(
            """Titel:\s*([^T\n]+?)(?=(?:Originaltitel:|Erscheinungsjahr:|L[aä]nge:|Produktionsland:|FSK:|Filmreihe:|Regie:|TMDbRating:|Genres:|Episoden:|$))""",
            RegexOption.IGNORE_CASE,
        )

    private val reOriginalTitle =
        Regex(
            """Originaltitel:\s*([^O\n]+?)(?=(?:Erscheinungsjahr:|L[aä]nge:|Produktionsland:|FSK:|Filmreihe:|Regie:|TMDbRating:|Genres:|Episoden:|$))""",
            RegexOption.IGNORE_CASE,
        )

    private val reYear = Regex("""Erscheinungsjahr:\s*(\d{4})""", RegexOption.IGNORE_CASE)
    private val reLengthMinutes = Regex("""L[aä]nge:\s*(\d+)\s*Minuten""", RegexOption.IGNORE_CASE)
    private val reCountry =
        Regex(
            """Produktionsland:\s*([^F\n]+?)(?=(?:FSK:|Filmreihe:|Regie:|TMDbRating:|Genres:|Episoden:|$))""",
            RegexOption.IGNORE_CASE,
        )
    private val reFsk = Regex("""FSK:\s*(\d{1,2})""", RegexOption.IGNORE_CASE)
    private val reCollection =
        Regex(
            """Filmreihe:\s*([^F\n]+?)(?=(?:Regie:|TMDbRating:|Genres:|Episoden:|$))""",
            RegexOption.IGNORE_CASE,
        )
    private val reDirector =
        Regex(
            """Regie:\s*([^T\n]+?)(?=(?:TMDbRating:|Genres:|Episoden:|$))""",
            RegexOption.IGNORE_CASE,
        )
    private val reTmdbRating =
        Regex(
            """TMDbRating:\s*([\d.,]+)(?:\s+bei\s+(\d+)\s+Stimmen)?""",
            RegexOption.IGNORE_CASE,
        )
    private val reGenres = Regex("""Genres:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val reEpisodes =
        Regex(
            """Episoden:\s*(\d+)\s+Episoden(?:\s+in\s+(\d+)\s+Staffeln)?""",
            RegexOption.IGNORE_CASE,
        )
    private val reInvite = Regex("""https?://t\.me/\S+|t\.me/\S+""", RegexOption.IGNORE_CASE)
    private val reSeasonEpisode = Regex("""[Ss](\d{1,2})\s*[Ee](\d{1,2})""")
    private val reAdultWords = Regex("""\b(porn|sex|xxx|18\+|🔞|creampie|anal|bds[mn])\b""", RegexOption.IGNORE_CASE)

    /**
     * Parse a Telegram message and extract media information, sub-chat references, or invite links.
     */
    fun parseMessage(
        chatId: Long,
        chatTitle: String?,
        message: Message,
        recentMessages: List<Message> = emptyList(),
    ): ParsedItem {
        parseMedia(chatId, chatTitle, message, recentMessages)?.let { return ParsedItem.Media(it) }
        parseSubChatRef(chatId, chatTitle, message)?.let { return ParsedItem.SubChat(it) }
        parseInvite(chatId, message)?.let { return ParsedItem.Invite(it) }
        return ParsedItem.None(chatId, message.id)
    }

    /**
     * Parse structured movie chat with 3-message pattern: Video + Meta + Poster.
     * 
     * Expected pattern (in descending order by messageId):
     * - i: MessageVideo with .mp4
     * - i+1: MessageText with metadata (Titel:, Erscheinungsjahr:, etc.)
     * - i+2: MessagePhoto (poster)
     * 
     * Returns list of ParsedItem where:
     * - Video becomes ParsedItem.Media with full metadata
     * - Meta and Poster are marked as consumed (isConsumed=true)
     * 
     * @param chatContext Chat context with isStructuredMovieChat flag
     * @param messages List of messages sorted descending by messageId
     * @return List of ParsedItem (Media items and consumed markers)
     */
    fun parseStructuredMovieChat(
        chatContext: ChatContext,
        messages: List<Message>,
    ): List<ParsedItem> {
        if (!chatContext.isStructuredMovieChat || messages.size < 3) {
            return emptyList()
        }

        val results = mutableListOf<ParsedItem>()
        val consumedIds = mutableSetOf<Long>()
        var i = 0

        while (i <= messages.size - 3) {
            val msg0 = messages[i]
            val msg1 = messages[i + 1]
            val msg2 = messages[i + 2]

            // Check for pattern: Video, Text, Photo
            val isPattern =
                msg0.content is MessageVideo &&
                    msg1.content is MessageText &&
                    msg2.content is MessagePhoto

            if (isPattern) {
                val videoContent = msg0.content as MessageVideo
                val textContent = msg1.content as MessageText
                val photoContent = msg2.content as MessagePhoto

                // Validate video file
                val video = videoContent.video
                val fileName = video.fileName
                val fileId = video.video?.id
                val fileUniqueId = video.video?.remote?.uniqueId

                // Only create media if fileId is valid
                if (fileId != null && fileId > 0 && !fileName.isNullOrBlank()) {
                    // Check if filename is .mp4
                    val isMp4 = fileName.endsWith(".mp4", ignoreCase = true)

                    if (isMp4) {
                        // Parse metadata from text
                        val metaText = textContent.text?.text.orEmpty()
                        val meta = parseMetaFromText(metaText)

                        // Extract poster info
                        val photo = photoContent.photo
                        val posterFileId = photo.sizes?.maxByOrNull { it.width }?.photo?.id

                        // Detect series from filename/caption
                        val allText = combineTextForAnalysis(fileName, videoContent.caption?.text.orEmpty())
                        val seasonEp = TgContentHeuristics.guessSeasonEpisode(allText)
                        val isSeries = seasonEp != null && (seasonEp.season != null || seasonEp.episode != null)
                        val kind = if (isSeries) MediaKind.EPISODE else MediaKind.MOVIE

                        // Extract series name if episode
                        val seriesName = if (isSeries) {
                            extractSeriesName(fileName, seasonEp)
                        } else null

                        // Create MediaInfo for video with full metadata
                        val mediaInfo = MediaInfo(
                            chatId = chatContext.chatId,
                            messageId = msg0.id,
                            kind = kind,
                            chatTitle = chatContext.chatTitle,
                            fileName = fileName,
                            mimeType = video.mimeType,
                            sizeBytes = video.video?.size?.toLong(),
                            fileId = fileId,
                            fileUniqueId = fileUniqueId,
                            title = meta?.title,
                            year = meta?.year,
                            genres = meta?.genres ?: emptyList(),
                            fsk = meta?.fsk,
                            durationMinutes = meta?.durationMinutes,
                            country = meta?.country,
                            director = meta?.director,
                            tmdbRating = meta?.tmdbRating,
                            tmdbVotes = meta?.tmdbVotes,
                            posterFileId = posterFileId,
                            seasonNumber = seasonEp?.season,
                            episodeNumber = seasonEp?.episode,
                            seriesName = seriesName,
                            extraInfo = meta?.extraInfo,
                        )

                        results.add(ParsedItem.Media(mediaInfo))

                        // Mark meta and poster messages as consumed
                        consumedIds.add(msg1.id)
                        consumedIds.add(msg2.id)

                        // Skip the processed triplet
                        i += 3
                        continue
                    }
                }
            }

            // No pattern match, move to next message
            i++
        }

        // No need to add consumed markers for meta/poster messages; these are filtered out downstream.

        return results
    }

    /**
     * Extract series name from filename by removing season/episode patterns.
     */
    internal fun extractSeriesName(
        fileName: String,
        seasonEp: TgContentHeuristics.SeasonEpisode?,
    ): String? {
        if (seasonEp == null) return null

        // Remove file extension
        val base = fileName.substringBeforeLast('.')

        // Remove the season/episode pattern
        val cleaned = when (seasonEp.pattern) {
            "SxxEyy" -> base.replace(Regex("""[Ss]\d{1,2}[Ee]\d{1,3}"""), "")
            "XxY" -> base.replace(Regex("""\d{1,2}x\d{1,3}"""), "")
            "Episode X" -> base.replace(Regex("""[Ee]pisode[.\s]*\d{1,3}""", RegexOption.IGNORE_CASE), "")
            "Ep X" -> base.replace(Regex("""[Ee]p[.\s]*\d{1,3}""", RegexOption.IGNORE_CASE), "")
            "Folge X" -> base.replace(Regex("""[Ff]olge[.\s]*\d{1,3}""", RegexOption.IGNORE_CASE), "")
            else -> base
        }

        // Clean up separators and whitespace
        return cleaned
            .replace(Regex("""[._-]+"""), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    /**
     * Combine filename and caption text for analysis.
     */
    private fun combineTextForAnalysis(
        fileName: String?,
        captionText: String,
    ): String = (fileName ?: "") + " " + captionText

    /**
     * Select appropriate title from caption or filename.
     * Prefers caption if it doesn't contain metadata markers,
     * otherwise uses parsed title from filename.
     */
    private fun selectTitle(
        captionText: String,
        parsedTitle: String?,
        fileName: String?,
    ): String? =
        if (captionText.isNotBlank() && !captionText.contains("Titel:", ignoreCase = true)) {
            captionText
        } else {
            parsedTitle ?: fileName?.substringBeforeLast('.') ?: fileName
        }

    private fun parseMedia(
        chatId: Long,
        chatTitle: String?,
        message: Message,
        recentMessages: List<Message>,
    ): MediaInfo? {
        val content = message.content

        return when (content) {
            is MessageVideo -> {
                val video = content.video
                val name = video.fileName?.takeIf { it.isNotBlank() }
                
                // Extract file IDs
                val fileId = video.video?.id
                val fileUniqueId = video.video?.remote?.uniqueId
                
                // Get thumbnail for fallback poster
                val thumbFileId = video.thumbnail?.file?.id
                
                // Parse metadata from filename and caption
                val metaFromName = parseMediaFromFileName(name)
                val captionText = content.caption?.text.orEmpty()
                val metaFromText = parseMetaFromText(captionText)
                
                // Detect series from filename or caption
                val allText = combineTextForAnalysis(name, captionText)
                val seasonEp = TgContentHeuristics.guessSeasonEpisode(allText)
                val isSeries = seasonEp != null && (seasonEp.season != null || seasonEp.episode != null)
                
                val kind =
                    if (isAdultChannel(chatTitle, captionText)) {
                        MediaKind.ADULT
                    } else {
                        if (isSeries || metaFromName?.seasonNumber != null) MediaKind.EPISODE else MediaKind.MOVIE
                    }

                // Extract series name if episode
                val seriesName = if (isSeries && name != null) {
                    extractSeriesName(name, seasonEp)
                } else null

                val base =
                    metaFromName?.copy(
                        kind = kind,
                        chatId = chatId,
                        messageId = message.id,
                        chatTitle = chatTitle,
                        fileId = fileId,
                        fileUniqueId = fileUniqueId,
                        seasonNumber = seasonEp?.season ?: metaFromName.seasonNumber,
                        episodeNumber = seasonEp?.episode ?: metaFromName.episodeNumber,
                        seriesName = seriesName,
                        title = selectTitle(captionText, metaFromName.title, name),
                    ) ?: MediaInfo(
                        chatId = chatId,
                        messageId = message.id,
                        kind = kind,
                        chatTitle = chatTitle,
                        fileName = name,
                        mimeType = video.mimeType,
                        sizeBytes = video.video?.size?.toLong(),
                        fileId = fileId,
                        fileUniqueId = fileUniqueId,
                        seasonNumber = seasonEp?.season,
                        episodeNumber = seasonEp?.episode,
                        seriesName = seriesName,
                        title = selectTitle(captionText, null, name),
                    )

                mergeMeta(base, metaFromText).copy(
                    posterFileId = thumbFileId, // Use video thumbnail as fallback poster
                )
            }

            is MessageDocument -> {
                val doc = content.document
                val name = doc.fileName?.takeIf { it.isNotBlank() }
                val isArchive = name?.matches(Regex(""".*\.(rar|zip|7z|tar|gz|bz2)$""", RegexOption.IGNORE_CASE)) == true
                
                // Extract file IDs
                val fileId = doc.document?.id
                val fileUniqueId = doc.document?.remote?.uniqueId
                
                // Get thumbnail for fallback poster
                val thumbFileId = doc.thumbnail?.file?.id
                
                // Check if it's a video file (mp4, mkv, avi, etc.)
                val isVideoFile = name?.matches(Regex(""".*\.(mp4|mkv|avi|mov|wmv|flv|webm)$""", RegexOption.IGNORE_CASE)) == true
                
                // Parse metadata
                val captionText = content.caption?.text.orEmpty()
                val metaFromName = parseMediaFromFileName(name)
                val metaFromText = parseMetaFromText(captionText)
                
                // Detect series if it's a video file
                val allText = combineTextForAnalysis(name, captionText)
                val seasonEp = if (isVideoFile) TgContentHeuristics.guessSeasonEpisode(allText) else null
                val isSeries = seasonEp != null && (seasonEp.season != null || seasonEp.episode != null)
                
                val kind =
                    when {
                        isArchive -> MediaKind.RAR_ARCHIVE
                        isAdultChannel(chatTitle, captionText) -> MediaKind.ADULT
                        isSeries -> MediaKind.EPISODE
                        else -> MediaKind.MOVIE
                    }

                // Extract series name if episode
                val seriesName = if (isSeries && name != null) {
                    extractSeriesName(name, seasonEp)
                } else null

                val base =
                    metaFromName?.copy(
                        kind = if (isArchive) MediaKind.RAR_ARCHIVE else kind,
                        chatId = chatId,
                        messageId = message.id,
                        chatTitle = chatTitle,
                        fileId = fileId,
                        fileUniqueId = fileUniqueId,
                        seasonNumber = seasonEp?.season ?: metaFromName.seasonNumber,
                        episodeNumber = seasonEp?.episode ?: metaFromName.episodeNumber,
                        seriesName = seriesName,
                        title = selectTitle(captionText, metaFromName.title, name),
                    ) ?: MediaInfo(
                        chatId = chatId,
                        messageId = message.id,
                        kind = kind,
                        chatTitle = chatTitle,
                        fileName = name,
                        mimeType = doc.mimeType,
                        sizeBytes = doc.document?.size?.toLong(),
                        fileId = fileId,
                        fileUniqueId = fileUniqueId,
                        seasonNumber = seasonEp?.season,
                        episodeNumber = seasonEp?.episode,
                        seriesName = seriesName,
                        title = selectTitle(captionText, null, name),
                    )

                mergeMeta(base, metaFromText).copy(
                    posterFileId = if (isVideoFile) thumbFileId else null, // Use thumbnail for video files
                )
            }

            is MessagePhoto -> {
                val photo = content.photo
                MediaInfo(
                    chatId = chatId,
                    messageId = message.id,
                    kind = if (isAdultChannel(chatTitle, content.caption?.text)) MediaKind.ADULT else MediaKind.PHOTO,
                    chatTitle = chatTitle,
                    fileName = null,
                    mimeType = null,
                    sizeBytes =
                        photo.sizes
                            ?.maxByOrNull { it.width }
                            ?.photo
                            ?.size
                            ?.toLong(),
                )
            }

            is MessageText -> {
                val text = content.text?.text.orEmpty()
                val meta = parseMetaFromText(text) ?: return null

                val kind =
                    when {
                        meta.totalEpisodes != null || meta.totalSeasons != null -> MediaKind.SERIES
                        isAdultChannel(chatTitle, text) -> MediaKind.ADULT
                        else -> MediaKind.TEXT_ONLY
                    }

                meta.copy(
                    chatId = chatId,
                    messageId = message.id,
                    kind = kind,
                    chatTitle = chatTitle,
                )
            }

            else -> null
        }
    }

    private fun parseMetaFromText(text: String): MediaInfo? {
        if (!text.contains("Titel:", ignoreCase = true) &&
            !text.contains("Erscheinungsjahr:", ignoreCase = true) &&
            !text.contains("Länge:", ignoreCase = true) &&
            !text.contains("FSK:", ignoreCase = true) &&
            !text.contains("TMDbRating:", ignoreCase = true)
        ) {
            return null
        }

        val t = text.replace('\u202f', ' ').replace('\u00A0', ' ')

        fun grab(re: Regex): String? =
            re
                .find(t)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()

        val title = grab(reTitle)
        val orig = grab(reOriginalTitle)
        val year = grab(reYear)?.toIntOrNull()
        val length = grab(reLengthMinutes)?.toIntOrNull()
        val country = grab(reCountry)
        val fsk = grab(reFsk)?.toIntOrNull()
        val collection = grab(reCollection)
        val director = grab(reDirector)

        val ratingMatch = reTmdbRating.find(t)
        val rating =
            ratingMatch
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull()
        val votes = ratingMatch?.groupValues?.getOrNull(2)?.toIntOrNull()

        val genresText = grab(reGenres)
        val genres =
            genresText
                ?.split(',', '/', '|')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

        val epsMatch = reEpisodes.find(t)
        val totalEps = epsMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        val totalSeasons = epsMatch?.groupValues?.getOrNull(2)?.toIntOrNull()

        if (title == null && year == null && length == null && country == null && fsk == null && rating == null && genres.isEmpty()) {
            return null
        }

        return MediaInfo(
            chatId = 0L,
            messageId = 0L,
            kind = MediaKind.OTHER,
            title = title,
            originalTitle = orig,
            year = year,
            durationMinutes = length,
            country = country,
            fsk = fsk,
            collection = collection,
            genres = genres,
            director = director,
            tmdbRating = rating,
            tmdbVotes = votes,
            totalEpisodes = totalEps,
            totalSeasons = totalSeasons,
            extraInfo =
                if (t.contains("Weitere Infos", ignoreCase = true)) {
                    t.substringAfter("Weitere Infos", "").trim().ifEmpty { null }
                } else {
                    null
                },
        )
    }

    private fun parseMediaFromFileName(fileName: String?): MediaInfo? {
        if (fileName.isNullOrBlank()) return null

        val base = fileName.substringBeforeLast('.')
        var title: String? = base
        var year: Int? = null
        var season: Int? = null
        var episode: Int? = null

        val yearMatch = Regex("""(19|20)\d{2}""").find(base)
        if (yearMatch != null) {
            year = yearMatch.value.toInt()
            title = base.substring(0, yearMatch.range.first).trim().trim('-', '.', '_')
        }

        val seMatch = reSeasonEpisode.find(base)
        if (seMatch != null) {
            season = seMatch.groupValues[1].toIntOrNull()
            episode = seMatch.groupValues[2].toIntOrNull()
            title = base.substring(0, seMatch.range.first).trim().trim('-', '.', '_')
        }

        return MediaInfo(
            chatId = 0L,
            messageId = 0L,
            kind = MediaKind.OTHER,
            title = title,
            year = year,
            seasonNumber = season,
            episodeNumber = episode,
            fileName = fileName,
        )
    }

    private fun mergeMeta(
        base: MediaInfo,
        add: MediaInfo?,
    ): MediaInfo {
        if (add == null) return base
        return base.copy(
            title = add.title ?: base.title,
            originalTitle = add.originalTitle ?: base.originalTitle,
            year = add.year ?: base.year,
            durationMinutes = add.durationMinutes ?: base.durationMinutes,
            country = add.country ?: base.country,
            fsk = add.fsk ?: base.fsk,
            collection = add.collection ?: base.collection,
            genres = if (add.genres.isNotEmpty()) add.genres else base.genres,
            director = add.director ?: base.director,
            tmdbRating = add.tmdbRating ?: base.tmdbRating,
            tmdbVotes = add.tmdbVotes ?: base.tmdbVotes,
            totalEpisodes = add.totalEpisodes ?: base.totalEpisodes,
            totalSeasons = add.totalSeasons ?: base.totalSeasons,
            seasonNumber = add.seasonNumber ?: base.seasonNumber,
            episodeNumber = add.episodeNumber ?: base.episodeNumber,
            extraInfo = add.extraInfo ?: base.extraInfo,
        )
    }

    private fun isAdultChannel(
        chatTitle: String?,
        text: String?,
    ): Boolean {
        val t = (chatTitle.orEmpty() + " " + text.orEmpty())
        return reAdultWords.containsMatchIn(t)
    }

    private fun parseSubChatRef(
        chatId: Long,
        chatTitle: String?,
        message: Message,
    ): SubChatRef? {
        val content = message.content

        if (content is MessageText) {
            val text = content.text?.text?.trim()
            if (!text.isNullOrEmpty()) {
                val isDirectoryLike =
                    chatTitle?.contains("Serien", ignoreCase = true) == true ||
                        chatTitle?.contains("Staffel", ignoreCase = true) == true

                if (isDirectoryLike && !text.contains("Titel:", ignoreCase = true)) {
                    return SubChatRef(
                        parentChatId = chatId,
                        parentChatTitle = chatTitle,
                        parentMessageId = message.id,
                        label = text,
                        linkedChatId = null,
                        inviteLinks = extractInviteLinksFromText(text),
                    )
                }
            }
        }

        val invite = parseInvite(chatId, message)
        if (invite != null) {
            val label =
                when (val c = content) {
                    is MessageText ->
                        c.text
                            ?.text
                            ?.trim()
                            .orEmpty()
                    else -> "(Invite)"
                }
            return SubChatRef(
                parentChatId = chatId,
                parentChatTitle = chatTitle,
                parentMessageId = message.id,
                label = label,
                linkedChatId = null,
                inviteLinks = listOf(invite.url),
            )
        }

        return null
    }

    private fun parseInvite(
        chatId: Long,
        message: Message,
    ): InviteLink? {
        val text =
            when (val c = message.content) {
                is MessageText -> c.text?.text
                is MessageVideo -> c.caption?.text
                is MessageDocument -> c.caption?.text
                is MessagePhoto -> c.caption?.text
                else -> null
            } ?: return null

        val m = reInvite.find(text) ?: return null
        val url = m.value
        return InviteLink(
            chatId = chatId,
            messageId = message.id,
            url = url,
        )
    }

    private fun extractInviteLinksFromText(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        return reInvite.findAll(text).map { it.value }.toList()
    }
}
