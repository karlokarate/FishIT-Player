plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fishit.player.infra.transport.tmdb"
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
    // Core dependencies
    implementation(project(":core:model"))
    implementation(project(":infra:logging"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // TMDB Java API
    implementation("com.uwetrottmann.tmdb2:tmdb-java:2.11.0")

    // Networking (aligned with project OkHttp version)
    api("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.0.0-alpha.14")
}
