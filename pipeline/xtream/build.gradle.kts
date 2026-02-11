plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fishit.player.pipeline.xtream"
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

    // Configure test working directory to root project for test-data access
    testOptions {
        unitTests.all {
            it.workingDir = rootProject.projectDir
            // Forward golden.update system property to test JVM
            it.systemProperty("golden.update", System.getProperty("golden.update") ?: "false")
        }
    }
}

dependencies {
    // Core dependencies
    implementation(project(":core:model"))
    implementation(project(":infra:logging"))

    // Transport layer (provides XtreamApiClient)
    api(project(":infra:transport-xtream"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-compiler:2.56.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    testImplementation("io.mockk:mockk:1.13.12")

    // Real-data chain tests: full Transport → Pipeline → Raw → Normalized → NX chain
    testImplementation(project(":core:metadata-normalizer"))
    testImplementation(project(":infra:data-nx"))
    testImplementation(project(":core:persistence"))
}
