package com.chris.m3usuite.core.util

/**
 * CategoryNormalizer (core)
 *
 * Normalizes potentially noisy provider/category strings into stable keys
 * and provides a human-friendly display label.
 *
 * This is a relocation of the previous ui.util.CategoryNormalizer to fix
 * a layering violation (data/work must not depend on ui).
 */
object CategoryNormalizer {

    // Drop leading country/language tags with common delimiters: "DE | ", "DE:", "[DE] ", "DE=> ", "EN-", etc.
    private val leadingLang = Regex("^(?:\\[?[A-Z]{2,3}\\]?\\s*(?:\\||:|/|->|=>|-)+\\s*)+")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val ws = Regex("\\s+")

    /**
     * Convert a raw category/provider string to a normalized key.
     * Examples:
     *  - "DE | Netflix" -> "netflix"
     *  - "Disney Plus"  -> "disney_plus"
     *  - "Apple TV+"    -> "apple_tv_plus"
     *  - "4K Movies"    -> "4k_movies"
     */
    fun normalizeKey(raw: String?): String {
        if (raw.isNullOrBlank()) return "unknown"
        var s = raw.trim()
        s = leadingLang.replace(s, "") // drop leading country/ALL prefixes like "DE | ", "DE=>", "[DE]" etc.

        val lower = s.lowercase()

        // Common OTT brands (return canonical slugs)
        if ("apple" in lower) return "apple_tv_plus"
        if ("netflix" in lower) return "netflix"
        if ("disney" in lower) return "disney_plus"
        if ("prime" in lower || "amazon" in lower) return "amazon_prime"
        if ("paramount" in lower) return "paramount_plus"
        if ("hbo" in lower || Regex(".*\\bmax\\b.*").containsMatchIn(lower)) return "max"
        if ("sky" in lower || Regex("\\bwow\\b").containsMatchIn(lower)) return "sky_wow"
        if ("discovery" in lower) return "discovery_plus"
        if ("mubi" in lower) return "mubi"

        // Theme buckets
        if ("kids" in lower || "kinder" in lower || "cartoon" in lower || "animation" in lower) return "kids"
        if ("sport" in lower || "sports" in lower || "fussball" in lower || "football" in lower) return "sports"
        if ("news" in lower || "nachrichten" in lower) return "news"
        if ("music" in lower || "musik" in lower) return "music"
        if ("documentary" in lower || "dokumentation" in lower || "doku" in lower) return "documentary"

        // Fallback: sanitize to snake_case
        val base = nonWord.replace(lower, "_").trim('_')
        return base.ifBlank { "unknown" }
    }

    /**
     * Deterministic bucket normalization per kind (live|vod|series), capped to ≤10 buckets by design.
     * Uses group-title, tvg-name and URL signals; ignores quality tokens (HEVC/FHD/HD/SD/4K).
     */
    fun normalizeBucket(kind: String, groupTitle: String?, tvgName: String?, url: String?): String {
        val gt = (groupTitle ?: "").lowercase()
        val name = (tvgName ?: "").lowercase()
        val u = (url ?: "").lowercase()

        fun has(any: String) = any in gt || any in name || any in u
        fun hasWord(re: Regex) = re.containsMatchIn(gt) || re.containsMatchIn(name) || re.containsMatchIn(u)
        fun hasAny(tokens: Array<String>) = tokens.any { has(it) }

        // Special handling: keep FOR ADULTS sub-categories as distinct buckets for VOD.
        // Example group-title: "FOR ADULTS ➾ MILF" -> bucket "adult_milf" (display label derived below).
        if (kind.equals("vod", true)) {
            val adultsRe = Regex("\\bfor adults\\b", RegexOption.IGNORE_CASE)
            val inAdults = adultsRe.containsMatchIn(groupTitle.orEmpty()) || adultsRe.containsMatchIn(tvgName.orEmpty())
            if (inAdults) {
                val src = groupTitle?.ifBlank { tvgName ?: "" } ?: (tvgName ?: "")
                val tail = Regex(
                    """for adults\s*(?:[➾:>\-]+\s*)?(.*)""",
                    RegexOption.IGNORE_CASE
                ).find(src)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                val sub = tail.ifBlank { "other" }
                val slug = nonWord.replace(sub.lowercase(), "_").trim('_').ifBlank { "other" }
                return "adult_" + slug
            }
        }

        val isScreensaver = has("screensaver")
        val isSport = hasAny(arrayOf("sport", "dazn", "sky sport", "eurosport"))
        val isNews = hasAny(arrayOf("news", "nachricht", "n-tv", "welt", "cnn", "bbc news", "al jazeera"))
        val isDoc = hasAny(arrayOf("doku", "docu", "documentary", "history", "nat geo", "discovery"))
        val isKids = hasAny(arrayOf("kids", "kinder", "cartoon", "nick", "kika", "disney channel"))
        val isMusic = hasAny(arrayOf("musik", "music", "mtv", "vh1"))
        val isMoviesLive = hasAny(arrayOf("sky cinema", "cinema"))
        val isInternational = hasAny(arrayOf("thailand", "arabic", "turkish", "fr ", " france", "italy", "spanish", "english", "usa", "uk "))

        val isNetflix = has("netflix")
        val isAmazon = has("amazon") || has("prime")
        val isDisney = has("disney+") || has("disney plus") || (has("disney ") && !has("disney channel"))
        val isApple = has("apple tv+") || has("apple tv plus") || has("apple tv") || hasWord(Regex("\\bapple\\b"))
        val isSkyWarner = has("sky ") || has("warner") || has("hbo") || hasWord(Regex("\\bmax\\b")) || has("paramount")
        val isAnime = has("anime")
        val isNew = has("neu aktuell") || has("neu ") || has("new ")
        val isGermanGroup = has("de ") || has("deutschland") || has("german")

        return when (kind.lowercase()) {
            "live" -> when {
                isScreensaver -> "screensaver"
                isSport -> "sports"
                isNews -> "news"
                isDoc -> "documentary"
                isKids -> "kids"
                isMusic -> "music"
                isMoviesLive -> "movies"
                isInternational -> "international"
                else -> "entertainment"
            }
            "vod" -> when {
                isNetflix -> "netflix"
                isAmazon -> "amazon_prime"
                isDisney -> "disney_plus"
                isApple -> "apple_tv_plus"
                isSkyWarner -> "sky_warner"
                isAnime -> "anime"
                isNew -> "new"
                isKids -> "kids"
                isGermanGroup -> "german"
                else -> "other"
            }
            "series" -> when {
                isNetflix -> "netflix_series"
                (isAmazon && isApple) || has("amazon & apple") -> "amazon_apple_series"
                isApple -> "amazon_apple_series" // Apple‑only einordnen
                isDisney -> "disney_plus_series"
                isSkyWarner -> "sky_warner_series"
                isAnime -> "anime_series"
                isKids -> "kids_series"
                isGermanGroup -> "german_series"
                else -> "other"
            }
            else -> "other"
        }
    }

