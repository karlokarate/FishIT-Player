plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("com.google.devtools.ksp") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    // Hilt 2.56.2: supports Kotlin 2.1.0 metadata, compatible with AGP 8.8.2 + Gradle 8.13
    id("com.google.dagger.hilt.android") version "2.56.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
    id("com.github.ben-manes.versions") version "0.51.0" apply true
    id("org.jetbrains.kotlinx.kover") version "0.9.0" apply true
    id("com.osacky.doctor") version "0.10.0" apply true
    id("org.jetbrains.dokka") version "1.9.20" apply false
    
    // Firebase plugins
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    id("com.google.firebase.firebase-perf") version "1.4.2" apply false
}

// Apply quality plugins to all subprojects (except standalone JVM tools)
subprojects {
    // Skip tools modules (standalone JVM, no Android)
    if (project.path.startsWith(":tools:")) {
        return@subprojects
    }
    
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "org.jetbrains.dokka")
    
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.files("detekt-config.yml"))
        buildUponDefaultConfig = true
        allRules = false
    }
    
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        android.set(true)
        outputToConsole.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)
        
        filter {
            exclude("**/generated/**")
            exclude("**/build/**")
            exclude("**/reference/**")
        }
    }
}

// Configure dependency updates plugin
tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates").configure {
    // Reject all non stable versions
    rejectVersionIf {
        isNonStable(candidate.version)
    }
    
    // Optional: report in multiple formats
    outputFormatter = "json,html,txt"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

// Configure Kover for test coverage
kover {
    reports {
        filters {
            // Exclude generated code and tests
            excludes {
                classes(
                    "**/BuildConfig",
                    "**/R",
                    "**/R\$*",
                    "**/Manifest",
                    "**/Manifest\$*",
                    "**/*Test*",
                    "**/*\$*",
                    "**/reference/**",
                )
                packages(
                    "*.generated.*",
                    "*.build.*",
                )
            }
        }
    }
}

// Configure Gradle Doctor for build health checks
doctor {
    // Warn about negative Avoidance Savings  
    negativeAvoidanceThreshold = 500
    // Don't fail build on GC warnings
    warnWhenNotUsingParallelGC = false
    // Enable test caching
    enableTestCaching = true
}

// ==============================================================================
// DOCUMENTATION GENERATION (Issue #699)
// ==============================================================================

// Task: Generate Dokka HTML API reference for all Android modules
tasks.register("dokkaHtmlMultiModule") {
    group = "documentation"
    description = "Generate API reference documentation for all modules using Dokka"
    
    // Depend on all subproject dokkaHtml tasks (except tools)
    dependsOn(
        subprojects
            .filter { !it.path.startsWith(":tools:") }
            .mapNotNull { subproject ->
                try {
                    subproject.tasks.findByName("dokkaHtml")?.path
                } catch (_: Exception) {
                    null
                }
            }
    )
    
    doLast {
        val outputDir = layout.buildDirectory.dir("docs/reference").get().asFile
        outputDir.mkdirs()
        
        subprojects
            .filter { !it.path.startsWith(":tools:") }
            .forEach { subproject ->
                val dokkaOutput = subproject.layout.buildDirectory.dir("dokka/html").get().asFile
                if (dokkaOutput.exists()) {
                    copy {
                        from(dokkaOutput)
                        into(outputDir.resolve(subproject.path.removePrefix(":").replace(":", "/")))
                    }
                }
            }
        
        println("✅ Dokka API reference generated at: $outputDir")
    }
}

// Task: Generate module dependency graph as Markdown with Mermaid diagram
tasks.register("generateModuleDocs") {
    group = "documentation"
    description = "Generate module dependency documentation from build.gradle.kts files"
    
    doLast {
        val output = file("docs/architecture/MODULE_DEPENDENCIES.md")
        output.parentFile.mkdirs()
        
        val sb = StringBuilder()
        sb.appendLine("# Module Dependencies")
        sb.appendLine()
        sb.appendLine("**Generated on ${java.time.LocalDate.now()} — DO NOT EDIT MANUALLY**")
        sb.appendLine()
        sb.appendLine("> Regenerate with: `./gradlew generateModuleDocs`")
        sb.appendLine()
        sb.appendLine("## Dependency Graph")
        sb.appendLine()
        sb.appendLine("```mermaid")
        sb.appendLine("graph TD")
        
        val moduleDetails = mutableListOf<Triple<String, String, List<String>>>()
        
        subprojects.sortedBy { it.path }.forEach { module ->
            val deps = mutableListOf<String>()
            try {
                module.configurations
                    .filter { it.name == "implementation" || it.name == "api" }
                    .flatMap { it.dependencies }
                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                    .forEach { dep ->
                        val depPath = dep.dependencyProject.path
                        val fromId = module.path.removePrefix(":").replace(":", "_")
                        val toId = depPath.removePrefix(":").replace(":", "_")
                        sb.appendLine("    $fromId --> $toId")
                        deps.add(depPath)
                    }
            } catch (_: Exception) {
                // Skip modules that can't be resolved yet
            }
            
            moduleDetails.add(
                Triple(
                    module.path,
                    module.projectDir.relativeTo(rootProject.projectDir).path,
                    deps
                )
            )
        }
        
        sb.appendLine("```")
        sb.appendLine()
        sb.appendLine("## Module Details")
        sb.appendLine()
        
        moduleDetails.sortedBy { it.first }.forEach { (path, dir, deps) ->
            sb.appendLine("### `$path`")
            sb.appendLine()
            sb.appendLine("**Path:** `$dir`")
            sb.appendLine()
            if (deps.isNotEmpty()) {
                sb.appendLine("**Dependencies:**")
                deps.sorted().forEach { sb.appendLine("- `$it`") }
            } else {
                sb.appendLine("**Dependencies:** None (leaf module)")
            }
            sb.appendLine()
        }
        
        output.writeText(sb.toString())
        println("✅ Module dependencies generated at: ${output.path}")
    }
}
