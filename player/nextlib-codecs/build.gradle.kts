plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fishit.player.nextlib"
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
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")

    // Media3 / ExoPlayer - same version as player:internal
    val media3Version = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")

    // NextLib FFmpeg extension for Media3
    // Provides FFmpeg-based decoders for: Vorbis, Opus, FLAC, ALAC, MP3, AAC,
    // AC3, EAC3, DTS, TrueHD, H.264, HEVC, VP8, VP9
    // License: GPL-3.0
    // NOTE: NextLib version must match Media3 major.minor version (1.8.x)
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.8.0-0.9.0")

    // Logging
    implementation(project(":infra:logging"))

    // Testing
    testImplementation("junit:junit:4.13.2")
}