    /** Optional helper keys for indexing/filter */
    fun langKey(groupTitle: String?, tvgName: String?, url: String?): String? {
        val s = listOfNotNull(groupTitle, tvgName, url).joinToString(" ").lowercase()
        return when {
            Regex("\\bde(utsch)?\\b").containsMatchIn(s) -> "DE"
            Regex("\\ben(glish)?|uk|gb|us\\b").containsMatchIn(s) -> if (s.contains("us")) "US" else "EN"
            Regex("\\bfr(ench)?\\b").containsMatchIn(s) -> "FR"
            Regex("\\bit(alian)?\\b").containsMatchIn(s) -> "IT"
            Regex("\\bes(p(anol|anish))?\\b").containsMatchIn(s) -> "ES"
            Regex("\\btr(turk(ish)?)?\\b").containsMatchIn(s) -> "TR"
            Regex("\\bar(abic)?\\b").containsMatchIn(s) -> "AR"
            else -> null
        }
    }

    fun qualityKey(tvgName: String?): String? {
        val n = (tvgName ?: "").lowercase()
        return when {
            "4k" in n || "uhd" in n -> "uhd"
            "fhd" in n || "1080" in n -> "fhd"
            "hd" in n || "720" in n -> "hd"
            "sd" in n || "480" in n -> "sd"
            else -> null
        }
    }

    fun extKey(url: String?): String? = url?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()?.ifBlank { null }

    fun seriesKey(tvgName: String?): String? {
        if (tvgName.isNullOrBlank()) return null
        val base = tvgName.replace(Regex("\\bS\\d{1,2}\\s*E\\d{1,3}\\b"), "").trim()
        return if (base.isNotBlank()) base else tvgName
    }

    /**
     * Convert a normalized key to a human-readable label.
     */
    fun displayLabel(key: String): String = when (key) {
        // Adult buckets (dynamic): map "adult_milf" -> "For Adults – MILF"
        else -> if (key.startsWith("adult_")) {
            val raw = key.removePrefix("adult_").replace('_', ' ').trim()
            val pretty = raw.split(' ').joinToString(" ") { w ->
                if (w.isEmpty()) w else w.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
            }.ifBlank { "Other" }
            "For Adults – $pretty"
        } else when (key) {
        // Curated VOD genre/thematic labels (German)
        "action" -> "Action"
        "comedy" -> "Komödie"
        "kids" -> "Kinder & Animation"
        "horror" -> "Horror"
        "thriller" -> "Thriller"
        "documentary" -> "Dokumentationen"
        "romance" -> "Romanze"
        "family" -> "Familie"
        "christmas" -> "Weihnachten"
        "sci_fi" -> "Science‑Fiction"
        "western" -> "Western"
        "war" -> "Kriegsfilme"
        "bollywood" -> "Bollywood"
        "anime" -> "Anime"
        "fantasy" -> "Fantasy"
        "martial_arts" -> "Martial Arts"
        "classic" -> "Classic"
        "adventure" -> "Abenteuer"
        "show" -> "Show"
        "collection" -> "Kollektionen"
        "4k" -> "4K"
        "other" -> "Unkategorisiert"
        "apple_tv_plus" -> "Apple TV+"
        "netflix" -> "Netflix"
        "disney_plus" -> "Disney+"
        "amazon_prime" -> "Amazon Prime"
        "sky_warner" -> "Sky/Warner"
        "paramount_plus" -> "Paramount+"
        "max" -> "Max"
        "sky_wow" -> "Sky / WOW"
        "discovery_plus" -> "discovery+"
        "mubi" -> "MUBI"
        "new" -> "Neu"
        "german" -> "Deutsch"
        // keep legacy keys for other contexts
        // (already handled curated kids above)
        // "kids" -> "Kids"
        "sports" -> "Sport"
        "news" -> "News"
        "music" -> "Musik"
        "international" -> "International"
        "screensaver" -> "Screensaver"
        // Series variants map to provider labels
        "netflix_series" -> "Netflix"
        "amazon_apple_series" -> "Amazon & Apple"
        "disney_plus_series" -> "Disney+"
        "sky_warner_series" -> "Sky/Warner"
        "anime_series" -> "Anime"
        "kids_series" -> "Kids"
        "german_series" -> "Deutsch"
        "unknown" -> "Unbekannt"
        else -> key.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
