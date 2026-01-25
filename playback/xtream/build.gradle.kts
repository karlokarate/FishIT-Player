plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// ⭐ COMPILE-TIME GATING (Issue #564): Read Gradle properties for conditional dependencies
val includeChucker = project.findProperty("includeChucker")?.toString()?.toBoolean() ?: true

android {
    namespace = "com.fishit.player.playback.xtream"
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
    implementation(project(":core:player-model"))
    implementation(project(":infra:logging"))
    implementation(project(":infra:transport-xtream"))
    implementation(project(":playback:domain"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Media3/ExoPlayer for DataSource
    val media3Version = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-datasource:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")

    // OkHttp for reliable redirect handling
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ========== COMPILE-TIME GATING: Chucker (Issue #564) ==========
    // Chucker is ONLY included when -PincludeChucker=true (default).
    // When false, the library is NOT in the APK at all - no auto-init, no overhead.
    // See: src/debug/ and src/release/ source sets for implementations.
    if (includeChucker) {
        debugImplementation(libs.chucker)
    }
    // NOTE: Release build uses source-set stubs instead of library (Issue #564)

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.59")
    ksp("com.google.dagger:hilt-compiler:2.59")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}
