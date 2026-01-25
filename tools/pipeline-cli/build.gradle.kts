plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.fishit.player.tools.cli"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Pipeline and transport dependencies (headless)
    implementation(project(":core:model"))
    implementation(project(":core:metadata-normalizer"))
    implementation(project(":infra:logging"))
    implementation(project(":infra:transport-telegram"))
    implementation(project(":infra:transport-xtream"))
    implementation(project(":pipeline:telegram"))
    implementation(project(":pipeline:xtream"))

    // Coroutines (core only - no Android coroutines)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // CLI framework - Clikt
    implementation("com.github.ajalt.clikt:clikt:4.4.0")

    // JSON serialization for --json output
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

// NOTE: This module is headless (no UI/Compose/DI)
// It exists as an Android library ONLY because dependencies are Android modules
// See docs/v2/FROZEN_MODULE_MANIFEST.md for JVM-only enforcement rules
