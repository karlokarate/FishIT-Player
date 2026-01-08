plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// ⭐ COMPILE-TIME GATING (Issue #564): Read Gradle properties for conditional dependencies
val includeChucker = project.findProperty("includeChucker")?.toString()?.toBoolean() ?: true

android {
    namespace = "com.fishit.player.infra.transport.xtream"
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
    implementation(project(":core:device-api"))
    implementation(project(":infra:device-android"))  // For backward compat in deprecated method
    implementation(project(":infra:logging"))

    // Debug settings (for GatedChuckerInterceptor in debug builds)
    // This module is always included in debug builds for the gating infrastructure.
    // The actual Chucker library is conditionally included below.
    debugImplementation(project(":core:debug-settings"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Networking
    api("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Security for credential storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")

    // ========== COMPILE-TIME GATING: Chucker (Issue #564) ==========
    // Chucker is ONLY included when -PincludeChucker=true (default).
    // When false, the library is NOT in the APK at all - no auto-init, no overhead.
    // See: src/debug/ and src/release/ source sets for DebugInterceptorModule.
    if (includeChucker) {
        debugImplementation(libs.chucker)
    }
    // NOTE: No releaseImplementation(libs.chucker.noop) - completely removed per Issue #564

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.0.0-alpha.14")
}
