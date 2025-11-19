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

        // Kotlin Gradle plugins (align with Kotlin 2.0.21 / Compose 1.9.x)
        kotlin("android") version "2.0.21" apply false
        kotlin("plugin.serialization") version "2.0.21" apply false
        kotlin("plugin.compose") version "2.0.21" apply false
        id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
        
        // Quality tools
        id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
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

// Modules
include(":app")
