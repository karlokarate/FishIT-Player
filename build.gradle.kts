plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("com.google.devtools.ksp") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
    id("com.github.ben-manes.versions") version "0.51.0" apply true
    id("org.jetbrains.kotlinx.kover") version "0.9.0" apply true
    id("com.osacky.doctor") version "0.10.0" apply true
    
    // Add the dependency for the Google services Gradle plugin
    id("com.google.gms.google-services") version "4.4.4" apply false
}

// Apply quality plugins to all subprojects
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
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
