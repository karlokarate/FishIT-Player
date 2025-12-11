pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // --- Platform: pin plugin versions project-wide ---
        id("com.android.application") version "8.6.1" apply false
        id("com.android.library") version "8.6.1" apply false

        // Kotlin Gradle plugins (align with Kotlin 2.1.0 / Compose 1.9.x)
        kotlin("android") version "2.1.0" apply false
        kotlin("plugin.serialization") version "2.1.0" apply false
        kotlin("plugin.compose") version "2.1.0" apply false
        id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
        
        // Quality tools
        id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
        id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
    }
    resolutionStrategy {
        eachPlugin {
            // Keep ObjectBox Gradle plugin resolution mapping
            if (requested.id.id == "io.objectbox") {
                useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FishITPlayer"

// ========== v2 Modules ==========
// App Entry
include(":app-v2")

// Core
include(":core:model")
include(":core:player-model")
include(":core:feature-api")
include(":core:persistence")
include(":core:metadata-normalizer")
include(":core:catalog-sync")
include(":core:firebase")
include(":core:ui-imaging")
include(":core:app-startup")

// Playback & Player
include(":playback:domain")
include(":playback:telegram")
include(":playback:xtream")
include(":player:internal")
include(":player:miniplayer")
include(":player:nextlib-codecs")

// Pipelines (no UI)
include(":pipeline:telegram")
include(":pipeline:xtream")
include(":pipeline:io")
include(":pipeline:audiobook")

// Feature Shells (UI)
include(":feature:home")
include(":feature:library")
include(":feature:live")
include(":feature:detail")
include(":feature:telegram-media")
include(":feature:audiobooks")
include(":feature:settings")

// Infrastructure
include(":infra:logging")
include(":infra:tooling")
include(":infra:transport-telegram")
include(":infra:transport-xtream")
include(":infra:data-telegram")
include(":infra:data-xtream")

// Tools (JVM CLI, no Android)
include(":tools:pipeline-cli")
