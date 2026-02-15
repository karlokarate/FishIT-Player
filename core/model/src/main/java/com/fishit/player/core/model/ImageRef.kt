package com.fishit.player.core.model

/**
 * Unified image reference abstraction for FishIT-Player v2.
 *
 * **Purpose:**
 * - Pipelines emit [ImageRef] inside metadata (poster, backdrop, thumbnail)
 * - UI modules consume [ImageRef] via GlobalImageLoader
 * - Core/ui-imaging handles resolution via Coil 3 Fetchers
 *
 * **Architecture:**
 * - Pipelines (:pipeline:telegram, :pipeline:xtream) produce ImageRef
 * - Normalizer may transform/merge ImageRef from raw sources
 * - UI uses ImageRef with GlobalImageLoader (no direct URLs/TDLib DTOs)
 * - Coil Fetchers in :core:ui-imaging resolve each variant
 *
 * **Contract:**
 * - Pipelines MUST NOT depend on Coil
 * - UI MUST NOT use raw URLs or TDLib DTOs directly
 * - All image resolution goes through ImageRef + Fetchers
 *
 * @property preferredWidth Hint for fetcher (optimal width in pixels, null = original)
 * @property preferredHeight Hint for fetcher (optimal height in pixels, null = original)
 */
sealed interface ImageRef {
    val preferredWidth: Int?
    val preferredHeight: Int?

    /**
     * HTTP/HTTPS image URL.
     *
     * Used for:
     * - Xtream posters/backdrops (from panel API)
     * - TMDB images (after enrichment)
     * - Any standard web-accessible image
     *
     * Fetcher: Standard Coil HttpUriFetcher (uses shared OkHttpClient)
     *
     * @property url Full HTTP/HTTPS URL to the image
     * @property headers Optional headers for auth/cookies (e.g., Xtream panel auth)
     */
    data class Http(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        override val preferredWidth: Int? = null,
        override val preferredHeight: Int? = null,
    ) : ImageRef

    /**
     * Telegram thumbnail reference.
     *
     * Used for:
     * - Video thumbnails from Telegram messages
     * - Document preview images
     * - Photo thumbnails (various sizes)
     *
     * ## remoteId-First Architecture (v2)
     *
     * This class follows the **remoteId-first design** defined in
     * `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`.
     *
     * ### Key Points:
     * - `remoteId` is the **only** file identifier stored
     * - `fileId` is resolved at runtime via `getRemoteFile(remoteId)`
     * - `uniqueId` is **not stored** (no API to resolve it back)
     *
     * Fetcher: TelegramThumbFetcher in :core:ui-imaging
     * - Resolves remoteId → fileId via transport layer
     * - Downloads via TDLib to its cache
     * - Returns local path for Coil to decode
     *
     * @property remoteId Stable TDLib remote file ID (cross-session stable)
     * @property chatId Chat containing the media (for context/debugging)
     * @property messageId Message containing the media (for context/debugging)
     *
     * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
     */
    data class TelegramThumb(
        val remoteId: String,
        val chatId: Long? = null,
        val messageId: Long? = null,
        override val preferredWidth: Int? = null,
        override val preferredHeight: Int? = null,
    ) : ImageRef

    /**
     * Local file reference.
     *
     * Used for:
     * - Already downloaded Telegram thumbnails
     * - Cached images on disk
     * - User-provided local images
     *
     * Fetcher: Standard Coil FileUriFetcher
     *
     * @property path Absolute path to the local file
     */
    data class LocalFile(
        val path: String,
        override val preferredWidth: Int? = null,
        override val preferredHeight: Int? = null,
    ) : ImageRef

