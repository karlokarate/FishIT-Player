package com.fishit.player.core.persistence.obx

import java.text.Normalizer
import java.util.Locale

/**
 * Key generators for NX_* entities.
 *
 * Per NX_SSOT_CONTRACT.md Section 2 (Key Formats).
 *
 * ## Key Format Specifications
 *
 * - **workKey:** `<workType>:<canonicalSlug>:<year|LIVE>`
 * - **authorityKey:** `<authority>:<type>:<id>`
 * - **sourceKey:** `<sourceType>:<accountKey>:<sourceId>`
 * - **variantKey:** `<sourceKey>#<qualityTag>:<languageTag>`
 * - **categoryKey:** `<sourceType>:<accountKey>:<categoryId>`
 *
 * ## Invariants
 *
 * - INV-04: sourceKey is globally unique across all accounts
 * - INV-12: workKey is globally unique
 * - INV-13: accountKey is mandatory in all NX_WorkSourceRef
 */
object NxKeyGenerator {
    // =========================================================================
    // Work Keys
    // =========================================================================

    /**
     * Generate a canonical work key.
     *
     * Format: `<workType>:<canonicalSlug>:<year|LIVE>`
     *
     * @param workType Type of work (MOVIE, EPISODE, SERIES, etc.)
     * @param title Canonical title
     * @param year Release year (null for LIVE)
     * @param season Season number (for EPISODE)
     * @param episode Episode number (for EPISODE)
     * @return Canonical work key
     */
    fun workKey(
        workType: WorkType,
        title: String,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
    ): String {
        val slug = toSlug(title)
        val yearPart =
            when {
                workType == WorkType.LIVE -> "LIVE"
                year != null -> year.toString()
                else -> "0000"
            }

        return when (workType) {
            WorkType.EPISODE -> {
                // Format: EPISODE:<slug>:<year>:S<season>E<episode>
                val s = (season ?: 1).toString().padStart(2, '0')
                val e = (episode ?: 1).toString().padStart(2, '0')
                "${workType.name}:$slug:$yearPart:S${s}E$e"
            }
            else -> "${workType.name}:$slug:$yearPart"
        }
    }

    /**
     * Generate a series work key (convenience method).
     */
    fun seriesKey(
        title: String,
        year: Int? = null,
    ): String = workKey(WorkType.SERIES, title, year)

    /**
     * Generate an episode work key (convenience method).
     */
    fun episodeKey(
        seriesTitle: String,
        year: Int? = null,
        season: Int,
        episode: Int,
    ): String = workKey(WorkType.EPISODE, seriesTitle, year, season, episode)

    // =========================================================================
    // Authority Keys
    // =========================================================================

    /**
     * Generate an authority key.
     *
     * Format: `<authority>:<type>:<id>`
     *
     * @param authority Authority name (tmdb, imdb, tvdb)
     * @param type Media type at authority (movie, tv, person)
     * @param id Authority-specific ID
     * @return Authority key
     */
    fun authorityKey(
        authority: String,
        type: String,
        id: String,
    ): String = "${authority.lowercase()}:${type.lowercase()}:$id"

    /**
     * Generate TMDB authority key.
     */
    fun tmdbKey(
        type: String,
        id: Int,
    ): String = authorityKey("tmdb", type, id.toString())

    /**
     * Generate IMDB authority key.
     */
    fun imdbKey(id: String): String = authorityKey("imdb", "title", id)

    /**
     * Generate TVDB authority key.
     */
    fun tvdbKey(id: Int): String = authorityKey("tvdb", "series", id.toString())

    // =========================================================================
    // Source Keys
    // =========================================================================

    /**
     * Generate a source key.
     *
     * Format: `<sourceType>:<accountKey>:<sourceId>`
     *
     * **INV-13:** accountKey is mandatory.
     *
     * @param sourceType Source type (telegram, xtream, local, plex)
     * @param accountKey Account identifier (mandatory)
     * @param sourceId Source-specific item identifier
     * @return Source key
     */
    fun sourceKey(
        sourceType: SourceType,
        accountKey: String,
        sourceId: String,
    ): String {
        require(accountKey.isNotBlank()) { "accountKey is mandatory (INV-13)" }
        return "${sourceType.name.lowercase()}:$accountKey:$sourceId"
    }

