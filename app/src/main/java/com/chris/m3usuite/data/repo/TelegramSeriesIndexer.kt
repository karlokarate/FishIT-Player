package com.chris.m3usuite.data.repo

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.data.obx.ObxEpisode
import com.chris.m3usuite.data.obx.ObxEpisode_
import com.chris.m3usuite.data.obx.ObxIndexLang
import com.chris.m3usuite.data.obx.ObxIndexLang_
import com.chris.m3usuite.data.obx.ObxSeries
import com.chris.m3usuite.data.obx.ObxSeries_
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.TelegramHeuristics
import com.chris.m3usuite.telegram.containerExt
import com.chris.m3usuite.telegram.posterUri
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Rebuilds aggregated Telegram series (ObxSeries + ObxEpisode) from indexed Telegram messages.
 * Ensures all metadata is persisted similar to Xtream flows and keeps the language index in sync.
 */
object TelegramSeriesIndexer {
    private const val PROVIDER_KEY = "telegram"
    private const val LANG_INDEX_PREFIX = "telegram:"

    data class RebuildStats(
        val seriesCount: Int,
        val episodeCount: Int,
        val newSeries: Int,
        val newEpisodes: Int
    )

    private val EMPTY_STATS = RebuildStats(0, 0, 0, 0)

    suspend fun rebuild(context: Context): Int = rebuildWithStats(context).seriesCount

