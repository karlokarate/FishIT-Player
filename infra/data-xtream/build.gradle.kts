plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fishit.player.infra.data.xtream"
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

// Ensure ObjectBox cursor classes are generated before this module compiles
afterEvaluate {
    tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
        dependsOn(":core:persistence:kaptDebugKotlin", ":core:persistence:kaptReleaseKotlin")
    }
}

dependencies {
    // Core dependencies
    implementation(project(":core:model"))
    implementation(project(":core:source-activation-api"))
    implementation(project(":core:onboarding-domain"))
    implementation(project(":core:library-domain"))
    implementation(project(":core:live-domain"))
    implementation(project(":core:detail-domain"))
    // NOTE: NO dependency on core:catalog-sync - cycle permanently broken.
    // Use api() for persistence to expose generated ObjectBox cursor classes (ObxVod_, ObxSeries_, etc.)
    api(project(":core:persistence"))
    implementation(project(":infra:logging"))
    implementation(project(":infra:transport-xtream"))
    // NOTE: No pipeline:xtream dependency - Data layer works only with RawMediaMetadata

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Serialization (for playback hints JSON in ObxXtreamSeriesIndexRepository)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.12")
}
