plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fishit.player.core.debugsettings"
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
    // DataStore for persisted settings
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
    
    // OkHttp for interceptor interface
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    
    // Chucker HTTP Inspector (for GatedChuckerInterceptor)
    implementation(libs.chucker)
    
    // LeakCanary (for LeakCanary runtime control)
    implementation("com.squareup.leakcanary:leakcanary-android:2.14")
    
    // Logging
    implementation(project(":infra:logging"))
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")
}
