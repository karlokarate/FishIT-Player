package com.fishit.player.tools.docgen

import java.io.File
import java.time.LocalDate

/**
 * Extracts module dependencies from `build.gradle.kts` files and generates
 * a Markdown document with a Mermaid dependency graph.
 *
 * Generates: docs/architecture/MODULE_DEPENDENCIES.md
 */
class ModuleDependencyExtractor(private val rootDir: File) {

    private data class ModuleInfo(
        val path: String,
        val dir: String,
        val dependencies: List<String>,
        val plugins: List<String>,
    )

    fun extract() {
        val settingsFile = File(rootDir, "settings.gradle.kts")
        if (!settingsFile.exists()) {
            println("   ⚠ settings.gradle.kts not found")
            return
        }

        // Parse module paths from settings.gradle.kts
        val modulePaths = Regex("""include\("([^"]+)"\)""")
            .findAll(settingsFile.readText())
            .map { it.groupValues[1] }
            .toList()

        // For each module, parse its build.gradle.kts for project dependencies
        val modules = modulePaths.mapNotNull { modulePath ->
            val moduleDir = File(rootDir, modulePath.removePrefix(":").replace(":", "/"))
            val buildFile = File(moduleDir, "build.gradle.kts")
            if (!buildFile.exists()) return@mapNotNull null

            val content = buildFile.readText()

            // Extract project(...) dependencies
            val deps = Regex("""(?:implementation|api)\s*\(\s*project\s*\(\s*"([^"]+)"\s*\)\s*\)""")
                .findAll(content)
                .map { it.groupValues[1] }
                .toList()

            // Extract applied plugins
            val plugins = Regex("""id\s*\(\s*"([^"]+)"\s*\)""")
                .findAll(content)
                .map { it.groupValues[1] }
                .filter { !it.startsWith("org.jetbrains.kotlin") && !it.startsWith("com.google.devtools") }
                .toList()

            ModuleInfo(
                path = modulePath,
                dir = moduleDir.relativeTo(rootDir).path,
                dependencies = deps,
                plugins = plugins,
            )
        }

        generateMarkdown(modules)
    }

    private fun generateMarkdown(modules: List<ModuleInfo>) {
        val outputDir = File(rootDir, "docs/architecture")
        outputDir.mkdirs()
        val output = File(outputDir, "MODULE_DEPENDENCIES.md")

        output.writeText(
            buildString {
                appendLine("# Module Dependencies")
                appendLine()
                appendLine("**Generated on ${LocalDate.now()} — DO NOT EDIT MANUALLY**")
                appendLine()
                appendLine("> Regenerate with: `./gradlew :tools:doc-generator:run --args=\"--deps\"`")
                appendLine("> Or: `./gradlew generateModuleDocs`")
                appendLine()

                // Group modules by layer
                appendLine("## Overview")
                appendLine()
                appendLine("| Layer | Modules |")
                appendLine("|-------|---------|")
                val layers = mapOf(
                    "App" to modules.filter { it.path.startsWith(":app") },
                    "Feature" to modules.filter { it.path.startsWith(":feature:") },
                    "Core" to modules.filter { it.path.startsWith(":core:") },
                    "Player/Playback" to modules.filter { it.path.startsWith(":player:") || it.path.startsWith(":playback:") },
                    "Pipeline" to modules.filter { it.path.startsWith(":pipeline:") },
                    "Infrastructure" to modules.filter { it.path.startsWith(":infra:") },
                    "Tools" to modules.filter { it.path.startsWith(":tools:") },
                )
                layers.forEach { (layer, mods) ->
                    appendLine("| **$layer** | ${mods.size} modules |")
                }
                appendLine()
                appendLine("**Total:** ${modules.size} modules")
                appendLine()

                // Mermaid dependency graph
                appendLine("## Dependency Graph")
                appendLine()
                appendLine("```mermaid")
                appendLine("graph TD")

                // Add subgraphs by layer
                layers.filter { it.value.isNotEmpty() }.forEach { (layer, mods) ->
                    appendLine("    subgraph ${layer.replace("/", "_")}")
                    mods.forEach { mod ->
                        val id = mod.path.removePrefix(":").replace(":", "_")
                        val label = mod.path.removePrefix(":")
                        appendLine("        $id[\"$label\"]")
                    }
                    appendLine("    end")
                }

                // Add edges
                modules.forEach { mod ->
                    val fromId = mod.path.removePrefix(":").replace(":", "_")
                    mod.dependencies.forEach { dep ->
                        val toId = dep.removePrefix(":").replace(":", "_")
                        appendLine("    $fromId --> $toId")
                    }
                }

                appendLine("```")
                appendLine()

                // Module details
                appendLine("## Module Details")
                appendLine()

                layers.filter { it.value.isNotEmpty() }.forEach { (layer, mods) ->
                    appendLine("### $layer")
                    appendLine()
                    mods.sortedBy { it.path }.forEach { mod ->
                        appendLine("#### `${mod.path}`")
                        appendLine()
                        appendLine("**Path:** `${mod.dir}`")
                        appendLine()
                        if (mod.dependencies.isNotEmpty()) {
                            appendLine("**Dependencies:**")
                            mod.dependencies.sorted().forEach { appendLine("- `$it`") }
                        } else {
                            appendLine("**Dependencies:** None (leaf module)")
                        }
                        appendLine()
                    }
                }
            },
        )

        println("   ✅ Generated: ${output.relativeTo(rootDir).path}")
    }
}
