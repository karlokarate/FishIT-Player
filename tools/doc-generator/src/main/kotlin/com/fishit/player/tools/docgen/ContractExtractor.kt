package com.fishit.player.tools.docgen

import java.io.File
import java.time.LocalDate

/**
 * Extracts layer contracts from source code by scanning for `@PipelineComponent` annotations
 * and KDoc `@responsibility` tags.
 *
 * Generates:
 * - docs/contracts/PIPELINE_LAYER_CONTRACT.md
 * - docs/contracts/TRANSPORT_LAYER_CONTRACT.md
 * - docs/contracts/NORMALIZER_CONTRACT.md
 */
class ContractExtractor(private val rootDir: File) {

    private data class ComponentContract(
        val name: String,
        val layer: String,
        val sourceType: String,
        val genericPattern: String,
        val responsibilities: List<String>,
        val interfaces: List<String>,
        val dependencies: List<String>,
        val filePath: String,
    )

    /**
     * Scan all .kt source files for @PipelineComponent annotations and extract contracts.
     */
    fun extract() {
        val sourceRoots = listOf(
            "core", "infra", "pipeline", "playback", "player", "feature",
        )

        val contracts = mutableListOf<ComponentContract>()

        sourceRoots.forEach { root ->
            val dir = File(rootDir, root)
            if (!dir.exists()) return@forEach

            dir.walkTopDown()
                .filter { it.extension == "kt" && !it.path.contains("/build/") }
                .forEach { file ->
                    extractFromFile(file)?.let { contracts.add(it) }
                }
        }

        if (contracts.isEmpty()) {
            println("   ⚠ No @PipelineComponent annotations found yet.")
            println("   → Annotate classes with @PipelineComponent to enable contract generation.")
            println("   → See: core/model/src/main/java/.../PipelineComponent.kt")
            generateEmptyContracts()
            return
        }

        val byLayer = contracts.groupBy { it.layer.uppercase() }

        byLayer["PIPELINE"]?.let {
            generateLayerContract("PIPELINE_LAYER_CONTRACT.md", "Pipeline Layer", it)
        }
        byLayer["TRANSPORT"]?.let {
            generateLayerContract("TRANSPORT_LAYER_CONTRACT.md", "Transport Layer", it)
        }
        byLayer["NORMALIZER"]?.let {
            generateLayerContract("NORMALIZER_CONTRACT.md", "Normalizer Layer", it)
        }
        byLayer["PERSISTENCE"]?.let {
            generateLayerContract("PERSISTENCE_LAYER_CONTRACT.md", "Persistence Layer", it)
        }
    }

    /**
     * Parse a single .kt file for @PipelineComponent annotation.
     */
    private fun extractFromFile(file: File): ComponentContract? {
        val content = file.readText()

        // Check for @PipelineComponent annotation
        val annotationRegex = Regex(
            """@PipelineComponent\(\s*layer\s*=\s*Layer\.(\w+)\s*,\s*sourceType\s*=\s*"([^"]+)"\s*,\s*genericPattern\s*=\s*"([^"]+)"\s*,?\s*\)""",
        )
        val annotationMatch = annotationRegex.find(content) ?: return null

        val layer = annotationMatch.groupValues[1]
        val sourceType = annotationMatch.groupValues[2]
        val genericPattern = annotationMatch.groupValues[3]

        // Extract class name
        val classRegex = Regex("""(?:class|object|interface)\s+(\w+)""")
        val className = classRegex.find(content, annotationMatch.range.last)?.groupValues?.get(1)
            ?: return null

        // Extract @responsibility tags from KDoc
        val responsibilities = Regex("""@responsibility\s+(.+)""")
            .findAll(content)
            .map { it.groupValues[1].trim() }
            .toList()

        // Extract implemented interfaces from class declaration
        val interfaces = extractInterfaces(content, className)

        // Extract constructor dependencies
        val dependencies = extractDependencies(content, className)

        val relativePath = file.relativeTo(rootDir).path

        return ComponentContract(
            name = className,
            layer = layer,
            sourceType = sourceType,
            genericPattern = genericPattern,
            responsibilities = responsibilities,
            interfaces = interfaces,
            dependencies = dependencies,
            filePath = relativePath,
        )
    }

