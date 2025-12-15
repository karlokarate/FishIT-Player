package com.fishit.player.tools.cli.commands

import com.fishit.player.tools.cli.CliPipelines
import com.fishit.player.pipeline.xtream.debug.XtreamDebugServiceImpl
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking

/**
 * Xtream pipeline commands.
 *
 * Subcommands:
 * - xt status: Show pipeline status
 * - xt list-vod: List VOD items
 * - xt list-series: List series
 * - xt list-live: List live channels
 * - xt inspect-id: Inspect a specific VOD item
 */
class XtreamCommand(private val pipelines: CliPipelines) : CliktCommand(
    name = "xt",
    help = "Xtream pipeline commands"
) {
    override fun run() = Unit

    init {
        subcommands(
            XtStatusCommand(pipelines),
            XtListVodCommand(pipelines),
            XtListSeriesCommand(pipelines),
            XtListLiveCommand(pipelines),
            XtInspectCommand(pipelines),
        )
    }
}

class XtStatusCommand(private val pipelines: CliPipelines) : CliktCommand(
    name = "status",
    help = "Show Xtream pipeline status"
) {
    private val json by option("--json", help = "Output as JSON").flag()

    override fun run() = runBlocking {
        val adapter = pipelines.xtream
        if (adapter == null) {
            echo("❌ Xtream pipeline not available")
            return@runBlocking
        }

        val service = XtreamDebugServiceImpl(adapter!!)
        val status = service.getStatus()

        if (json) {
            echo("""
                |{
                |  "baseUrl": "${status.baseUrl}",
                |  "isAuthenticated": ${status.isAuthenticated},
                |  "vodCount": ${status.vodCountEstimate},
                |  "seriesCount": ${status.seriesCountEstimate},
                |  "liveCount": ${status.liveCountEstimate}
                |}
            """.trimMargin())
        } else {
            echo("📺 Xtream Pipeline Status")
            echo("━".repeat(40))
            echo("Server:        ${status.baseUrl}")
            echo("Authenticated: ${if (status.isAuthenticated) "✅ Yes" else "❌ No"}")
            echo("━".repeat(40))
            echo("Content:")
            echo("  🎬 VOD:      ${status.vodCountEstimate}")
            echo("  📺 Series:   ${status.seriesCountEstimate}")
            echo("  📡 Live:     ${status.liveCountEstimate}")
        }
    }
}

class XtListVodCommand(private val pipelines: CliPipelines) : CliktCommand(
    name = "list-vod",
    help = "List VOD items"
) {
    private val limit by option("--limit", help = "Maximum items to show")
        .int()
        .default(20)
    private val json by option("--json", help = "Output as JSON").flag()

    override fun run() = runBlocking {
        val adapter = pipelines.xtream
        if (adapter == null) {
            echo("❌ Xtream pipeline not available")
            return@runBlocking
        }

        val service = XtreamDebugServiceImpl(adapter!!)
        val items = service.listVod(limit)

        if (json) {
            echo("[")
            items.forEachIndexed { index, item ->
                val comma = if (index < items.size - 1) "," else ""
                echo("""  {"streamId": ${item.streamId}, "title": "${item.title.replace("\"", "\\\"")}", "year": ${item.year ?: "null"}, "category": "${item.categoryName ?: ""}", "extension": "${item.extension ?: ""}", "type": "${item.normalizedMediaType}"}$comma""")
            }
            echo("]")
        } else {
            echo("🎬 VOD Items (limit: $limit)")
            echo("━".repeat(100))
            echo(String.format("%-10s %-50s %-8s %-12s %-8s %s", "ID", "Title", "Year", "Category", "Ext", "Type"))
            echo("─".repeat(100))

            for (item in items) {
                val title = item.title.take(48).padEnd(50)
                val year = item.year?.toString() ?: "-"
                val category = (item.categoryName ?: "-").take(10).padEnd(12)
                val ext = (item.extension ?: "-").take(6).padEnd(8)
                echo(String.format("%-10d %s %-8s %s %s %s", item.streamId, title, year, category, ext, item.normalizedMediaType))
            }

            echo("─".repeat(100))
            echo("Total: ${items.size} items")
        }
    }
}

class XtListSeriesCommand(private val pipelines: CliPipelines) : CliktCommand(
    name = "list-series",
    help = "List series"
) {
    private val limit by option("--limit", help = "Maximum items to show")
        .int()
        .default(20)
    private val json by option("--json", help = "Output as JSON").flag()

    override fun run() = runBlocking {
        val adapter = pipelines.xtream
        if (adapter == null) {
            echo("❌ Xtream pipeline not available")
            return@runBlocking
        }

        val service = XtreamDebugServiceImpl(adapter!!)
        val items = service.listSeries(limit)

        if (json) {
            echo("[")
            items.forEachIndexed { index, item ->
                val comma = if (index < items.size - 1) "," else ""
                echo("""  {"seriesId": ${item.seriesId}, "title": "${item.title.replace("\"", "\\\"")}", "year": ${item.year ?: "null"}, "category": "${item.categoryName ?: ""}", "rating": ${item.rating ?: "null"}}$comma""")
            }
            echo("]")
        } else {
            echo("📺 Series (limit: $limit)")
            echo("━".repeat(90))
            echo(String.format("%-10s %-50s %-8s %-12s %s", "ID", "Title", "Year", "Category", "Rating"))
            echo("─".repeat(90))

            for (item in items) {
                val title = item.title.take(48).padEnd(50)
                val year = item.year?.toString() ?: "-"
                val category = (item.categoryName ?: "-").take(10).padEnd(12)
                val rating = item.rating?.let { String.format("%.1f", it) } ?: "-"
                echo(String.format("%-10d %s %-8s %s %s", item.seriesId, title, year, category, rating))
            }

            echo("─".repeat(90))
            echo("Total: ${items.size} series")
        }
    }
}

