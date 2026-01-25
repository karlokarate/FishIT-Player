plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

/**
 * Issue #564: Compile-time gating for debug tools.
 * When false, LeakCanary is NOT included in the APK at all.
 * Default: true (for development builds)
 */
val includeLeakCanary: Boolean = (project.findProperty("includeLeakCanary") as? String)?.toBoolean() ?: true

/**
 * Keystore configuration for release signing.
 * Reads from Gradle properties or environment variables (set by CI workflow).
 */
val keystorePath: String? =
    (project.findProperty("MYAPP_UPLOAD_STORE_FILE") as String?)
        ?: System.getenv("MYAPP_UPLOAD_STORE_FILE")
val keystoreStorePassword: String? =
    (project.findProperty("MYAPP_UPLOAD_STORE_PASSWORD") as String?)
        ?: System.getenv("MYAPP_UPLOAD_STORE_PASSWORD")
val keystoreKeyAlias: String? =
    (project.findProperty("MYAPP_UPLOAD_KEY_ALIAS") as String?)
        ?: System.getenv("MYAPP_UPLOAD_KEY_ALIAS")
val keystoreKeyPassword: String? =
    (project.findProperty("MYAPP_UPLOAD_KEY_PASSWORD") as String?)
        ?: System.getenv("MYAPP_UPLOAD_KEY_PASSWORD")
val hasKeystore = !keystorePath.isNullOrBlank()

