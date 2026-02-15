package com.fishit.player.tools.docgen

import java.io.File
import java.time.LocalDate

/**
 * FishIT-Player Documentation Generator — CLI entry point.
 *
 * Generates documentation artifacts from source code:
 * - Layer contracts (from @PipelineComponent annotations)
 * - Module dependency graph (from build.gradle.kts)
 * - Architecture diagrams (PlantUML/Mermaid)
 *
 * Usage:
 *   ./gradlew :tools:doc-generator:run --args="--all"
 *   ./gradlew :tools:doc-generator:run --args="--contracts"
 *   ./gradlew :tools:doc-generator:run --args="--diagrams"
 */
fun main(args: Array<String>) {
    val rootDir = findRootDir()
    println("📄 FishIT Documentation Generator")
    println("   Root: ${rootDir.absolutePath}")
    println("   Date: ${LocalDate.now()}")
    println()

    val commands = args.toSet().ifEmpty { setOf("--all") }

    if ("--all" in commands || "--contracts" in commands) {
        println("═══ Extracting Layer Contracts ═══")
        ContractExtractor(rootDir).extract()
        println()
    }

    if ("--all" in commands || "--diagrams" in commands) {
        println("═══ Generating Architecture Diagrams ═══")
        DiagramGenerator(rootDir).generate()
        println()
    }

    if ("--all" in commands || "--deps" in commands) {
        println("═══ Generating Module Dependencies ═══")
        ModuleDependencyExtractor(rootDir).extract()
        println()
    }

    println("✅ Documentation generation complete")
}

/**
 * Locate the project root directory.
 * Works both when run via Gradle (workingDir = rootProject) and standalone.
 */
private fun findRootDir(): File {
    // When run via Gradle, workingDir is set to rootProject.projectDir
    val cwd = File(System.getProperty("user.dir"))
    if (File(cwd, "settings.gradle.kts").exists()) return cwd

    // Walk up to find root
    var dir = cwd
    while (dir.parentFile != null) {
        if (File(dir, "settings.gradle.kts").exists()) return dir
        dir = dir.parentFile
    }
    error("Cannot find project root (settings.gradle.kts not found)")
}
