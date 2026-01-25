plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// ⭐ COMPILE-TIME GATING (Issue #564): Read Gradle properties for conditional dependencies
// This module is only included via debugImplementation when tools are enabled.
// The dependencies below are conditional to avoid pulling in unused libraries.
val includeChucker = project.findProperty("includeChucker")?.toString()?.toBoolean() ?: true
val includeLeakCanary = project.findProperty("includeLeakCanary")?.toString()?.toBoolean() ?: true

android {
    namespace = "com.fishit.player.core.debugsettings"
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
    // DataStore for persisted settings
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")

    // OkHttp for interceptor interface
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ========== COMPILE-TIME GATING: Chucker & LeakCanary (Issue #564) ==========
    // These dependencies are ONLY included when enabled via Gradle properties.
    // When disabled, the library is NOT in the APK at all.

    // Chucker HTTP Inspector (for GatedChuckerInterceptor)
    if (includeChucker) {
        implementation(libs.chucker)
    }

    // LeakCanary (for LeakCanary runtime control)
    if (includeLeakCanary) {
        implementation("com.squareup.leakcanary:leakcanary-android:2.14")
    }

    // Logging
    implementation(project(":infra:logging"))

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
}
