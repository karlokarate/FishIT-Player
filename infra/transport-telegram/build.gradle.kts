plugins {
    id("com.android.library")
    id("com.chaquo.python")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.fishit.player.infra.transport.telegram"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        // Required by Chaquopy — restrict to 64-bit ABIs for Android TV
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
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

// Chaquopy — Python runtime configuration for Telethon sidecar proxy
chaquopy {
    defaultConfig {
        // Python 3.10: Minimum version supporting PEP 604 union types (X | None)
        // used in tg_proxy.py. Chaquopy 17 supports 3.10–3.14.
        version = "3.10"

        pip {
            // Telethon — async Telegram MTProto client
            install("telethon==1.37.0")
            // pyaes — AES encryption for Telegram protocol
            install("pyaes>=1.6.1")
        }
    }
}

dependencies {
    // Core dependencies
    implementation(project(":core:model"))
    implementation(project(":core:ui-imaging"))  // For TelegramThumbFetcher interface
    implementation(project(":infra:logging"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // OkHttp — HTTP client for Telethon proxy communication
    implementation(libs.okhttp)

    // kotlinx.serialization — JSON parsing of proxy responses
    implementation(libs.kotlinx.serialization.json)

    // Coil 3 — For CoilTelegramThumbFetcherImpl bridge
    implementation("io.coil-kt.coil3:coil-core:3.3.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-compiler:2.56.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("io.mockk:mockk-agent-jvm:1.13.12")
}
