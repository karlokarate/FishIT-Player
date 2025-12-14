plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fishit.player.core.playermodel"
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

// NOTE: This module has NO dependencies except Kotlin stdlib.
// This is intentional - it contains only primitive player types.
dependencies {
    // Core model for SourceType
    implementation(project(":core:model"))
}