    /**
     * Inline bytes reference (e.g., TDLib minithumbnails).
     *
     * Used for:
     * - TDLib minithumbnails (~40x40 pixel inline JPEGs)
     * - Blur placeholders before full thumbnail loads
     * - Instant preview without network request
     *
     * Fetcher: ImageRefFetcher decodes directly from ByteArray
     *
     * **Performance:** Zero network latency - bytes are already in memory. Typically used as
     * blur-placeholder until full thumbnail downloads.
     *
     * @property bytes Raw JPEG/PNG bytes
     * @property mimeType MIME type (default: image/jpeg for TDLib minithumbnails)
     */
    data class InlineBytes(
        val bytes: ByteArray,
        val mimeType: String = "image/jpeg",
        override val preferredWidth: Int? = null,
        override val preferredHeight: Int? = null,
    ) : ImageRef {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is InlineBytes) return false
            return bytes.contentEquals(other.bytes) &&
                mimeType == other.mimeType &&
                preferredWidth == other.preferredWidth &&
                preferredHeight == other.preferredHeight
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + (preferredWidth ?: 0)
            result = 31 * result + (preferredHeight ?: 0)
            return result
        }
    }

    companion object {
        /**
         * Create an ImageRef from a URL string.
         *
         * **CANONICAL PARSING FUNCTION** - This is the ONLY place where string → ImageRef
         * parsing should happen outside of persistence layer converters.
         *
         * Intelligently determines the appropriate variant:
         * - `tg://thumb/...` → TelegramThumb (parsed from URI)
         * - `tg://...` → TelegramThumb (legacy format)
         * - `tg:<remoteId>` → TelegramThumb (NxCatalogWriter format)
         * - `file://...` → LocalFile
         * - `file:<path>` → LocalFile (NxCatalogWriter format)
         * - `http://...` or `https://...` → Http
         * - `http:<url>` → Http (NxCatalogWriter format without scheme)
         * - `Http(url=...)` → Http (Kotlin data class toString() fallback)
         * - `TelegramThumb(remoteId=...)` → TelegramThumb (Kotlin data class toString() fallback)
         * - `LocalFile(path=...)` → LocalFile (Kotlin data class toString() fallback)
         *
         * ## URI Format (v2 remoteId-first):
         * `tg://thumb/<remoteId>?chatId=123&messageId=456`
         *
         * ## NxCatalogWriter Format:
         * `http:<url>`, `tg:<remoteId>`, `file:<path>`
         *
         * @param urlOrPath URL or path string
         * @return Appropriate ImageRef variant, or null if invalid
         */
        fun fromString(urlOrPath: String?): ImageRef? {
            if (urlOrPath.isNullOrBlank()) return null

            // === Handle Kotlin data class toString() format (from corrupt data or logging) ===
            // Pattern: Http(url=https://..., headers={}, ...)
            if (urlOrPath.startsWith("Http(")) {
                return parseHttpToString(urlOrPath)
            }
            // Pattern: TelegramThumb(remoteId=..., chatId=..., ...)
            if (urlOrPath.startsWith("TelegramThumb(")) {
                return parseTelegramThumbToString(urlOrPath)
            }
            // Pattern: LocalFile(path=..., ...)
            if (urlOrPath.startsWith("LocalFile(")) {
                return parseLocalFileToString(urlOrPath)
            }

            // === Handle standard URL formats ===
            return when {
                // v2 Telegram URI format: tg://thumb/<remoteId>?chatId=123&messageId=456
                urlOrPath.startsWith("tg://thumb/") -> parseTelegramThumbUri(urlOrPath)
                // Legacy Telegram format: tg://<remoteId>
                urlOrPath.startsWith("tg://") -> TelegramThumb(remoteId = urlOrPath.removePrefix("tg://"))
                // Standard file URI
                urlOrPath.startsWith("file://") -> LocalFile(urlOrPath.removePrefix("file://"))
                // Local absolute path
                urlOrPath.startsWith("/") -> LocalFile(urlOrPath)
                // Standard HTTP/HTTPS URLs
                urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://") ->
                    Http(urlOrPath)
                // NxCatalogWriter prefixed format: "type:value"
                else -> parsePrefixedFormat(urlOrPath)
            }
        }

        /**
         * Parse NxCatalogWriter prefixed format: "http:<url>", "tg:<remoteId>", "file:<path>"
         */
        private fun parsePrefixedFormat(s: String): ImageRef? {
            val colonIndex = s.indexOf(':')
            if (colonIndex < 0) return null

            val prefix = s.substring(0, colonIndex)
            val value = s.substring(colonIndex + 1)

            return when (prefix) {
                "http" -> Http(url = value)
                "https" -> Http(url = "https:$value") // Reconstruct full URL
                "tg" -> TelegramThumb(remoteId = value.removePrefix("//"))
                "file" -> LocalFile(path = value)
                else -> null
            }
        }

        /**
         * Parse Http data class toString() format: "Http(url=https://..., headers={}, ...)"
         */
        private fun parseHttpToString(s: String): Http? {
            val urlMatch = Regex("""url=([^,)]+)""").find(s)
            val url = urlMatch?.groupValues?.get(1)?.trim() ?: return null
            return Http(url = url)
        }

        /**
         * Parse TelegramThumb data class toString() format: "TelegramThumb(remoteId=..., chatId=..., ...)"
         */
        private fun parseTelegramThumbToString(s: String): TelegramThumb? {
            val remoteIdMatch = Regex("""remoteId=([^,)]+)""").find(s)
            val remoteId = remoteIdMatch?.groupValues?.get(1)?.trim() ?: return null
            val chatIdMatch = Regex("""chatId=([^,)]+)""").find(s)
            val chatId =
                chatIdMatch
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.toLongOrNull()
            val messageIdMatch = Regex("""messageId=([^,)]+)""").find(s)
            val messageId =
                messageIdMatch
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.toLongOrNull()
            return TelegramThumb(
                remoteId = remoteId,
                chatId = chatId,
                messageId = messageId,
            )
        }

        /**
         * Parse LocalFile data class toString() format: "LocalFile(path=..., ...)"
         */
        private fun parseLocalFileToString(s: String): LocalFile? {
            val pathMatch = Regex("""path=([^,)]+)""").find(s)
            val path = pathMatch?.groupValues?.get(1)?.trim() ?: return null
            return LocalFile(path = path)
        }

        /**
         * Parse a `tg://thumb/<remoteId>?chatId=123&messageId=456` URI into TelegramThumb.
         *
         * ## v2 Format (remoteId-first):
         * `tg://thumb/<remoteId>?chatId=123&messageId=456`
         *
         * Note: remoteId may contain special characters, so it's URL-encoded in the path.
         */
        private fun parseTelegramThumbUri(uri: String): TelegramThumb? {
            // Format: tg://thumb/<remoteId>?chatId=123&messageId=456
            val path = uri.removePrefix("tg://thumb/")
            val parts = path.split("?", limit = 2)

            // remoteId is the path component (URL-decoded)
            val remoteId = java.net.URLDecoder.decode(parts[0], "UTF-8")
            if (remoteId.isBlank()) return null

            var chatId: Long? = null
            var messageId: Long? = null

            if (parts.size > 1) {
                val params =
                    parts[1].split("&").associate {
                        val kv = it.split("=", limit = 2)
                        kv[0] to kv.getOrElse(1) { "" }
                    }
                chatId = params["chatId"]?.toLongOrNull()
                messageId = params["messageId"]?.toLongOrNull()
            }

            return TelegramThumb(
                remoteId = remoteId,
                chatId = chatId,
                messageId = messageId,
            )
        }
    }
}

