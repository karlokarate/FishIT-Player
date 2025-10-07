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

        // Kotlin Gradle plugins (align with Kotlin 2.1.x)
        kotlin("android") version "2.1.0" apply false
        kotlin("kapt") version "2.1.0" apply false
        kotlin("plugin.serialization") version "2.1.0" apply false
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
include(":libtd")
