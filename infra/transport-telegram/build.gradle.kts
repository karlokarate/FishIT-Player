plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fishit.player.infra.transport.telegram"
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
    // Core dependencies
    implementation(project(":core:model"))
    implementation(project(":core:ui-imaging"))  // For TelegramThumbFetcher interface
    implementation(project(":infra:logging"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // TDLib integration (g00sha tdlib-coroutines)
    api("dev.g000sha256:tdl-coroutines-android:8.0.0")
    
    // Coil 3 - For CoilTelegramThumbFetcherImpl bridge
    implementation("io.coil-kt.coil3:coil-core:3.3.0")
    
    // OkIO - For file sources
    implementation("com.squareup.okio:okio:3.9.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.59")
    ksp("com.google.dagger:hilt-compiler:2.59")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("io.mockk:mockk-agent-jvm:1.13.12")
}