    suspend fun rebuildWithStats(context: Context): RebuildStats = withContext(Dispatchers.IO) {
        val settings = SettingsStore(context)
        val enabled = settings.tgEnabled.first()
        val store = ObxStore.get(context)
        if (!enabled) {
            cleanupTelegramSeries(store)
            return@withContext EMPTY_STATS
        }

        val selectedChats = settings.tgSelectedSeriesChatsCsv.first()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .distinct()
        if (selectedChats.isEmpty()) {
            cleanupTelegramSeries(store)
            return@withContext EMPTY_STATS
        }

        val msgBox = store.boxFor<ObxTelegramMessage>()
        val query = msgBox.query(
            ObxTelegramMessage_.chatId.oneOf(selectedChats.toLongArray())
                .and(ObxTelegramMessage_.captionLower.notNull())
        ).orderDesc(ObxTelegramMessage_.date)
            .order(ObxTelegramMessage_.chatId)
            .build()
        val messages = try {
            query.find()
        } finally {
            query.close()
        }
        if (messages.isEmpty()) {
            cleanupTelegramSeries(store)
            return@withContext EMPTY_STATS
        }

        val apiId = settings.tgApiId.first()
        val apiHash = settings.tgApiHash.first()
        val resolvedApiId = if (apiId > 0) apiId else BuildConfig.TG_API_ID
        val resolvedApiHash = apiHash.ifBlank { BuildConfig.TG_API_HASH }
        val chatTitles = loadChatTitles(context, selectedChats, resolvedApiId, resolvedApiHash)

        val aggregates = mutableMapOf<String, SeriesAggregate>()
        messages.forEach { msg ->
            val parsed = TelegramHeuristics.parse(msg.caption)
            if (!parsed.isSeries) return@forEach
            val rawTitle = parsed.seriesTitle?.takeIf { it.isNotBlank() }
                ?: chatTitles[msg.chatId]
                ?: "Telegram ${msg.chatId}"
            val baseTitle = normalizeSeriesTitle(rawTitle)
            if (baseTitle.isBlank()) return@forEach
            val season = parsed.season ?: return@forEach
            val startEpisode = parsed.episode ?: return@forEach
            val episodes = buildEpisodeNumbers(startEpisode, parsed.episodeEnd)
            val normalized = normalizeSeriesKey(baseTitle)
            val poster = msg.posterUri(context)
            val language = (parsed.language ?: msg.language)
                ?.lowercase(Locale.getDefault())
            val messageDate = msg.date ?: (System.currentTimeMillis() / 1000)

            val aggregate = aggregates.getOrPut(normalized) {
                SeriesAggregate(
                    seriesId = seriesIdFor(normalized),
                    title = baseTitle,
                    posterUri = poster,
                    firstSeen = messageDate,
                    lastSeen = messageDate,
                    captions = mutableListOf(),
                    languages = mutableSetOf(),
                    years = mutableSetOf(),
                    episodes = mutableMapOf()
                )
            }
            aggregate.posterUri = aggregate.posterUri ?: poster
            aggregate.lastSeen = maxOf(aggregate.lastSeen, messageDate)
            aggregate.firstSeen = minOf(aggregate.firstSeen, messageDate)
            if (!msg.caption.isNullOrBlank()) aggregate.captions += msg.caption!!
            detectYear(msg.caption)?.let { aggregate.years += it }
            detectYear(parsed.title)?.let { aggregate.years += it }
            parsed.year?.let { aggregate.years += it }
            if (!language.isNullOrBlank()) aggregate.languages += language

            episodes.forEach { epNum ->
                val key = season to epNum
                val existing = aggregate.episodes[key]
                if (existing == null || messageDate >= existing.messageDate) {
                    aggregate.episodes[key] = EpisodeAggregate(
                        message = msg,
                        parsed = parsed,
                        season = season,
                        episode = epNum,
                        messageDate = messageDate,
                        language = language,
                        posterOverride = poster
                    )
                }
            }
        }

        val seriesBox = store.boxFor<ObxSeries>()
        val episodeBox = store.boxFor<ObxEpisode>()

        // Remove series that no longer exist (deselected chats or no matching messages)
        val existingSeriesQuery = seriesBox.query(ObxSeries_.providerKey.equal(PROVIDER_KEY)).build()
        val existingSeries = try { existingSeriesQuery.find() } finally { existingSeriesQuery.close() }
        val existingSeriesIds = existingSeries.map { it.seriesId }.toSet()
        val keepSeriesIds = aggregates.values.map { it.seriesId }.toSet()
        val removeSeries = existingSeries.filter { it.seriesId !in keepSeriesIds }
        if (removeSeries.isNotEmpty()) {
            val removeIds = removeSeries.map { it.seriesId.toLong() }.toLongArray()
            val episodeCleanup = episodeBox.query(ObxEpisode_.seriesId.oneOf(removeIds)).build()
            try {
                val staleEpisodes = episodeCleanup.find()
                if (staleEpisodes.isNotEmpty()) episodeBox.remove(staleEpisodes)
            } finally {
                episodeCleanup.close()
            }
            seriesBox.remove(removeSeries)
        }

        val existingEpisodeIds: Set<Int> = if (existingSeriesIds.isNotEmpty()) {
            val q = episodeBox.query(ObxEpisode_.seriesId.oneOf(existingSeriesIds.map { it.toLong() }.toLongArray())).build()
            try {
                q.find().map { it.episodeId }.toSet()
            } finally {
                q.close()
            }
        } else emptySet()

        // Drop existing episodes for kept series before writing the fresh snapshot
        if (keepSeriesIds.isNotEmpty()) {
            val q = episodeBox.query(ObxEpisode_.seriesId.oneOf(keepSeriesIds.map { it.toLong() }.toLongArray())).build()
            try {
                val rows = q.find()
                if (rows.isNotEmpty()) episodeBox.remove(rows)
            } finally {
                q.close()
            }
        }

        val existingSeriesById = existingSeries.associateBy { it.seriesId }
        val newSeriesRows = mutableListOf<ObxSeries>()
        val newEpisodeRows = mutableListOf<ObxEpisode>()
        val langCounts = mutableMapOf<String, Int>()

        aggregates.values.forEach { agg ->
            val baseTitle = agg.title.ifBlank { "Telegram ${agg.seriesId}" }
            val seriesRow = existingSeriesById[agg.seriesId] ?: ObxSeries()
            seriesRow.seriesId = agg.seriesId
            seriesRow.name = baseTitle
            seriesRow.nameLower = baseTitle.lowercase(Locale.getDefault())
            seriesRow.sortTitleLower = baseTitle.lowercase(Locale.getDefault())
            val posterList = agg.posterUri?.let { listOf(it) } ?: emptyList()
            seriesRow.imagesJson = if (posterList.isNotEmpty()) JSONArray(posterList).toString() else null
            val plot = agg.captions.maxByOrNull { it.length }
            seriesRow.plot = plot
            val year = agg.years.maxOrNull()
            seriesRow.year = year
            seriesRow.yearKey = year
            val languagesSorted = agg.languages.sorted()
            val languageLabel = languagesSorted.joinToString(separator = ", ") { it.uppercase(Locale.getDefault()) }
            seriesRow.genre = languageLabel.takeIf { it.isNotBlank() }?.let { "Sprache: $it" }
            val primaryLangKey = languagesSorted.firstOrNull()
            seriesRow.genreKey = primaryLangKey?.let { "telegram_lang_${it}" } ?: "telegram_lang_other"
            seriesRow.providerKey = PROVIDER_KEY
            seriesRow.categoryId = PROVIDER_KEY
            seriesRow.importedAt = seriesRow.importedAt ?: (agg.firstSeen * 1000)
            seriesRow.updatedAt = agg.lastSeen * 1000
            newSeriesRows += seriesRow

            if (agg.languages.isEmpty()) {
                langCounts["other"] = (langCounts["other"] ?: 0) + 1
            } else {
                agg.languages.forEach { lang ->
                    langCounts[lang] = (langCounts[lang] ?: 0) + 1
                }
            }

            agg.episodes.values
                .sortedWith(compareBy<EpisodeAggregate>({ it.season }, { it.episode }, { it.messageDate }))
                .forEach { entry ->
                    val msg = entry.message
                    val ep = ObxEpisode()
                    ep.seriesId = agg.seriesId
                    ep.season = entry.season
                    ep.episodeNum = entry.episode
                    ep.episodeId = telegramEpisodeId(agg.seriesId, entry.season, entry.episode)
                    ep.title = buildEpisodeTitle(baseTitle, entry.season, entry.episode, entry.parsed.title)
                    ep.durationSecs = msg.durationSecs
                    ep.plot = msg.caption
                    ep.airDate = formatAirDate(msg.date)
                    ep.playExt = msg.containerExt()
                    ep.imageUrl = entry.posterOverride ?: msg.posterUri(context)
                    ep.tgChatId = msg.chatId
                    ep.tgMessageId = msg.messageId
                    ep.tgFileId = msg.fileId
                    ep.mimeType = msg.mimeType
                    ep.width = msg.width
                    ep.height = msg.height
                    ep.sizeBytes = msg.sizeBytes
                    ep.supportsStreaming = msg.supportsStreaming
                    ep.language = entry.language
                    newEpisodeRows += ep
                }
        }

        if (newSeriesRows.isNotEmpty()) seriesBox.put(newSeriesRows)
        if (newEpisodeRows.isNotEmpty()) episodeBox.put(newEpisodeRows)

        updateLanguageIndex(store, langCounts)

        // Refresh aggregated indexes so provider/year/genre counts remain accurate
        runCatching { XtreamObxRepository(context, settings).rebuildIndexes() }

        val newSeriesCount = aggregates.values.count { it.seriesId !in existingSeriesIds }
        val newEpisodeCount = newEpisodeRows.count { it.episodeId !in existingEpisodeIds }

        RebuildStats(
            seriesCount = aggregates.size,
            episodeCount = newEpisodeRows.size,
            newSeries = newSeriesCount,
            newEpisodes = newEpisodeCount
        )
    }

