package com.fishit.player.tools.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/**
 * Telegram Tools for MCP Server
 *
 * TDLib Integration using g00sha's tdl-coroutines library.
 * The library bundles native libs for Linux x64, macOS arm64, macOS x64.
 *
 * @see https://github.com/g000sha256/tdl-coroutines
 */
object TelegramTools {
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }
    
    // TDLib client instance (lazy initialization)
    private val tdlClient: TdlClientWrapper? by lazy {
        try {
            TdlClientWrapper.create()
        } catch (e: Exception) {
            System.err.println("Failed to create TDLib client: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun register(server: Server) {
        // Tool: Check Telegram configuration & TDLib status
        server.addTool(
            name = "telegram_config_check",
            description = """
                Check if Telegram API credentials are configured and TDLib is ready.
                Returns: Configuration status, TDLib client status, and masked credentials.
                
                Required environment variables:
                - TELEGRAM_API_ID: Telegram API ID
                - TELEGRAM_API_HASH: Telegram API Hash
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { _ ->
            checkTelegramConfig()
        }

        // Tool: Get TDLib version
        server.addTool(
            name = "telegram_tdlib_version",
            description = """
                Get the TDLib library version.
                Returns: TDLib version string.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { _ ->
            getTdLibVersion()
        }
        
        // Tool: Get auth state
        server.addTool(
            name = "telegram_auth_state",
            description = """
                Get current Telegram authorization state.
                Returns: Current auth state (WaitPhoneNumber, WaitCode, Ready, etc.)
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { _ ->
            getAuthState()
        }
        
        // Tool: Initialize TDLib parameters
        server.addTool(
            name = "telegram_init",
            description = """
                Initialize TDLib with API credentials (first step of authentication).
                Uses TELEGRAM_API_ID and TELEGRAM_API_HASH from environment.
                
                Returns: Success or error status.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { _ ->
            initTdLib()
        }
        
        // Tool: Set phone number
        server.addTool(
            name = "telegram_set_phone",
            description = """
                Set phone number for Telegram authentication.
                
                Parameters:
                - phone: Phone number in international format (e.g., +491234567890)
                
                Returns: Success (code will be sent) or error.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("phone") {
                        put("type", "string")
                        put("description", "Phone number in international format")
                    }
                },
                required = listOf("phone")
            )
        ) { request ->
            val phone = request.arguments?.get("phone")?.jsonPrimitive?.content ?: ""
            setPhoneNumber(phone)
        }
        
        // Tool: Submit auth code
        server.addTool(
            name = "telegram_submit_code",
            description = """
                Submit the authentication code received via SMS/Telegram.
                
                Parameters:
                - code: The authentication code
                
                Returns: Success or error (may need 2FA password next).
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("code") {
                        put("type", "string")
                        put("description", "Authentication code")
                    }
                },
                required = listOf("code")
            )
        ) { request ->
            val code = request.arguments?.get("code")?.jsonPrimitive?.content ?: ""
            submitCode(code)
        }
        
        // Tool: Submit 2FA password
        server.addTool(
            name = "telegram_submit_password",
            description = """
                Submit the 2FA password (if enabled on account).
                
                Parameters:
                - password: The 2FA password
                
                Returns: Success or error.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("password") {
                        put("type", "string")
                        put("description", "2FA password")
                    }
                },
                required = listOf("password")
            )
        ) { request ->
            val password = request.arguments?.get("password")?.jsonPrimitive?.content ?: ""
            submitPassword(password)
        }
        
        // Tool: Get chats
        server.addTool(
            name = "telegram_get_chats",
            description = """
                Get list of Telegram chats (requires authentication).
                
                Parameters:
                - limit: Maximum number of chats to return (default: 20)
                
                Returns: List of chat IDs and total count.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("limit") {
                        put("type", "number")
                        put("description", "Max chats to return (default: 20)")
                    }
                },
                required = emptyList()
            )
        ) { request ->
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 20
            getChats(limit)
        }
        
        // Tool: Get chat info
        server.addTool(
            name = "telegram_get_chat",
            description = """
                Get detailed info about a specific chat.
                
                Parameters:
                - chat_id: The chat ID
                
                Returns: Chat title, type, and other info.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("chat_id") {
                        put("type", "number")
                        put("description", "Chat ID")
                    }
                },
                required = listOf("chat_id")
            )
        ) { request ->
            val chatId = request.arguments?.get("chat_id")?.jsonPrimitive?.longOrNull ?: 0L
            getChat(chatId)
        }
        
        // Tool: Get chat history (messages)
        server.addTool(
            name = "telegram_get_messages",
            description = """
                Get messages from a chat (media channel).
                
                Parameters:
                - chat_id: The chat ID
                - from_message_id: Start from this message ID (0 = latest)
                - limit: Maximum messages to return (default: 20)
                
                Returns: List of messages with content types.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("chat_id") {
                        put("type", "number")
                        put("description", "Chat ID")
                    }
                    putJsonObject("from_message_id") {
                        put("type", "number")
                        put("description", "Start from message ID (0 = latest)")
                    }
                    putJsonObject("limit") {
                        put("type", "number")
                        put("description", "Max messages to return (default: 20)")
                    }
                },
                required = listOf("chat_id")
            )
        ) { request ->
            val chatId = request.arguments?.get("chat_id")?.jsonPrimitive?.longOrNull ?: 0L
            val fromMessageId = request.arguments?.get("from_message_id")?.jsonPrimitive?.longOrNull ?: 0L
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 20
            getChatHistory(chatId, fromMessageId, limit)
        }

        // Tool: Describe TgMessage structure
        server.addTool(
            name = "telegram_message_schema",
            description = """
                Get the schema/structure of TgMessage DTO.
                Returns: JSON schema describing the TgMessage data class.
                
                Use this to understand what fields are available when
                processing Telegram messages in the pipeline.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { _ ->
            getTgMessageSchema()
        }

        // Tool: Describe TgContent variants
        server.addTool(
            name = "telegram_content_schema",
            description = """
                Get the schema of TgContent sealed interface variants.
                Returns: All possible content types (Video, Photo, Document, etc.)
                
                TgContent is a sealed interface with these variants:
                - TgContent.Video: Video files with duration, dimensions
                - TgContent.Photo: Photo with sizes
                - TgContent.Document: Generic files
                - TgContent.Audio: Audio files
                - TgContent.Text: Text-only messages
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject { },
                required = emptyList()
            )
        ) { _ ->
            getTgContentSchema()
        }

        // Tool: Parse sample Telegram message JSON
        server.addTool(
            name = "telegram_parse_sample",
            description = """
                Parse a sample Telegram message JSON and show how it maps to TgMessage.
                
                Parameters:
                - json: Raw JSON from Telegram (or TDLib export)
                
                Returns: Parsed TgMessage structure with field mapping.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("json") {
                        put("type", "string")
                        put("description", "Raw Telegram message JSON")
                    }
                },
                required = listOf("json")
            )
        ) { request ->
            val jsonStr = request.arguments?.get("json")?.jsonPrimitive?.content ?: ""
            parseTelegramSample(jsonStr)
        }

        // Tool: Generate mock TgMessage for testing
        server.addTool(
            name = "telegram_mock_message",
            description = """
                Generate a mock TgMessage for pipeline testing.
                
                Parameters:
                - content_type: Type of content (video, photo, document, audio, text)
                - chat_id: Optional chat ID (default: -1001234567890)
                - message_id: Optional message ID (default: 12345)
                
                Returns: Mock TgMessage JSON that matches production structure.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("content_type") {
                        put("type", "string")
                        put("enum", JsonArray(listOf("video", "photo", "document", "audio", "text").map { JsonPrimitive(it) }))
                        put("description", "Type of content to generate")
                    }
                    putJsonObject("chat_id") {
                        put("type", "number")
                        put("description", "Chat ID (default: -1001234567890)")
                    }
                    putJsonObject("message_id") {
                        put("type", "number")
                        put("description", "Message ID (default: 12345)")
                    }
                },
                required = listOf("content_type")
            )
        ) { request ->
            val contentType = request.arguments?.get("content_type")?.jsonPrimitive?.content ?: "video"
            val chatId = request.arguments?.get("chat_id")?.jsonPrimitive?.longOrNull ?: -1001234567890L
            val messageId = request.arguments?.get("message_id")?.jsonPrimitive?.longOrNull ?: 12345L
            generateMockMessage(contentType, chatId, messageId)
        }
    }

    private fun checkTelegramConfig(): CallToolResult {
        val apiId = System.getenv("TELEGRAM_API_ID")
        val apiHash = System.getenv("TELEGRAM_API_HASH")
        val clientReady = tdlClient != null

        val result = buildJsonObject {
            put("configured", apiId != null && apiHash != null)
            put("api_id_set", apiId != null)
            put("api_hash_set", apiHash != null)
            if (apiId != null) {
                put("api_id_masked", "${apiId.take(2)}****${apiId.takeLast(2)}")
            }
            if (apiHash != null) {
                put("api_hash_masked", "${apiHash.take(4)}****${apiHash.takeLast(4)}")
            }
            
            put("tdlib_client_ready", clientReady)
            
            if (clientReady && tdlClient != null) {
                put("auth_state", tdlClient!!.getAuthorizationStateJson())
            }
            
            // Summary
            val status = when {
                !apiId.isNullOrBlank() && !apiHash.isNullOrBlank() && clientReady -> 
                    "READY - TDLib client initialized"
                !apiId.isNullOrBlank() && !apiHash.isNullOrBlank() -> 
                    "CREDENTIALS_SET - TDLib client failed to initialize"
                else -> 
                    "NOT_CONFIGURED - Set TELEGRAM_API_ID and TELEGRAM_API_HASH"
            }
            put("status", status)
        }

        return CallToolResult(
            content = listOf(TextContent(text = json.encodeToString(result))),
            isError = false
        )
    }
    
    private fun getTdLibVersion(): CallToolResult {
        val client = tdlClient
        if (client == null) {
            return CallToolResult(
                content = listOf(TextContent(text = """{"error": "TDLib client not initialized"}""")),
                isError = true
            )
        }
        
        return try {
            val version = runBlocking { client.getVersion() }
            CallToolResult(
                content = listOf(TextContent(text = buildJsonObject {
                    put("tdlib_version", version)
                    put("jvm_artifact", "dev.g000sha256:tdl-coroutines:5.0.0")
                }.toString())),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "${e.message}"}""")),
                isError = true
            )
        }
    }
    
    private fun getAuthState(): CallToolResult {
        val client = tdlClient
        if (client == null) {
            return CallToolResult(
                content = listOf(TextContent(text = """{"error": "TDLib client not initialized"}""")),
                isError = true
            )
        }
        
        return CallToolResult(
            content = listOf(TextContent(text = json.encodeToString(client.getAuthorizationStateJson()))),
            isError = false
        )
    }
    
    private fun initTdLib(): CallToolResult {
        val client = tdlClient
        if (client == null) {
            return CallToolResult(
                content = listOf(TextContent(text = """{"error": "TDLib client not initialized"}""")),
                isError = true
            )
        }
        
        val apiId = System.getenv("TELEGRAM_API_ID")?.toIntOrNull()
        val apiHash = System.getenv("TELEGRAM_API_HASH")
        
        if (apiId == null || apiHash == null) {
            return CallToolResult(
                content = listOf(TextContent(text = """{"error": "TELEGRAM_API_ID or TELEGRAM_API_HASH not set"}""")),
                isError = true
            )
        }
        
        return try {
            val result = runBlocking { client.setTdlibParameters(apiId, apiHash) }
            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "${e.message}"}""")),
                isError = true
            )
        }
    }
    
    private fun setPhoneNumber(phone: String): CallToolResult {
        val client = tdlClient
        if (client == null) {
            return CallToolResult(
                content = listOf(TextContent(text = """{"error": "TDLib client not initialized"}""")),
                isError = true
            )
        }
        
        return try {
            val result = runBlocking { client.setAuthenticationPhoneNumber(phone) }
            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "${e.message}"}""")),
                isError = true
            )
        }
    }
    
    private fun submitCode(code: String): CallToolResult {
        val client = tdlClient
        if (client == null) {
            return CallToolResult(
                content = listOf(TextContent(text = """{"error": "TDLib client not initialized"}""")),
                isError = true
            )
        }
        
        return try {
            val result = runBlocking { client.checkAuthenticationCode(code) }
            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "${e.message}"}""")),
                isError = true
            )
        }
    }
    
    private fun submitPassword(password: String): CallToolResult {
        val client = tdlClient
        if (client == null) {
            return CallToolResult(
                content = listOf(TextContent(text = """{"error": "TDLib client not initialized"}""")),
                isError = true
            )
        }
        
        return try {
            val result = runBlocking { client.checkAuthenticationPassword(password) }
            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "${e.message}"}""")),
                isError = true
            )
        }
    }
    
    private fun getChats(limit: Int): CallToolResult {
        val client = tdlClient
        if (client == null) {
            return CallToolResult(
                content = listOf(TextContent(text = """{"error": "TDLib client not initialized"}""")),
                isError = true
            )
        }
        
        return try {
            val result = runBlocking { client.getChats(limit) }
            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "${e.message}"}""")),
                isError = true
            )
        }
    }
    
    private fun getChat(chatId: Long): CallToolResult {
        val client = tdlClient
        if (client == null) {
            return CallToolResult(
                content = listOf(TextContent(text = """{"error": "TDLib client not initialized"}""")),
                isError = true
            )
        }
        
        return try {
            val result = runBlocking { client.getChat(chatId) }
            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "${e.message}"}""")),
                isError = true
            )
        }
    }
    
    private fun getChatHistory(chatId: Long, fromMessageId: Long, limit: Int): CallToolResult {
        val client = tdlClient
        if (client == null) {
            return CallToolResult(
                content = listOf(TextContent(text = """{"error": "TDLib client not initialized"}""")),
                isError = true
            )
        }
        
        return try {
            val result = runBlocking { client.getChatHistory(chatId, fromMessageId, limit) }
            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "${e.message}"}""")),
                isError = true
            )
        }
    }

    private fun getTgMessageSchema(): CallToolResult {
        val schema = buildJsonObject {
            put("type", "TgMessage")
            put("description", "Wrapper for Telegram messages with media content")
            putJsonObject("fields") {
                putJsonObject("id") {
                    put("type", "Long")
                    put("description", "Unique message ID within chat")
                }
                putJsonObject("chatId") {
                    put("type", "Long")
                    put("description", "Chat ID (negative for groups/channels)")
                }
                putJsonObject("date") {
                    put("type", "Int")
                    put("description", "Unix timestamp of message")
                }
                putJsonObject("content") {
                    put("type", "TgContent")
                    put("description", "Message content (sealed interface)")
                }
                putJsonObject("caption") {
                    put("type", "String?")
                    put("description", "Optional caption for media messages")
                }
                putJsonObject("senderName") {
                    put("type", "String?")
                    put("description", "Display name of sender")
                }
                putJsonObject("replyToMessageId") {
                    put("type", "Long?")
                    put("description", "ID of message being replied to")
                }
            }
        }

        return CallToolResult(
            content = listOf(TextContent(text = json.encodeToString(schema))),
            isError = false
        )
    }

    private fun getTgContentSchema(): CallToolResult {
        val schema = buildJsonObject {
            put("type", "TgContent (sealed interface)")
            putJsonArray("variants") {
                addJsonObject {
                    put("type", "TgContent.Video")
                    putJsonObject("fields") {
                        put("file", "TgFile")
                        put("duration", "Int (seconds)")
                        put("width", "Int")
                        put("height", "Int")
                        put("thumbnail", "TgPhotoSize?")
                        put("mimeType", "String?")
                        put("fileName", "String?")
                    }
                }
                addJsonObject {
                    put("type", "TgContent.Photo")
                    putJsonObject("fields") {
                        put("sizes", "List<TgPhotoSize>")
                    }
                }
                addJsonObject {
                    put("type", "TgContent.Document")
                    putJsonObject("fields") {
                        put("file", "TgFile")
                        put("fileName", "String?")
                        put("mimeType", "String?")
                        put("thumbnail", "TgPhotoSize?")
                    }
                }
                addJsonObject {
                    put("type", "TgContent.Audio")
                    putJsonObject("fields") {
                        put("file", "TgFile")
                        put("duration", "Int (seconds)")
                        put("title", "String?")
                        put("performer", "String?")
                    }
                }
                addJsonObject {
                    put("type", "TgContent.Text")
                    putJsonObject("fields") {
                        put("text", "String")
                    }
                }
            }
        }

        return CallToolResult(
            content = listOf(TextContent(text = json.encodeToString(schema))),
            isError = false
        )
    }

    private fun parseTelegramSample(jsonStr: String): CallToolResult {
        return try {
            val parsed = json.parseToJsonElement(jsonStr)
            val result = buildJsonObject {
                put("input", parsed)
                put("status", "parsed")
                putJsonObject("mapping_hints") {
                    put("message_id", "id field → TgMessage.id")
                    put("chat_id", "chat.id → TgMessage.chatId")
                    put("date", "date → TgMessage.date")
                    put("content", "Determined by message type (video/photo/document/etc)")
                }
            }
            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(result))),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent(text = """{"error": "Invalid JSON: ${e.message}"}""")),
                isError = true
            )
        }
    }

    private fun generateMockMessage(contentType: String, chatId: Long, messageId: Long): CallToolResult {
        val content = when (contentType) {
            "video" -> buildJsonObject {
                put("type", "Video")
                putJsonObject("file") {
                    put("id", 123456)
                    put("remoteId", "AgACAgIAAxkBAAI...")
                    put("size", 1073741824) // 1GB
                    put("downloadedSize", 0)
                }
                put("duration", 7200) // 2 hours
                put("width", 1920)
                put("height", 1080)
                put("mimeType", "video/mp4")
                put("fileName", "Movie.Title.2024.1080p.WEB-DL.mp4")
            }
            "photo" -> buildJsonObject {
                put("type", "Photo")
                putJsonArray("sizes") {
                    addJsonObject {
                        put("width", 320)
                        put("height", 180)
                        putJsonObject("file") {
                            put("id", 123457)
                            put("remoteId", "AgACAgIAAxkBAAI...")
                            put("size", 15000)
                        }
                    }
                    addJsonObject {
                        put("width", 1280)
                        put("height", 720)
                        putJsonObject("file") {
                            put("id", 123458)
                            put("remoteId", "AgACAgIAAxkBAAI...")
                            put("size", 150000)
                        }
                    }
                }
            }
            "document" -> buildJsonObject {
                put("type", "Document")
                putJsonObject("file") {
                    put("id", 123459)
                    put("remoteId", "AgACAgIAAxkBAAI...")
                    put("size", 52428800) // 50MB
                }
                put("fileName", "document.pdf")
                put("mimeType", "application/pdf")
            }
            "audio" -> buildJsonObject {
                put("type", "Audio")
                putJsonObject("file") {
                    put("id", 123460)
                    put("remoteId", "AgACAgIAAxkBAAI...")
                    put("size", 10485760) // 10MB
                }
                put("duration", 180) // 3 minutes
                put("title", "Song Title")
                put("performer", "Artist Name")
            }
            else -> buildJsonObject {
                put("type", "Text")
                put("text", "This is a text message")
            }
        }

        val message = buildJsonObject {
            put("id", messageId)
            put("chatId", chatId)
            put("date", System.currentTimeMillis() / 1000)
            put("content", content)
            put("caption", "Sample caption for $contentType content")
            put("senderName", "Test User")
            put("replyToMessageId", JsonNull)
        }

        return CallToolResult(
            content = listOf(TextContent(text = json.encodeToString(message))),
            isError = false
        )
    }
}
