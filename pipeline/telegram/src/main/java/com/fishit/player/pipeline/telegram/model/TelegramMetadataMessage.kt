package com.fishit.player.pipeline.telegram.model

/**
 * Represents a text-only metadata message from Telegram.
 *
 * Based on analysis of real Telegram export JSONs (docs/telegram/exports/exports/),
 * many chats include pure metadata messages alongside media messages.
 * These messages contain rich metadata fields parsed from Telegram bot messages.
 *
 * Example patterns found in exports:
 * ```
 * {
 *   "text": "󠄿Titel: Das Ende der Welt...\nOriginaltitel: The 12 Disasters...",
 *   "year": 2012,
 *   "lengthMinutes": 89,
 *   "fsk": 12,
 *   "originalTitle": "The 12 Disasters of Christmas",
 *   "productionCountry": "Kanada",
 *   "director": "Steven R. Monroe",
 *   "tmdbRating": 4.456,
 *   "genres": ["TV-Film", "Action", "Science Fiction"],
 *   "tmdbUrl": "https://www.themoviedb.org/movie/149722-..."
 * }
 * ```
 *
 * **CONTRACT COMPLIANCE (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - All fields are RAW as provided by Telegram export
 * - NO normalization, cleaning, or TMDB lookups performed
 * - TMDB URL is kept as raw string (not parsed to ID in pipeline)
 * - All processing delegated to :core:metadata-normalizer
 *
 * @property chatId Telegram chat ID
 * @property messageId Telegram message ID
 * @property date Message timestamp
 * @property title Title field (if present in metadata)
 * @property originalTitle Original title field (if present)
 * @property year Release/air year (if present)
 * @property lengthMinutes Duration in minutes (if present)
 * @property fsk FSK/age rating (if present)
 * @property productionCountry Production country (if present)
 * @property director Director name (if present)
 * @property genres List of genre strings (if present)
 * @property tmdbUrl Raw TMDB URL string (if present)
 * @property tmdbRating TMDB rating value (if present)
 * @property rawText Full raw text content of the message
 */
data class TelegramMetadataMessage(
    val chatId: Long,
    val messageId: Long,
    val date: Long,
    val title: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val lengthMinutes: Int? = null,
    val fsk: Int? = null,
    val productionCountry: String? = null,
    val director: String? = null,
    val genres: List<String> = emptyList(),
    val tmdbUrl: String? = null,
    val tmdbRating: Double? = null,
    val rawText: String = "",
)