    private fun cleanupTelegramSeries(store: BoxStore) {
        val seriesBox = store.boxFor<ObxSeries>()
        val episodeBox = store.boxFor<ObxEpisode>()
        val q = seriesBox.query(ObxSeries_.providerKey.equal(PROVIDER_KEY)).build()
        val series = try { q.find() } finally { q.close() }
        if (series.isNotEmpty()) {
            val ids = series.map { it.seriesId.toLong() }.toLongArray()
            val epQuery = episodeBox.query(ObxEpisode_.seriesId.oneOf(ids)).build()
            try {
                val episodes = epQuery.find()
                if (episodes.isNotEmpty()) episodeBox.remove(episodes)
            } finally {
                epQuery.close()
            }
            seriesBox.remove(series)
        }
        // Remove language index entries owned by Telegram aggregator
        val langBox = store.boxFor<ObxIndexLang>()
        val langQuery = langBox.query(
            ObxIndexLang_.kind.equal("series")
                .and(ObxIndexLang_.key.startsWith(LANG_INDEX_PREFIX))
        ).build()
        try {
            val rows = langQuery.find()
            if (rows.isNotEmpty()) langBox.remove(rows)
        } finally {
            langQuery.close()
        }
    }

    private fun buildEpisodeNumbers(start: Int, endInclusive: Int?): List<Int> {
        val s = start.takeIf { it > 0 } ?: return emptyList()
        val rawEnd = endInclusive?.takeIf { it >= s } ?: s
        val limitedEnd = (s + 199).coerceAtMost(rawEnd)
        return (s..limitedEnd).toList()
    }

