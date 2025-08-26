plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.chris.m3usuite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chris.m3usuite"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { isMinifyEnabled = false }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val compose = "1.7.6" // aktuellstes Compose (Feb 2025)

    // Core + Compose
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.material:material:$compose")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.ui:ui-tooling-preview:$compose")
    debugImplementation("androidx.compose.ui:ui-tooling:$compose")

    // Material (für XML-Themes)
    implementation("com.google.android.material:material:1.12.0")

    // Lifecycle Compose (für LocalLifecycleOwner etc.)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.10.2")

    // JSON (Kotlin Serialization)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.0")
    kapt("androidx.room:room-compiler:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Coil (Bilder)
    // Coil 3
    implementation("io.coil-kt.coil3:coil:3.0.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")

    // 🔧 WICHTIG: Netzwerk-Backend für HTTP(S)-Bilder
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0")
    implementation("io.coil-kt.coil3:coil-network-core:3.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Media3 (ExoPlayer + UI + DataSource)
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.0")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
