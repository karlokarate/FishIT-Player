package com.fishit.player.core.persistence.obx

import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.util.SlugGenerator
import java.util.Locale

/**
 * Key generators for NX_* entities.
 *
 * Per NX_SSOT_CONTRACT.md Section 2 (Key Formats).
 *
 * ## Key Format Specifications
 *
 * - **workKey:** `<workType>:<authority>:<id>` where authority is `heuristic` (slug-based) or `tmdb` (ID-based)
 * - **authorityKey:** `<authority>:<type>:<id>`
 * - **sourceKey:** Handled by `SourceKeyParser` (infra/data-nx). Format: `src:<sourceType>:<accountKey>:<itemKind>:<itemKey>`
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
     * Format: `<workType>:<authority>:<id>`
     *
     * When `tmdbId` is provided, authority is `tmdb` and the key uses the numeric
     * TMDB ID directly (e.g., `movie:tmdb:603`). Otherwise, authority is `heuristic`
     * and the key uses a slug-based ID (e.g., `movie:heuristic:matrix-1999`).
     *
     * Examples:
     * - `movie:heuristic:matrix-1999`
     * - `movie:tmdb:603`
     * - `episode:heuristic:breaking-bad-2008-s02e07`
     * - `live_channel:heuristic:cnn-live`
     * - `series:heuristic:game-of-thrones-2011`
     *
     * @param workType Type of work (MOVIE, EPISODE, SERIES, etc.)
     * @param title Canonical title (articles stripped by [SlugGenerator])
     * @param year Release year (null for LIVE)
     * @param tmdbId If non-null, produces a TMDB-authority key instead of heuristic-slug key
     * @param season Season number (for EPISODE keys)
     * @param episode Episode number (for EPISODE keys)
     * @return Canonical work key
     */
    fun workKey(
        workType: NxWorkRepository.WorkType,
        title: String,
        year: Int? = null,
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
    ): String {
        val authority = if (tmdbId != null) "tmdb" else "heuristic"
        val type = workType.name.lowercase()

        if (tmdbId != null) {
            return "$type:$authority:$tmdbId"
        }

        // Heuristic key: slug + year (+ season/episode for episodes)
        val slug = toSlug(title)
        val yearPart = when {
            workType == NxWorkRepository.WorkType.LIVE_CHANNEL -> null // no year for live
            year != null -> year.toString()
            else -> "unknown"
        }

        val id = buildString {
            append(slug)
            if (yearPart != null) {
                append("-")
                append(yearPart)
            }
            if (workType == NxWorkRepository.WorkType.EPISODE && season != null && episode != null) {
                val s = season.toString().padStart(2, '0')
                val e = episode.toString().padStart(2, '0')
                append("-s${s}e$e")
            }
        }

        return "$type:$authority:$id"
    }

    /**
     * Generate a series work key (convenience method).
     */
    fun seriesKey(
        title: String,
        year: Int? = null,
        tmdbId: Int? = null,
    ): String = workKey(NxWorkRepository.WorkType.SERIES, title, year, tmdbId)

    /**
     * Generate an episode work key (convenience method).
     */
    fun episodeKey(
        seriesTitle: String,
        year: Int? = null,
        tmdbId: Int? = null,
        season: Int? = null,
        episode: Int? = null,
    ): String = workKey(NxWorkRepository.WorkType.EPISODE, seriesTitle, year, tmdbId, season, episode)

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
     * Map internal WorkType name to TMDB namespace.
     *
     * WorkType "SERIES" → TMDB namespace "tv" (not "series").
     * WorkType "EPISODE" → "episode", "MOVIE" → "movie".
     */
    fun workTypeToTmdbNamespace(workType: String): String = when (workType.uppercase()) {
        "SERIES" -> "tv"
        "EPISODE" -> "episode"
        else -> workType.lowercase() // "MOVIE" → "movie"
    }

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
    // Source Keys — SSOT is SourceKeyParser (infra/data-nx)
    // =========================================================================
    // sourceKey building and parsing is handled by SourceKeyParser.buildSourceKey()
    // and SourceKeyParser.parse(). Format: src:{sourceType}:{accountKey}:{sourceId}
    // Do NOT add sourceKey generation here — it would produce a different format
    // (without "src:" prefix) that conflicts with the actual database format.

    // =========================================================================
    // Variant Keys
    // =========================================================================

    /**
     * Generate the default variant key for a source.
     *
     * Format: `<sourceKey>#original`
     *
     * **SSOT:** Used by [NxCatalogWriter] and [NxEnrichmentWriter].
     * Every source gets exactly one default variant with this key.
     *
     * @param sourceKey Parent source key
     * @return Default variant key
     */
    fun defaultVariantKey(sourceKey: String): String = "$sourceKey#original"

    /**
     * Generate a variant key with explicit quality and language tags.
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
    fun toSlug(title: String): String = SlugGenerator.toSlug(title)

    // =========================================================================
    // Key Parsing
    // =========================================================================

    /**
     * Parse a work key into components.
     *
     * Handles format: `<workType>:<authority>:<id>`
     * Examples:
     * - `movie:tmdb:12345` → WorkKeyComponents(MOVIE, "tmdb", 12345, "12345", null, null, null)
     * - `movie:heuristic:the-matrix-1999` → WorkKeyComponents(MOVIE, "heuristic", null, "the-matrix-1999", 1999, null, null)
     * - `episode:heuristic:breaking-bad-2008-s02e07` → WorkKeyComponents(EPISODE, "heuristic", null, "breaking-bad-2008-s02e07", 2008, 2, 7)
     *
     * @param workKey Work key to parse
     * @return Parsed components or null if invalid
     */
    fun parseWorkKey(workKey: String): WorkKeyComponents? {
        val parts = workKey.split(":", limit = 3)
        if (parts.size < 3) return null

        val workType = NxWorkRepository.WorkType.entries
            .find { it.name.equals(parts[0], ignoreCase = true) }
            ?: NxWorkRepository.WorkType.UNKNOWN
        val authority = parts[1]
        val id = parts[2]

        return when (authority) {
            "tmdb" -> {
                val tmdbId = id.toIntOrNull()
                WorkKeyComponents(workType, authority, tmdbId, id, null, null, null)
            }
            "heuristic" -> {
                // Parse year and optional season/episode from slug-based id
                // e.g., "the-matrix-1999" or "breaking-bad-2008-s02e07" or "cnn-live"
                val seMatch = Regex("-s(\\d+)e(\\d+)$").find(id)
                val season = seMatch?.groupValues?.get(1)?.toIntOrNull()
                val episode = seMatch?.groupValues?.get(2)?.toIntOrNull()

                // Try to extract year (4-digit number near the end, before optional season/episode)
                val idWithoutSe = if (seMatch != null) id.substring(0, seMatch.range.first) else id
                val yearMatch = Regex("-(\\d{4})$").find(idWithoutSe)
                val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()

                WorkKeyComponents(workType, authority, null, id, year, season, episode)
            }
            else -> {
                // Unknown authority - best-effort parse
                WorkKeyComponents(workType, authority, null, id, null, null, null)
            }
        }
    }

    data class WorkKeyComponents(
        val workType: NxWorkRepository.WorkType,
        val authority: String,
        val tmdbId: Int?,
        val id: String,
        val year: Int?,
        val season: Int?,
        val episode: Int?,
    )

    // NOTE: SourceKey parsing is handled by SourceKeyParser.parse() in infra/data-nx.
    // Format: src:{sourceType}:{accountKey}:{itemKind}:{itemKey}
    // Do NOT add parseSourceKey() here — it would use a different format.
}