class XtListLiveCommand(private val pipelines: CliPipelines) : CliktCommand(
    name = "list-live",
    help = "List live channels"
) {
    private val limit by option("--limit", help = "Maximum items to show")
        .int()
        .default(20)
    private val json by option("--json", help = "Output as JSON").flag()

    override fun run() = runBlocking {
        val adapter = pipelines.xtream
        if (adapter == null) {
            echo("❌ Xtream pipeline not available")
            return@runBlocking
        }

        val service = XtreamDebugServiceImpl(adapter!!)
        val items = service.listLive(limit)

        if (json) {
            echo("[")
            items.forEachIndexed { index, item ->
                val comma = if (index < items.size - 1) "," else ""
                echo("""  {"channelId": ${item.channelId}, "name": "${item.name.replace("\"", "\\\"")}", "category": "${item.categoryName ?: ""}", "tvArchive": ${item.hasTvArchive}}$comma""")
            }
            echo("]")
        } else {
            echo("📡 Live Channels (limit: $limit)")
            echo("━".repeat(80))
            echo(String.format("%-10s %-50s %-12s %s", "ID", "Name", "Category", "Archive"))
            echo("─".repeat(80))

            for (item in items) {
                val name = item.name.take(48).padEnd(50)
                val category = (item.categoryName ?: "-").take(10).padEnd(12)
                val archive = if (item.hasTvArchive) "✅" else "-"
                echo(String.format("%-10d %s %s %s", item.channelId, name, category, archive))
            }

            echo("─".repeat(80))
            echo("Total: ${items.size} channels")
        }
    }
}

class XtInspectCommand(private val pipelines: CliPipelines) : CliktCommand(
    name = "inspect-id",
    help = "Inspect a VOD item by ID"
) {
    private val id by option("--id", help = "Stream ID to inspect")
        .int()
        .required()
    private val json by option("--json", help = "Output as JSON").flag()

    override fun run() = runBlocking {
        val adapter = pipelines.xtream
        if (adapter == null) {
            echo("❌ Xtream pipeline not available")
            return@runBlocking
        }

        val service = XtreamDebugServiceImpl(adapter!!)
        val details = service.inspectVod(id)

        if (details == null) {
            echo("❌ VOD with ID $id not found")
            return@runBlocking
        }

        if (json) {
            echo("""
                |{
                |  "raw": {
                |    "id": ${details.raw.id},
                |    "name": "${details.raw.name.replace("\"", "\\\"")}",
                |    "streamIcon": "${details.raw.streamIcon ?: ""}",
                |    "categoryId": "${details.raw.categoryId ?: ""}",
                |    "containerExtension": "${details.raw.containerExtension ?: ""}"
                |  },
                |  "rawMedia": {
                |    "originalTitle": "${details.rawMedia.originalTitle.replace("\"", "\\\"")}",
                |    "mediaType": "${details.rawMedia.mediaType}",
                |    "year": ${details.rawMedia.year ?: "null"},
                |    "sourceType": "${details.rawMedia.sourceType}",
                |    "sourceId": "${details.rawMedia.sourceId}",
                |    "globalId": "${details.rawMedia.globalId}"
                |  }
                |}
            """.trimMargin())
        } else {
            echo("🔍 VOD Details: ID $id")
            echo("━".repeat(60))
            echo()
            echo("📦 Raw Data:")
            echo("  ID:        ${details.raw.id}")
            echo("  Name:      ${details.raw.name}")
            echo("  Icon:      ${details.raw.streamIcon ?: "(none)"}")
            echo("  Category:  ${details.raw.categoryId ?: "(none)"}")
            echo("  Extension: ${details.raw.containerExtension ?: "(none)"}")
            echo()
            echo("🎯 RawMediaMetadata:")
            echo("  Title:     ${details.rawMedia.originalTitle}")
            echo("  Type:      ${details.rawMedia.mediaType}")
            echo("  Year:      ${details.rawMedia.year ?: "(none)"}")
            echo("  Source:    ${details.rawMedia.sourceType}")
            echo("  SourceID:  ${details.rawMedia.sourceId}")
            echo("  GlobalID:  ${details.rawMedia.globalId}")
            echo()
            echo("🖼️ Images:")
            echo("  Poster:    ${details.rawMedia.poster?.let { "✅ Available" } ?: "❌ None"}")
            echo("  Backdrop:  ${details.rawMedia.backdrop?.let { "✅ Available" } ?: "❌ None"}")
            echo("  Thumbnail: ${details.rawMedia.thumbnail?.let { "✅ Available" } ?: "❌ None"}")
        }
    }
}
