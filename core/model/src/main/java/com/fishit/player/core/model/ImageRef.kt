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
     * Fetcher: TelegramThumbFetcher in :core:ui-imaging
     * - Delegates to infra/telegram-core for TDLib file resolution
     * - Uses g00sha tdlib-coroutines repository
     * - Does NOT call raw TdApi directly
     *
     * @property fileId TDLib file ID for the thumbnail
     * @property uniqueId TDLib unique file ID (cross-session stable)
     * @property chatId Chat containing the media (for context)
     * @property messageId Message containing the media (for context)
     */
    data class TelegramThumb(
            val fileId: Int,
            val uniqueId: String,
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
         * Intelligently determines the appropriate variant:
         * - `tg://thumb/...` → TelegramThumb (parsed from URI)
         * - `file://...` → LocalFile
         * - `http://...` or `https://...` → Http
         *
         * @param urlOrPath URL or path string
         * @return Appropriate ImageRef variant, or null if invalid
         */
        fun fromString(urlOrPath: String?): ImageRef? {
            if (urlOrPath.isNullOrBlank()) return null

            return when {
                urlOrPath.startsWith("tg://thumb/") -> parseTelegramThumbUri(urlOrPath)
                urlOrPath.startsWith("file://") -> LocalFile(urlOrPath.removePrefix("file://"))
                urlOrPath.startsWith("/") -> LocalFile(urlOrPath)
                urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://") ->
                        Http(urlOrPath)
                else -> null
            }
        }

        /** Parse a `tg://thumb/<fileId>/<uniqueId>` URI into TelegramThumb. */
        private fun parseTelegramThumbUri(uri: String): TelegramThumb? {
            // Format: tg://thumb/<fileId>/<uniqueId>?chatId=123&messageId=456
            val path = uri.removePrefix("tg://thumb/")
            val parts = path.split("?", limit = 2)
            val pathParts = parts[0].split("/")

            if (pathParts.size < 2) return null

            val fileId = pathParts[0].toIntOrNull() ?: return null
            val uniqueId = pathParts[1]

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
                    fileId = fileId,
                    uniqueId = uniqueId,
                    chatId = chatId,
                    messageId = messageId,
            )
        }
    }
}

/**
 * Convert ImageRef to a URI string for caching/serialization.
 *
 * @return URI string representation
 */
fun ImageRef.toUriString(): String =
        when (this) {
            is ImageRef.Http -> url
            is ImageRef.TelegramThumb ->
                    buildString {
                        append("tg://thumb/$fileId/$uniqueId")
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
fun ImageRef.withPreferredSize(width: Int? = null, height: Int? = null): ImageRef =
        when (this) {
            is ImageRef.Http -> copy(preferredWidth = width, preferredHeight = height)
            is ImageRef.TelegramThumb -> copy(preferredWidth = width, preferredHeight = height)
            is ImageRef.LocalFile -> copy(preferredWidth = width, preferredHeight = height)
            is ImageRef.InlineBytes -> copy(preferredWidth = width, preferredHeight = height)
        }