    /**
     * Generate Telegram source key.
     *
     * @param accountKey Telegram account key (e.g., phone hash)
     * @param chatId Telegram chat ID
     * @param messageId Telegram message ID
     */
    fun telegramSourceKey(
        accountKey: String,
        chatId: Long,
        messageId: Long,
    ): String = sourceKey(SourceType.TELEGRAM, accountKey, "${chatId}_$messageId")

    /**
     * Generate Xtream source key.
     *
     * @param accountKey Xtream account key
     * @param streamType Type of stream (live, movie, series, episode)
     * @param streamId Xtream stream ID
     */
    fun xtreamSourceKey(
        accountKey: String,
        streamType: String,
        streamId: Int,
    ): String = sourceKey(SourceType.XTREAM, accountKey, "${streamType}_$streamId")

    /**
     * Generate local file source key.
     *
     * @param filePath Absolute file path
     */
    fun localSourceKey(filePath: String): String {
        val hash = filePath.hashCode().toUInt().toString(16)
        return sourceKey(SourceType.LOCAL, "local", hash)
    }

    // =========================================================================
    // Variant Keys
    // =========================================================================

    /**
     * Generate a variant key.
     *
     * Format: `<sourceKey>#<qualityTag>:<languageTag>`
     *
     * @param sourceKey Parent source key
     * @param qualityTag Quality identifier (source, 1080p, 720p, etc.)
     * @param languageTag Language identifier (original, en, de, etc.)
     * @return Variant key
     */
    fun variantKey(
        sourceKey: String,
        qualityTag: String = "source",
        languageTag: String = "original",
    ): String = "$sourceKey#$qualityTag:$languageTag"

    // =========================================================================
    // Category Keys
    // =========================================================================

    /**
     * Generate a category key.
     *
     * Format: `<sourceType>:<accountKey>:<categoryId>`
     *
     * @param sourceType Source type
     * @param accountKey Account identifier
     * @param categoryId Category ID from source
     * @return Category key
     */
    fun categoryKey(
        sourceType: SourceType,
        accountKey: String,
        categoryId: String,
    ): String {
        require(accountKey.isNotBlank()) { "accountKey is mandatory" }
        return "${sourceType.name.lowercase()}:$accountKey:$categoryId"
    }

    // =========================================================================
    // Account Keys
    // =========================================================================

    /**
     * Generate an account key.
     *
     * Format: `<sourceType>:<identifier>`
     *
     * @param sourceType Source type
     * @param identifier Account-specific identifier (phone hash, server hash, etc.)
     * @return Account key
     */
    fun accountKey(
        sourceType: SourceType,
        identifier: String,
    ): String = "${sourceType.name.lowercase()}:$identifier"

    /**
     * Generate Telegram account key from phone number.
     *
     * Format per NX_SSOT_CONTRACT Section 3.3: `telegram:{phoneNumber}`
     * Example: `telegram:+491234567890`
     *
     * @param phoneNumber Phone number with country code (e.g., +491234567890)
     * @return Account key in format `telegram:+491234567890`
     */
    fun telegramAccountKey(phoneNumber: String): String {
        // Normalize: keep only digits and leading +
        val normalized = phoneNumber
            .trim()
            .let { if (it.startsWith("+")) "+" + it.filter { c -> c.isDigit() } else it.filter { c -> c.isDigit() } }
        require(normalized.isNotBlank()) { "Phone number required for Telegram account key" }
        return accountKey(SourceType.TELEGRAM, normalized)
    }

    /**
     * Generate Telegram account key from user ID.
     *
     * Alternative to [telegramAccountKey] for cases where phone number
     * is not available (e.g., after login when only userId is cached).
     *
     * Format: `telegram:user:{userId}`
     * Example: `telegram:user:123456789`
     *
     * @param userId Telegram user ID (from TDLib)
     * @return Account key in format `telegram:user:{userId}`
     */
    fun telegramAccountKeyFromUserId(userId: Long): String {
        require(userId > 0) { "Valid user ID required for Telegram account key" }
        return accountKey(SourceType.TELEGRAM, "user:$userId")
    }

