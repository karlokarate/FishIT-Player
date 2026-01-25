plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    kotlin("kapt") // Required for ObjectBox code generation
    id("io.objectbox") version "5.0.1"
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fishit.player.core.persistence"
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


    // Include kapt-generated ObjectBox sources in Java compilation
    sourceSets {
        getByName("debug") {
            java.srcDir("build/generated/source/kapt/debug")
        }
        getByName("release") {
            java.srcDir("build/generated/source/kapt/release")
        }
    }
}

// Force Java compilation to depend on kapt and include generated sources
tasks.matching { it.name == "compileDebugJavaWithJavac" }.configureEach {
    dependsOn("kaptDebugKotlin")
    // Disable caching for this task to ensure generated sources are always compiled
    outputs.cacheIf { false }
}

tasks.matching { it.name == "compileReleaseJavaWithJavac" }.configureEach {
    dependsOn("kaptReleaseKotlin")
    outputs.cacheIf { false }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:device-api")) // SSOT for device classification
    implementation(project(":infra:logging"))

    // Optional runtime implementation for backward compatibility
    // Consumers should inject DeviceClassProvider via Hilt for production use
    compileOnly(project(":infra:device-android"))

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // ObjectBox - use api() to expose generated cursor classes (ObxVod_, ObxSeries_, etc.)
    // to consumer modules like infra:data-xtream
    api("io.objectbox:objectbox-android:5.0.1")
    api("io.objectbox:objectbox-kotlin:5.0.1")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Hilt for DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.15")
}