    private fun normalizeSeriesTitle(raw: String): String {
        return raw
            .replace(Regex("[._]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeSeriesKey(raw: String): String {
        val s = raw.trim().lowercase(Locale.getDefault())
        val normalized = Normalizer.normalize(s, Normalizer.Form.NFD)
        val noMarks = normalized.replace(Regex("\\p{M}+"), "")
        return noMarks.replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
    }

    private fun seriesIdFor(normTitle: String): Int {
        val sha = MessageDigest.getInstance("SHA-1").digest(normTitle.toByteArray())
        var h = 0
        for (i in 0 until 4) {
            h = (h shl 8) or (sha[i].toInt() and 0xFF)
        }
        if (h < 0) h = -h
        val base = 1_500_000_000
        val span = 400_000_000
        return base + (h % span)
    }

    private fun telegramEpisodeId(seriesId: Int, season: Int, episode: Int): Int {
        var hash = seriesId
        hash = 31 * hash + season
        hash = 31 * hash + episode
        hash = hash and Int.MAX_VALUE
        return 1_700_000_000 + (hash % 200_000_000)
    }

    private fun detectYear(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val match = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(text)
        return match?.value?.toIntOrNull()
    }

    private fun buildEpisodeTitle(base: String, season: Int, episode: Int, extra: String?): String {
        val core = buildString {
            append(base.trim())
            append(' ')
            append('S')
            append(season.toString().padStart(2, '0'))
            append('E')
            append(episode.toString().padStart(2, '0'))
        }
        val suffix = extra?.trim().orEmpty()
        return if (suffix.isNotBlank() && !suffix.equals(base, ignoreCase = true)) "$core – $suffix" else core
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun formatAirDate(epochSeconds: Long?): String? {
        if (epochSeconds == null || epochSeconds <= 0) return null
        return runCatching {
            Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        }.getOrNull()
    }

    private fun updateLanguageIndex(store: BoxStore, counts: Map<String, Int>) {
        val langBox = store.boxFor<ObxIndexLang>()
        val existingQuery = langBox.query(
            ObxIndexLang_.kind.equal("series")
                .and(ObxIndexLang_.key.startsWith(LANG_INDEX_PREFIX))
        ).build()
        val existing = try { existingQuery.find() } finally { existingQuery.close() }
        val keepKeys = counts.keys.map { "$LANG_INDEX_PREFIX$it" }.toSet()
        val toRemove = existing.filter { it.key !in keepKeys }
        if (toRemove.isNotEmpty()) langBox.remove(toRemove)
        counts.forEach { (lang, count) ->
            val key = "$LANG_INDEX_PREFIX$lang"
            val row = existing.firstOrNull { it.key == key } ?: ObxIndexLang(kind = "series", key = key)
            row.count = count.toLong()
            langBox.put(row)
        }
        if (counts.isEmpty() && existing.isNotEmpty()) {
            langBox.remove(existing)
        }
    }

    private data class SeriesAggregate(
        val seriesId: Int,
        val title: String,
        var posterUri: String?,
        var firstSeen: Long,
        var lastSeen: Long,
        val captions: MutableList<String>,
        val languages: MutableSet<String>,
        val years: MutableSet<Int>,
        val episodes: MutableMap<Pair<Int, Int>, EpisodeAggregate>
    )

    private data class EpisodeAggregate(
        val message: ObxTelegramMessage,
        val parsed: TelegramHeuristics.ParseResult,
        val season: Int,
        val episode: Int,
        val messageDate: Long,
        val language: String?,
        val posterOverride: String?
    )

    private suspend fun loadChatTitles(
        context: Context,
        chatIds: List<Long>,
        apiId: Int,
        apiHash: String,
    ): Map<Long, String> {
        if (chatIds.isEmpty()) return emptyMap()
        return withContext(Dispatchers.Main) {
            val svc = TelegramServiceClient(context.applicationContext)
            try {
                svc.bind()
                if (apiId > 0 && apiHash.isNotBlank()) {
                    svc.start(apiId, apiHash)
                    svc.getAuth()
                }
                val titles = runCatching { svc.resolveChatTitles(chatIds.toLongArray()) }.getOrNull().orEmpty()
                titles.associate { (id, title) -> id to normalizeSeriesTitle(title) }
            } catch (_: Throwable) {
                emptyMap()
            } finally {
                svc.unbind()
            }
        }
    }

}
