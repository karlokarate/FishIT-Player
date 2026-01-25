plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fishit.player.core.catalogsync"
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
    implementation(project(":core:source-activation-api"))
    implementation(project(":core:feature-api")) // For TelegramAuthRepository (userId validation)
    implementation(project(":core:metadata-normalizer"))
    implementation(project(":core:persistence")) // For CanonicalMediaRepository
    implementation(project(":infra:logging"))

    // Data layer repositories (for persisting catalog data)
    implementation(project(":infra:data-telegram"))
    implementation(project(":infra:data-xtream"))
    implementation(project(":infra:data-nx")) // NX work graph (v2 SSOT)

    // Pipeline events (for consuming catalog events)
    implementation(project(":pipeline:telegram"))
    implementation(project(":pipeline:xtream"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // DataStore for checkpoint persistence
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-compiler:2.56.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.13.12")
}
