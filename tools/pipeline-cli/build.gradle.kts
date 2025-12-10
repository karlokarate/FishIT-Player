plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fishit.player.tools.cli"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
    // App Startup (includes all pipeline dependencies)
    implementation(project(":core:app-startup"))
    implementation(project(":core:model"))
    implementation(project(":core:metadata-normalizer"))
    implementation(project(":infra:logging"))
    implementation(project(":infra:transport-telegram"))
    implementation(project(":infra:transport-xtream"))
    implementation(project(":pipeline:telegram"))
    implementation(project(":pipeline:xtream"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // CLI framework - Clikt
    implementation("com.github.ajalt.clikt:clikt:4.4.0")

    // JSON serialization for --json output
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // DI
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
