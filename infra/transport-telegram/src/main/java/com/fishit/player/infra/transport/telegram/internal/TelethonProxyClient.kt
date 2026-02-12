package com.fishit.player.infra.transport.telegram.internal

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.net.URLEncoder

/**
 * OkHttp client for communicating with the Telethon HTTP proxy.
 *
 * All methods are synchronous (blocking) — callers are expected to
 * run them on `Dispatchers.IO` via Kotlin coroutines.
 *
 * @property baseUrl Base URL of the proxy (e.g., "http://127.0.0.1:8089")
 * @param client DI-provided OkHttpClient with Telegram-specific timeouts.
 *               Constructed in [TelegramTransportModule] as a qualified singleton.
 */
class TelethonProxyClient(
    private val config: TelegramSessionConfig,
    private val client: OkHttpClient,
) {
    private val baseUrl: String get() = config.proxyBaseUrl

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "TelethonProxyClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    // ── Auth endpoints ──────────────────────────────────────────────────────

    /** GET /auth/status → {"authorized": bool} */
    fun getAuthStatus(): Boolean {
        val response = get("/auth/status")
        return response.jsonObject["authorized"]?.jsonPrimitive?.boolean ?: false
    }

    /** POST /auth/phone → {"phoneCodeHash": "..."} */
    fun sendPhone(phone: String): String {
        val body = buildJsonObject { put("phone", phone) }
        val response = post("/auth/phone", json.encodeToString(body))
        return response.jsonObject["phoneCodeHash"]?.jsonPrimitive?.content ?: ""
    }

    /** POST /auth/code → {"authorized": true} */
    fun sendCode(phone: String, code: String, phoneCodeHash: String) {
        val body = buildJsonObject {
            put("phone", phone)
            put("code", code)
            put("phoneCodeHash", phoneCodeHash)
        }
        post("/auth/code", json.encodeToString(body))
    }

    /** POST /auth/password → {"authorized": true} */
    fun sendPassword(password: String) {
        val body = buildJsonObject { put("password", password) }
        post("/auth/password", json.encodeToString(body))
    }

    /** POST /auth/logout */
    fun logout() {
        post("/auth/logout", "{}")
    }

    /** GET /me → {"id": 12345, "username": "..."} */
    fun getCurrentUserId(): Long {
        val response = get("/me")
        return response.jsonObject["id"]?.jsonPrimitive?.long ?: 0L
    }

    // ── Chat endpoints ──────────────────────────────────────────────────────

    /** GET /chats?limit=N → [{"id":..., "title":..., ...}] */
    fun getChats(limit: Int = 100): JsonArray {
        return get("/chats?limit=$limit").jsonArray
    }

    /** GET /chat?id=X → {"id":..., "title":..., ...} */
    fun getChat(chatId: Long): JsonObject {
        return get("/chat?id=$chatId").jsonObject
    }

    // ── Message endpoints ───────────────────────────────────────────────────

    /** GET /messages?chat=X&limit=Y&offset=Z → [...] */
    fun getMessages(chatId: Long, limit: Int = 100, offsetId: Int = 0): JsonArray {
        return get("/messages?chat=$chatId&limit=$limit&offset=$offsetId").jsonArray
    }

    /** GET /messages/search?chat=X&q=Y&limit=Z → [...] */
    fun searchMessages(chatId: Long, query: String, limit: Int = 100): JsonArray {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return get("/messages/search?chat=$chatId&q=$encodedQuery&limit=$limit").jsonArray
    }

    // ── File endpoints ──────────────────────────────────────────────────────

    /** GET /file/info?chat=X&id=Y → content info JSON */
    fun getFileInfo(chatId: Long, messageId: Long): JsonObject {
        return get("/file/info?chat=$chatId&id=$messageId").jsonObject
    }

    /**
     * GET /file?chat=X&id=Y with optional Range header.
     *
     * Returns the raw response InputStream for streaming.
     * Caller MUST close the returned [FileStreamResponse].
     */
    fun streamFile(chatId: Long, messageId: Long, rangeStart: Long? = null, rangeEnd: Long? = null): FileStreamResponse {
        val url = "$baseUrl/file?chat=$chatId&id=$messageId"
        val requestBuilder = Request.Builder().url(url).get()

        if (rangeStart != null) {
            val rangeValue = if (rangeEnd != null) {
                "bytes=$rangeStart-$rangeEnd"
            } else {
                "bytes=$rangeStart-"
            }
            requestBuilder.addHeader("Range", rangeValue)
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
        val contentType = response.header("Content-Type") ?: "application/octet-stream"
        val isPartial = response.code == 206

        return FileStreamResponse(
            stream = response.body?.byteStream() ?: InputStream.nullInputStream(),
            contentLength = contentLength,
            contentType = contentType,
            isPartial = isPartial,
            closeable = response,
        )
    }

    // ── Thumbnail endpoint ──────────────────────────────────────────────────

    /** GET /thumb?chat=X&id=Y → raw image bytes */
    fun getThumbnail(chatId: Long, messageId: Long): ByteArray? {
        val url = "$baseUrl/thumb?chat=$chatId&id=$messageId"
        val request = Request.Builder().url(url).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    UnifiedLog.d(TAG) { "getThumbnail: chat=$chatId msg=$messageId → ${bytes?.size ?: 0} bytes" }
                    bytes
                } else {
                    UnifiedLog.w(TAG) { "getThumbnail: chat=$chatId msg=$messageId → HTTP ${response.code}" }
                    null
                }
            }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "getThumbnail: chat=$chatId msg=$messageId failed" }
            null
        }
    }

    // ── Health ───────────────────────────────────────────────────────────────

    /** GET /health → {"status": "ok", ...} */
    fun health(): Boolean {
        return try {
            val response = get("/health")
            val ok = response.jsonObject["status"]?.jsonPrimitive?.content == "ok"
            UnifiedLog.d(TAG) { "health check → $ok" }
            ok
        } catch (e: Exception) {
            UnifiedLog.w(TAG) { "health check failed: ${e.message}" }
            false
        }
    }

    // ── Internal HTTP helpers ────────────────────────────────────────────────

    private fun get(path: String): JsonElement {
        val url = "$baseUrl$path"
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw TelethonProxyException("Empty response from $path")

            if (!response.isSuccessful) {
                UnifiedLog.w(TAG) { "GET $path → HTTP ${response.code}" }
                throw TelethonProxyException("Proxy error ${response.code}: $body", response.code)
            }

            json.parseToJsonElement(body)
        }
    }

    private fun post(path: String, jsonBody: String): JsonElement {
        val url = "$baseUrl$path"
        val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(url).post(body).build()
        return client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw TelethonProxyException("Empty response from $path")

            if (!response.isSuccessful) {
                val errorJson = try {
                    json.parseToJsonElement(responseBody).jsonObject["error"]?.jsonPrimitive?.content
                } catch (_: Exception) { null }
                val errorMsg = errorJson ?: "Proxy error ${response.code}: $responseBody"
                UnifiedLog.w(TAG) { "POST $path → HTTP ${response.code}: $errorMsg" }
                throw TelethonProxyException(errorMsg, response.code)
            }

            json.parseToJsonElement(responseBody)
        }
    }
}

/**
 * Response from a file stream request. MUST be closed after use.
 */
class FileStreamResponse(
    val stream: InputStream,
    val contentLength: Long,
    val contentType: String,
    val isPartial: Boolean,
    private val closeable: AutoCloseable,
) : AutoCloseable {
    override fun close() = closeable.close()
}

/**
 * Exception thrown when the Telethon proxy returns an error.
 */
class TelethonProxyException(
    message: String,
    val httpCode: Int = 0,
) : RuntimeException(message)
