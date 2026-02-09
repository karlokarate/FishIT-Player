plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fishit.player.infra.data.nx"
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
    api(project(":core:model"))
    implementation(project(":core:persistence"))
    implementation(project(":core:detail-domain"))
    implementation(project(":core:home-domain"))
    implementation(project(":core:library-domain"))
    implementation(project(":core:live-domain"))
    implementation(project(":core:telegrammedia-domain"))
    implementation(project(":infra:logging"))

    // Data layer dependencies (for repository interfaces)
    implementation(project(":infra:data-xtream"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    
    // Paging 3 - for PagingData generation
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-compiler:2.56.2")

    // ObjectBox - version must match core:persistence (5.0.1)
    // Note: core:persistence provides transitive api() dependency, but explicit for flow() extensions
    implementation("io.objectbox:objectbox-kotlin:5.0.1")

    // Paging 3 - For PagingSource implementation
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")

    // Serialization for JSON storage of playbackHints
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // DataStore for gate state persistence (XOC-2: categorySelectionComplete)
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.13.12")
    // P1: Full-chain tests need the normalizer (RawMediaMetadata → NormalizedMediaMetadata)
    testImplementation(project(":core:metadata-normalizer"))
    // P2: Property-based testing (Kotest generators + forAll/checkAll)
    testImplementation("io.kotest:kotest-property:5.9.1")
}