android {
    namespace = "com.fishit.player.v2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fishit.player.v2"
        minSdk = 24
        targetSdk = 35

        // Version from Gradle properties (CI can override)
        val versionCodeProp = project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: 1
        val versionNameProp = project.findProperty("versionName")?.toString() ?: "2.0.0-alpha01"
        versionCode = versionCodeProp
        versionName = versionNameProp

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Telegram API credentials (from environment or local.properties)
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
        // These flags control whether LeakCanary and Chucker are included in the build.
        // - debug default: Both tools enabled for memory leak detection and network inspection
        // - release: Both tools completely removed (no stubs, no imports, no UI)
        // Override via Gradle properties: -PincludeChucker=true -PincludeLeakCanary=true
        val includeChucker = project.findProperty("includeChucker")?.toString()?.toBoolean() ?: true
        val includeLeakCanary = project.findProperty("includeLeakCanary")?.toString()?.toBoolean() ?: true
        buildConfigField("boolean", "INCLUDE_LEAKCANARY", includeLeakCanary.toString())
        buildConfigField("boolean", "INCLUDE_CHUCKER", includeChucker.toString())

        // ABI configuration is handled via splits when useSplits=true
        // Otherwise, use NDK abiFilters for single-ABI builds
    }

    // ABI Splits for smaller APKs (enabled via -PuseSplits=true)
    val useSplits = project.findProperty("useSplits")?.toString()?.toBoolean() ?: false
    val abiFilters = project.findProperty("abiFilters")?.toString()

    if (useSplits) {
        splits {
            abi {
                isEnable = true
                reset()
                // If abiFilters is specified, use it; otherwise use all ABIs
                if (abiFilters != null) {
                    abiFilters.split(",").forEach { abi ->
                        include(abi.trim())
                    }
                } else {
                    include("arm64-v8a", "armeabi-v7a")
                }
                isUniversalApk = project.findProperty("universalApk")?.toString()?.toBoolean() ?: false
            }
        }
    } else if (abiFilters != null) {
        // Only use NDK abiFilters when splits are NOT enabled
        defaultConfig.ndk {
            abiFilters.split(",").forEach { abi ->
                this.abiFilters.add(abi.trim())
            }
        }
    }

    signingConfigs {
        create("release") {
            if (hasKeystore) {
                storeFile = file(keystorePath!!)
                storePassword = keystoreStorePassword
                keyAlias = keystoreKeyAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                println("⚠️  V2 Release will be UNSIGNED (no keystore found).")
            }

            // Override: Debug tools MUST be disabled in release builds
            buildConfigField("boolean", "INCLUDE_LEAKCANARY", "false")
            buildConfigField("boolean", "INCLUDE_CHUCKER", "false")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false

            // Explicit: Debug tools enabled
            buildConfigField("boolean", "INCLUDE_LEAKCANARY", "true")
            buildConfigField("boolean", "INCLUDE_CHUCKER", "true")
        }
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
    // v2 Core Modules
    implementation(project(":core:model"))
    implementation(project(":core:player-model"))
    implementation(project(":core:feature-api"))
    implementation(project(":core:source-activation-api"))
    implementation(project(":core:persistence"))
    implementation(project(":core:catalog-sync"))
    implementation(project(":core:firebase"))
    implementation(project(":core:app-startup"))

    // Debug settings (debug builds only)
    debugImplementation(project(":core:debug-settings"))

    // v2 Domain Modules (extracted from features)
    implementation(project(":core:home-domain"))
    implementation(project(":core:library-domain"))
    implementation(project(":core:live-domain"))
    implementation(project(":core:detail-domain"))
    implementation(project(":core:telegrammedia-domain"))
    implementation(project(":core:onboarding-domain"))

    // v2 Metadata Normalizer (TMDB enrichment)
    implementation(project(":core:metadata-normalizer"))

    // v2 Playback & Player
    implementation(project(":playback:domain"))
    implementation(project(":playback:xtream"))
    implementation(project(":playback:telegram"))
    implementation(project(":player:ui"))
    implementation(project(":player:ui-api"))
    implementation(project(":player:internal"))
    implementation(project(":player:miniplayer"))
    implementation(project(":player:nextlib-codecs"))

    // v2 Pipelines
    implementation(project(":pipeline:telegram"))
    implementation(project(":pipeline:xtream"))
    implementation(project(":pipeline:io"))
    implementation(project(":pipeline:audiobook"))

    // v2 Features
    implementation(project(":feature:home"))
    implementation(project(":feature:library"))
    implementation(project(":feature:live"))
    implementation(project(":feature:detail"))
    implementation(project(":feature:telegram-media"))
    implementation(project(":feature:audiobooks"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:onboarding"))

    // v2 UI Core
    implementation(project(":core:ui-theme"))
    implementation(project(":core:ui-layout"))
    implementation(project(":core:ui-imaging"))

    // v2 Infrastructure
    implementation(project(":infra:logging"))
    implementation(project(":infra:imaging"))
    implementation(project(":infra:cache"))
    implementation(project(":infra:tooling"))
    implementation(project(":infra:transport-telegram"))
    implementation(project(":infra:transport-xtream"))
    implementation(project(":infra:data-telegram"))
    implementation(project(":infra:data-xtream"))
    implementation(project(":infra:data-detail"))
    implementation(project(":infra:data-home"))
    implementation(project(":infra:data-nx"))
    implementation(project(":infra:work"))

    // Coil for ImageLoader singleton
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    // OkHttp (for shared image loading client)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Core AndroidX
    implementation("androidx.core:core-ktx:1.15.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // LeakCanary (debug-only memory leak detection)
    // ⭐ COMPILE-TIME GATING (Issue #564):
    // Only included when -PincludeLeakCanary=true (default for debug)
    // When false, the library is NOT in the APK at all - no auto-init, no overhead
    if (includeLeakCanary) {
        debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    }
}

/**
 * Guardrail task: Verify WorkManagerInitializer is not in merged manifests.
 *
 * This project uses on-demand WorkManager initialization via Configuration.Provider.
 * The auto-initialization must remain disabled to prevent runtime conflicts.
 *
 * Run after assembling to validate the manifest merge rules are correct.
 * CI should run this as a post-build verification step.
 */
tasks.register<Exec>("checkNoWorkManagerInitializer") {
    group = "verification"
    description = "Verify WorkManagerInitializer is not in merged manifests (on-demand init only)"

    commandLine("${rootProject.projectDir}/scripts/check_no_workmanager_initializer.sh")

    // Only run if build has produced at least one merged manifest (any variant)
    val mergedManifestsDir = file("${layout.buildDirectory.get()}/intermediates/merged_manifests")
    onlyIf {
        mergedManifestsDir.exists() &&
            fileTree(mergedManifestsDir) {
                include("**/AndroidManifest.xml")
            }.files.isNotEmpty()
    }

    // Make this task run after manifest processing
    tasks.findByName("processDebugManifest")?.let { processDebug ->
        mustRunAfter(processDebug)
    }
    tasks.findByName("processReleaseManifest")?.let { processRelease ->
        mustRunAfter(processRelease)
    }
}

// Hook into assemble tasks for automatic validation
tasks.matching { it.name.startsWith("assemble") }.configureEach {
    finalizedBy("checkNoWorkManagerInitializer")
}
