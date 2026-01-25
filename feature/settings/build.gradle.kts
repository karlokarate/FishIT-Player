plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// Read debug tool flags from root project properties (passed from app-v2 via CI)
// Default: true for debug builds (tools available), but can be disabled via -PincludeChucker=false
val includeChucker = project.findProperty("includeChucker")?.toString()?.toBoolean() ?: true
val includeLeakCanary = project.findProperty("includeLeakCanary")?.toString()?.toBoolean() ?: true

android {
    namespace = "com.fishit.player.feature.settings"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        // API Keys from environment (same as app-v2)
        val tgApiIdEnv = System.getenv("TG_API_ID")
        val tgApiHashEnv = System.getenv("TG_API_HASH")
        val tgApiIdValue = tgApiIdEnv?.toIntOrNull() ?: 0
        val tgApiHashValue = tgApiHashEnv ?: ""
        
        buildConfigField("int", "TG_API_ID", tgApiIdValue.toString())
        buildConfigField("String", "TG_API_HASH", "\"$tgApiHashValue\"")
        
        // TMDB API key (from environment or gradle.properties)
        val tmdbApiKey =
            System.getenv("TMDB_API_KEY")
                ?: project.findProperty("TMDB_API_KEY")?.toString()
                ?: ""
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")

        // ⭐ Compile-time gating for debug tools (Issue #564)
        // These flags control whether the UI shows debug tool sections.
        // The actual tool dependencies are still controlled by debugImplementation,
        // but the UI will hide sections when tools are disabled via Gradle properties.
        buildConfigField("boolean", "INCLUDE_CHUCKER", includeChucker.toString())
        buildConfigField("boolean", "INCLUDE_LEAKCANARY", includeLeakCanary.toString())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:persistence"))
    implementation(project(":core:firebase"))
    implementation(project(":core:source-activation-api"))
    implementation(project(":core:catalog-sync"))
    implementation(project(":core:metadata-normalizer"))
    implementation(project(":core:feature-api"))  // For TelegramAuthRepository
    implementation(project(":playback:domain"))
    implementation(project(":infra:logging"))
    implementation(project(":infra:cache"))
    implementation(project(":infra:data-telegram"))  // For TelegramContentRepository
    implementation(project(":infra:data-xtream"))  // For XtreamCatalogRepository, XtreamLiveRepository
    implementation(project(":infra:transport-xtream"))  // For XtreamCredentialsStore

    // Debug settings (for runtime toggles in DebugToolsControllerImpl - debug only)
    // This module is always included in debug builds for the runtime toggle infrastructure.
    // The actual tool libraries (Chucker, LeakCanary) are conditionally included below.
    debugImplementation(project(":core:debug-settings"))

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Activity Result / document export (SAF)
    implementation("androidx.activity:activity-compose:1.9.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // WorkManager (for debug diagnostics)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ========== COMPILE-TIME GATING: LeakCanary & Chucker (Issue #564) ==========
    // These dependencies are ONLY included when explicitly enabled via Gradle properties.
    // When disabled (-PincludeChucker=false -PincludeLeakCanary=false):
    // - Libraries are NOT in the APK at all
    // - No auto-init ContentProviders run
    // - Zero runtime overhead
    // - UI will not show debug tool sections (isAvailable=false via BuildConfig)

    // LeakCanary (debug-only for LeakDiagnosticsImpl)
    if (includeLeakCanary) {
        debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    }

    // Chucker HTTP Inspector (debug-only for ChuckerDiagnosticsImpl)
    if (includeChucker) {
        debugImplementation(libs.chucker)
    }
}
