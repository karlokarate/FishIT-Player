plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fishit.player.v2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fishit.player.v2"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "2.0.0-alpha01"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    implementation(project(":feature:telegram-media"))
    implementation(project(":feature:audiobooks"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:devtools"))

    // v2 Infrastructure
    implementation(project(":infra:logging"))
    implementation(project(":infra:tooling"))

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
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