/**
 * Convert ImageRef to a URI string for caching/serialization.
 *
 * ## v2 Format for TelegramThumb:
 * `tg://thumb/<remoteId>?chatId=123&messageId=456`
 *
 * Note: remoteId is URL-encoded to handle special characters.
 *
 * @return URI string representation
 */
fun ImageRef.toUriString(): String =
    when (this) {
        is ImageRef.Http -> url
        is ImageRef.TelegramThumb ->
            buildString {
                append("tg://thumb/")
                append(java.net.URLEncoder.encode(remoteId, "UTF-8"))
                val params = mutableListOf<String>()
                chatId?.let { params.add("chatId=$it") }
                messageId?.let { params.add("messageId=$it") }
                if (params.isNotEmpty()) {
                    append("?")
                    append(params.joinToString("&"))
                }
            }
        is ImageRef.LocalFile -> "file://$path"
        is ImageRef.InlineBytes -> "inline:${bytes.size}bytes"
    }

/** Create a copy with preferred dimensions. */
fun ImageRef.withPreferredSize(
    width: Int? = null,
    height: Int? = null,
): ImageRef =
    when (this) {
        is ImageRef.Http -> copy(preferredWidth = width, preferredHeight = height)
        is ImageRef.TelegramThumb -> copy(preferredWidth = width, preferredHeight = height)
        is ImageRef.LocalFile -> copy(preferredWidth = width, preferredHeight = height)
        is ImageRef.InlineBytes -> copy(preferredWidth = width, preferredHeight = height)
    }