    /**
     * Generate Xtream account key from server URL and username.
     *
     * Format per NX_SSOT_CONTRACT Section 3.3: `xtream:{username}@{server}`
     * Example: `xtream:user@iptv.server.com`
     *
     * @param serverUrl Server URL (http://iptv.server.com:8080/...)
     * @param username Xtream username
     * @return Account key in format `xtream:username@server.host`
     */
    fun xtreamAccountKey(
        serverUrl: String,
        username: String,
    ): String {
        require(username.isNotBlank()) { "Username required for Xtream account key" }
        val host = extractHost(serverUrl)
        require(host.isNotBlank()) { "Valid server URL required for Xtream account key" }
        return accountKey(SourceType.XTREAM, "$username@$host")
    }

    /**
     * Generate Local/IO account key.
     *
     * Format per NX_SSOT_CONTRACT Section 3.3: `local:default`
     * There's only one "local" source per device.
     *
     * @return Account key `local:default`
     */
    fun localAccountKey(): String = accountKey(SourceType.LOCAL, "default")

    /**
     * Extract normalized host from a URL.
     *
     * Strips protocol (http://, https://), www. prefix, port, and path.
     * Example: "http://www.iptv.server.com:8080/path" → "iptv.server.com"
     *
     * @param url Full URL or host string
     * @return Normalized host (lowercase, no protocol/port/path)
     */
    fun extractHost(url: String): String {
        return url
            .trim()
            .lowercase(Locale.ROOT)
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore(":")  // Remove port
            .substringBefore("/")  // Remove path
            .trim()
    }

    // =========================================================================
    // Profile Keys
    // =========================================================================

    /**
     * Generate a profile key.
     *
     * Format: `profile:<type>:<index>`
     *
     * @param profileType Profile type
     * @param index Profile index (for multiple profiles of same type)
     * @return Profile key
     */
    fun profileKey(
        profileType: ProfileType,
        index: Int = 0,
    ): String = "profile:${profileType.name.lowercase()}:$index"

    // =========================================================================
    // Slug Generation
    // =========================================================================

    /**
     * Convert a title to a URL-safe slug.
     *
     * - Normalizes Unicode (NFD → ASCII)
     * - Converts to lowercase
     * - Replaces spaces and special chars with hyphens
     * - Removes consecutive hyphens
     * - Trims leading/trailing hyphens
     *
     * @param title Input title
     * @return URL-safe slug
     */
    fun toSlug(title: String): String =
        Normalizer
            .normalize(title, Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "untitled" }

    // =========================================================================
    // Key Parsing
    // =========================================================================

    /**
     * Parse a work key into components.
     *
     * @param workKey Work key to parse
     * @return Parsed components or null if invalid
     */
    fun parseWorkKey(workKey: String): WorkKeyComponents? {
        val parts = workKey.split(":")
        if (parts.size < 3) return null

        val workType = WorkType.fromString(parts[0])
        val slug = parts[1]
        val yearOrLive = parts[2]

        val year = if (yearOrLive == "LIVE") null else yearOrLive.toIntOrNull()

        // Check for episode format: EPISODE:<slug>:<year>:S<season>E<episode>
        val (season, episode) =
            if (parts.size >= 4 && workType == WorkType.EPISODE) {
                val seMatch = Regex("S(\\d+)E(\\d+)").find(parts[3])
                if (seMatch != null) {
                    seMatch.groupValues[1].toInt() to seMatch.groupValues[2].toInt()
                } else {
                    null to null
                }
            } else {
                null to null
            }

        return WorkKeyComponents(workType, slug, year, season, episode)
    }

    /**
     * Parse a source key into components.
     *
     * @param sourceKey Source key to parse
     * @return Parsed components or null if invalid
     */
    fun parseSourceKey(sourceKey: String): SourceKeyComponents? {
        val parts = sourceKey.split(":", limit = 3)
        if (parts.size < 3) return null

        val sourceType = SourceType.fromString(parts[0])
        val accountKey = parts[1]
        val sourceId = parts[2]

        return SourceKeyComponents(sourceType, accountKey, sourceId)
    }

    data class WorkKeyComponents(
        val workType: WorkType,
        val slug: String,
        val year: Int?,
        val season: Int?,
        val episode: Int?,
    )

    data class SourceKeyComponents(
        val sourceType: SourceType,
        val accountKey: String,
        val sourceId: String,
    )
}