    private fun extractInterfaces(content: String, className: String): List<String> {
        val classDecl = Regex("""class\s+$className[^{]*:\s*([^{]+)\{""").find(content)
            ?: return emptyList()
        return classDecl.groupValues[1]
            .split(",")
            .map { it.trim().substringBefore("(").substringBefore("<") }
            .filter { it.isNotBlank() && it[0].isUpperCase() }
    }

    private fun extractDependencies(content: String, className: String): List<String> {
        // Find constructor block after class name
        val constructorRegex = Regex(
            """class\s+$className\s*(?:@\w+\s*)*(?:constructor)?\s*\(([^)]+)\)""",
        )
        val match = constructorRegex.find(content) ?: return emptyList()
        return match.groupValues[1]
            .split(",")
            .mapNotNull { param ->
                val typeMatch = Regex(""":\s*(\w+)""").find(param)
                typeMatch?.groupValues?.get(1)
            }
    }

    private fun generateLayerContract(filename: String, title: String, contracts: List<ComponentContract>) {
        val outputDir = File(rootDir, "docs/contracts")
        outputDir.mkdirs()
        val output = File(outputDir, filename)

        output.writeText(
            buildString {
                appendLine("# $title Contract")
                appendLine()
                appendLine("**Generated from code on ${LocalDate.now()} — DO NOT EDIT MANUALLY**")
                appendLine()
                appendLine("> Regenerate with: `./gradlew :tools:doc-generator:run --args=\"--contracts\"`")
                appendLine()
                appendLine("---")
                appendLine()

                appendLine("## Components")
                appendLine()
                contracts.forEach { contract ->
                    appendLine("### ${contract.name}")
                    appendLine()
                    appendLine("- **Source Type:** ${contract.sourceType}")
                    appendLine("- **Generic Pattern:** `${contract.genericPattern}`")
                    appendLine("- **File:** `${contract.filePath}`")
                    appendLine()

                    if (contract.responsibilities.isNotEmpty()) {
                        appendLine("**Responsibilities:**")
                        contract.responsibilities.forEach { appendLine("- $it") }
                        appendLine()
                    }

                    if (contract.interfaces.isNotEmpty()) {
                        appendLine("**Implements:**")
                        contract.interfaces.forEach { appendLine("- `$it`") }
                        appendLine()
                    }

                    if (contract.dependencies.isNotEmpty()) {
                        appendLine("**Dependencies:**")
                        contract.dependencies.forEach { appendLine("- `$it`") }
                        appendLine()
                    }

                    appendLine("---")
                    appendLine()
                }

                appendLine("## Naming Convention")
                appendLine()
                appendLine("| Source Type | Pattern | Example |")
                appendLine("|------------|---------|---------|")
                contracts.forEach { contract ->
                    val example = contract.genericPattern.replace("{Source}", contract.sourceType)
                    appendLine("| ${contract.sourceType} | `${contract.genericPattern}` | `$example` |")
                }
                appendLine()

                appendLine("## Future Sources")
                appendLine()
                appendLine("When implementing new sources (Telegram, Plex, etc.), follow the same patterns:")
                appendLine()
                contracts.forEach { contract ->
                    appendLine("- `${contract.genericPattern.replace("{Source}", "Telegram")}` (Telegram)")
                    appendLine("- `${contract.genericPattern.replace("{Source}", "Plex")}` (Plex)")
                }
            },
        )

        println("   ✅ Generated: ${output.relativeTo(rootDir).path}")
    }

    private fun generateEmptyContracts() {
        val outputDir = File(rootDir, "docs/contracts")
        outputDir.mkdirs()

        listOf("PIPELINE_LAYER_CONTRACT.md", "TRANSPORT_LAYER_CONTRACT.md", "NORMALIZER_CONTRACT.md").forEach { filename ->
            val title = filename.removeSuffix(".md").replace("_", " ")
            val output = File(outputDir, filename)
            output.writeText(
                buildString {
                    appendLine("# $title")
                    appendLine()
                    appendLine("**Generated on ${LocalDate.now()} — DO NOT EDIT MANUALLY**")
                    appendLine()
                    appendLine("> No `@PipelineComponent` annotations found yet.")
                    appendLine("> Annotate classes and re-run: `./gradlew :tools:doc-generator:run --args=\"--contracts\"`")
                },
            )
            println("   ✅ Generated (empty): ${output.relativeTo(rootDir).path}")
        }
    }
}
