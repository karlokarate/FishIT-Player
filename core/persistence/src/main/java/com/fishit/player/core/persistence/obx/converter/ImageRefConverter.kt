package com.fishit.player.core.persistence.obx.converter

import com.fishit.player.core.model.ImageRef
import io.objectbox.converter.PropertyConverter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ObjectBox PropertyConverter for [ImageRef] sealed interface.
 *
 * **Purpose:**
 * - Enables storing ImageRef in ObjectBox entities
 * - Serializes to JSON for storage, deserializes on read
 * - Preserves type information (Http, TelegramThumb, LocalFile, InlineBytes)
 *
 * **Architecture:**
 * - Uses kotlinx.serialization for type-safe JSON
 * - Intermediate DTO to avoid polluting core:model with serialization annotations
 * - Null-safe: null ImageRef → null JSON string
 *
 * **Contract Compliance:**
 * - IMAGING_SYSTEM.md: ImageRef is the canonical image abstraction
 * - Pipelines produce ImageRef, persistence stores it, UI consumes it
 */
class ImageRefConverter : PropertyConverter<ImageRef?, String?> {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            isLenient = true
        }

    override fun convertToEntityProperty(databaseValue: String?): ImageRef? {
        if (databaseValue.isNullOrBlank()) return null

        return try {
            val dto = json.decodeFromString<ImageRefDto>(databaseValue)
            dto.toImageRef()
        } catch (e: Exception) {
            // Fallback: try parsing as plain URL string (legacy data)
            ImageRef.fromString(databaseValue)
        }
    }

    override fun convertToDatabaseValue(entityProperty: ImageRef?): String? {
        if (entityProperty == null) return null

        return try {
            val dto = ImageRefDto.fromImageRef(entityProperty)
            json.encodeToString(dto)
        } catch (e: Exception) {
            // Fallback: store as URI string
            entityProperty.toUriString()
        }
    }
}

/**
 * Extension function for URI string conversion.
 *
 * ## v2 Format for TelegramThumb:
 * `tg://thumb/<remoteId>?chatId=123&messageId=456`
 *
 * Note: remoteId is URL-encoded to handle special characters.
 *
 * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
 */
private fun ImageRef.toUriString(): String =
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

// =============================================================================
// Serialization DTO
// =============================================================================

/**
 * Intermediate DTO for JSON serialization of ImageRef.
 *
 * Uses discriminator field "type" to identify the variant.
 */
@Serializable
private sealed class ImageRefDto {
    abstract fun toImageRef(): ImageRef

    @Serializable
    @SerialName("http")
    data class HttpDto(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val preferredWidth: Int? = null,
        val preferredHeight: Int? = null,
    ) : ImageRefDto() {
        override fun toImageRef() =
            ImageRef.Http(
                url = url,
                headers = headers,
                preferredWidth = preferredWidth,
                preferredHeight = preferredHeight,
            )
    }

    /**
     * Telegram thumbnail DTO for JSON serialization.
     *
     * ## v2 remoteId-First Architecture
     *
     * Uses only `remoteId` (stable across sessions).
     * fileId is resolved at runtime via `getRemoteFile(remoteId)`.
     *
     * @see contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
     */
    @Serializable
    @SerialName("telegram")
    data class TelegramThumbDto(
        val remoteId: String,
        val chatId: Long? = null,
        val messageId: Long? = null,
        val preferredWidth: Int? = null,
        val preferredHeight: Int? = null,
    ) : ImageRefDto() {
        override fun toImageRef() =
            ImageRef.TelegramThumb(
                remoteId = remoteId,
                chatId = chatId,
                messageId = messageId,
                preferredWidth = preferredWidth,
                preferredHeight = preferredHeight,
            )
    }

    @Serializable
    @SerialName("local")
    data class LocalFileDto(
        val path: String,
        val preferredWidth: Int? = null,
        val preferredHeight: Int? = null,
    ) : ImageRefDto() {
        override fun toImageRef() =
            ImageRef.LocalFile(
                path = path,
                preferredWidth = preferredWidth,
                preferredHeight = preferredHeight,
            )
    }

    @Serializable
    @SerialName("inline")
    data class InlineBytesDto(
        val bytesBase64: String,
        val mimeType: String = "image/jpeg",
        val preferredWidth: Int? = null,
        val preferredHeight: Int? = null,
    ) : ImageRefDto() {
        override fun toImageRef() =
            ImageRef.InlineBytes(
                bytes = android.util.Base64.decode(bytesBase64, android.util.Base64.DEFAULT),
                mimeType = mimeType,
                preferredWidth = preferredWidth,
                preferredHeight = preferredHeight,
            )
    }

    companion object {
        fun fromImageRef(ref: ImageRef): ImageRefDto =
            when (ref) {
                is ImageRef.Http ->
                    HttpDto(
                        url = ref.url,
                        headers = ref.headers,
                        preferredWidth = ref.preferredWidth,
                        preferredHeight = ref.preferredHeight,
                    )
                is ImageRef.TelegramThumb ->
                    TelegramThumbDto(
                        remoteId = ref.remoteId,
                        chatId = ref.chatId,
                        messageId = ref.messageId,
                        preferredWidth = ref.preferredWidth,
                        preferredHeight = ref.preferredHeight,
                    )
                is ImageRef.LocalFile ->
                    LocalFileDto(
                        path = ref.path,
                        preferredWidth = ref.preferredWidth,
                        preferredHeight = ref.preferredHeight,
                    )
                is ImageRef.InlineBytes ->
                    InlineBytesDto(
                        bytesBase64 =
                            android.util.Base64.encodeToString(
                                ref.bytes,
                                android.util.Base64.NO_WRAP,
                            ),
                        mimeType = ref.mimeType,
                        preferredWidth = ref.preferredWidth,
                        preferredHeight = ref.preferredHeight,
                    )
            }
    }
}
