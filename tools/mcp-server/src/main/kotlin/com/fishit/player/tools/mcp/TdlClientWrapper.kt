package com.fishit.player.tools.mcp

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.io.File

/**
 * TDLib Client Wrapper for MCP Server
 *
 * Based on g00sha's tdl-coroutines JVM example:
 * https://github.com/g000sha256/tdl-coroutines/tree/master/tdl-coroutines-example-jvm
 *
 * Uses dev.g000sha256:tdl-coroutines:X.X.X which:
 * - Bundles native libs for JVM (unlike older versions)
 * - Provides typed Kotlin API with coroutines
 * - Handles all serialization/deserialization internally
 *
 * @see https://github.com/g000sha256/tdl-coroutines
 */
class TdlClientWrapper private constructor(
    private val client: TdlClient,
    private val scope: CoroutineScope
) {
    @Volatile
    private var authState: AuthorizationState? = null
    
    companion object {
        private val FILES_DIRECTORY = File(System.getProperty("user.home"), ".fishit-mcp/tdlib-data")
        
        /**
         * Create a new TDLib client wrapper.
         * 
         * @throws UnsatisfiedLinkError if native library is not available
         * @throws IllegalStateException if client creation fails
         */
        fun create(): TdlClientWrapper {
            FILES_DIRECTORY.mkdirs()
            
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = TdlClient.create()
            val wrapper = TdlClientWrapper(client, scope)
            wrapper.startUpdateListener()
            return wrapper
        }
    }
    
    private fun startUpdateListener() {
        client.authorizationStateUpdates
            .onEach { update -> 
                authState = update.authorizationState
                println("[TDL] Auth state: ${update.authorizationState::class.simpleName}")
            }
            .launchIn(scope)
    }
    
    /**
     * Get TDLib version string.
     */
    suspend fun getVersion(): String {
        val result = client.getOption(name = "version")
        return when (result) {
            is TdlResult.Success -> {
                val optionValue = result.result
                if (optionValue is OptionValueString) {
                    optionValue.value
                } else {
                    "unknown"
                }
            }
            is TdlResult.Failure -> "error: ${result.message}"
        }
    }
    
    /**
     * Get current authorization state as JSON.
     */
    fun getAuthorizationStateJson(): JsonObject {
        val state = authState
        return buildJsonObject {
            put("state", state?.let { it::class.simpleName } ?: "unknown")
            put("description", when (state) {
                is AuthorizationStateWaitTdlibParameters -> "Waiting for TDLib parameters"
                is AuthorizationStateWaitPhoneNumber -> "Waiting for phone number"
                is AuthorizationStateWaitCode -> "Waiting for authentication code"
                is AuthorizationStateWaitPassword -> "Waiting for 2FA password"
                is AuthorizationStateReady -> "Authenticated and ready"
                is AuthorizationStateLoggingOut -> "Logging out"
                is AuthorizationStateClosing -> "Closing"
                is AuthorizationStateClosed -> "Closed"
                null -> "Not initialized"
                else -> "Unknown state"
            })
        }
    }
    
    /**
     * Set TDLib parameters (required before authentication).
     */
    suspend fun setTdlibParameters(apiId: Int, apiHash: String): JsonObject {
        val result = client.setTdlibParameters(
            useTestDc = false,
            databaseDirectory = "${FILES_DIRECTORY.absolutePath}/database",
            filesDirectory = "${FILES_DIRECTORY.absolutePath}/files",
            databaseEncryptionKey = byteArrayOf(),
            useFileDatabase = true,
            useChatInfoDatabase = true,
            useMessageDatabase = true,
            useSecretChats = false,
            apiId = apiId,
            apiHash = apiHash,
            systemLanguageCode = "en",
            deviceModel = "MCP Server",
            systemVersion = "JVM",
            applicationVersion = "1.0.0",
        )
        return when (result) {
            is TdlResult.Success -> buildJsonObject {
                put("success", true)
            }
            is TdlResult.Failure -> buildJsonObject {
                put("success", false)
                put("error_code", result.code)
                put("error_message", result.message)
            }
        }
    }
    
    /**
     * Set phone number for authentication.
     */
    suspend fun setAuthenticationPhoneNumber(phoneNumber: String): JsonObject {
        val result = client.setAuthenticationPhoneNumber(phoneNumber = phoneNumber, settings = null)
        return when (result) {
            is TdlResult.Success -> buildJsonObject {
                put("success", true)
                put("message", "Code sent to phone")
            }
            is TdlResult.Failure -> buildJsonObject {
                put("success", false)
                put("error_code", result.code)
                put("error_message", result.message)
            }
        }
    }
    
    /**
     * Submit authentication code.
     */
    suspend fun checkAuthenticationCode(code: String): JsonObject {
        val result = client.checkAuthenticationCode(code)
        return when (result) {
            is TdlResult.Success -> buildJsonObject {
                put("success", true)
            }
            is TdlResult.Failure -> buildJsonObject {
                put("success", false)
                put("error_code", result.code)
                put("error_message", result.message)
            }
        }
    }
    
    /**
     * Submit 2FA password.
     */
    suspend fun checkAuthenticationPassword(password: String): JsonObject {
        val result = client.checkAuthenticationPassword(password)
        return when (result) {
            is TdlResult.Success -> buildJsonObject {
                put("success", true)
            }
            is TdlResult.Failure -> buildJsonObject {
                put("success", false)
                put("error_code", result.code)
                put("error_message", result.message)
            }
        }
    }
    
    /**
     * Get list of chats.
     */
    suspend fun getChats(limit: Int = 20): JsonObject {
        val result = client.getChats(chatList = null, limit = limit)
        return when (result) {
            is TdlResult.Success -> {
                val chats = result.result
                buildJsonObject {
                    put("success", true)
                    put("total_count", chats.totalCount)
                    putJsonArray("chat_ids") {
                        chats.chatIds.forEach { add(it) }
                    }
                }
            }
            is TdlResult.Failure -> buildJsonObject {
                put("success", false)
                put("error_code", result.code)
                put("error_message", result.message)
            }
        }
    }
    
    /**
     * Get chat info by ID.
     */
    suspend fun getChat(chatId: Long): JsonObject {
        val result = client.getChat(chatId)
        return when (result) {
            is TdlResult.Success -> {
                val chat = result.result
                buildJsonObject {
                    put("success", true)
                    put("id", chat.id)
                    put("title", chat.title)
                    put("type", chat.type::class.simpleName)
                }
            }
            is TdlResult.Failure -> buildJsonObject {
                put("success", false)
                put("error_code", result.code)
                put("error_message", result.message)
            }
        }
    }
    
    /**
     * Get chat history (messages).
     */
    suspend fun getChatHistory(chatId: Long, fromMessageId: Long = 0, limit: Int = 20): JsonObject {
        val result = client.getChatHistory(
            chatId = chatId,
            fromMessageId = fromMessageId,
            offset = 0,
            limit = limit,
            onlyLocal = false
        )
        return when (result) {
            is TdlResult.Success -> {
                val messages = result.result
                buildJsonObject {
                    put("success", true)
                    put("total_count", messages.totalCount)
                    putJsonArray("messages") {
                        messages.messages.filterNotNull().forEach { msg ->
                            addJsonObject {
                                put("id", msg.id)
                                put("chat_id", msg.chatId)
                                put("date", msg.date)
                                put("content_type", msg.content::class.simpleName)
                                // Add more fields based on content type
                                when (val content = msg.content) {
                                    is MessageVideo -> {
                                        put("duration", content.video.duration)
                                        put("width", content.video.width)
                                        put("height", content.video.height)
                                        put("file_name", content.video.fileName)
                                        put("mime_type", content.video.mimeType)
                                        put("file_size", content.video.video.size)
                                    }
                                    is MessagePhoto -> {
                                        put("has_photo", true)
                                    }
                                    is MessageDocument -> {
                                        put("file_name", content.document.fileName)
                                        put("mime_type", content.document.mimeType)
                                        put("file_size", content.document.document.size)
                                    }
                                    is MessageText -> {
                                        put("text", content.text.text)
                                    }
                                    else -> { /* other content types */ }
                                }
                            }
                        }
                    }
                }
            }
            is TdlResult.Failure -> buildJsonObject {
                put("success", false)
                put("error_code", result.code)
                put("error_message", result.message)
            }
        }
    }
    
    /**
     * Close the TDLib client.
     */
    suspend fun close() {
        try {
            client.close()
        } catch (e: Exception) {
            System.err.println("Error closing TDLib client: ${e.message}")
        }
    }
}
