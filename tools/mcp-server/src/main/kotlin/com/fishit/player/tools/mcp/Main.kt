package com.fishit.player.tools.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * FishIT Pipeline MCP Server
 *
 * Provides tools for testing Xtream and Telegram pipelines with real data.
 * Runs as STDIO server for VS Code Copilot integration.
 *
 * Tools:
 * - xtream_* : Xtream API operations
 * - telegram_* : Telegram operations
 * - normalize_* : Pipeline normalization testing
 *
 * Configuration via environment variables:
 * - XTREAM_URL, XTREAM_USER, XTREAM_PASS
 * - TELEGRAM_API_ID, TELEGRAM_API_HASH
 */
fun main() = runBlocking {
    val server = Server(
        serverInfo = Implementation(
            name = "fishit-pipeline-mcp",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true)
            )
        )
    )

    // Register all tools
    XtreamTools.register(server)
    TelegramTools.register(server)
    NormalizerTools.register(server)

    // Setup STDIO transport (per SDK documentation)
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )

    // Connect and run
    server.connect(transport)
    
    val done = Job()
    server.onClose {
        done.complete()
    }
    done.join()
}
