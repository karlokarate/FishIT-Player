plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

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
                "proguard-rules.pro"
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                println("⚠️  V2 Release will be UNSIGNED (no keystore found).")
            }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
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
    implementation(project(":core:feature-api"))
    implementation(project(":core:persistence"))
    implementation(project(":core:firebase"))
    
    // v2 Playback & Player
    implementation(project(":playback:domain"))
    implementation(project(":player:internal"))
    
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
    implementation(project(":infra:tooling"))
    implementation(project(":infra:transport-telegram"))
    implementation(project(":infra:transport-xtream"))
    
    // Coil for ImageLoader singleton
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    
    // OkHttp (for shared image loading client)
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
