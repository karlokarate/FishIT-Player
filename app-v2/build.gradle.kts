plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fishit.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fishit.player"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.play.services.cast.framework)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ⭐ NEW: Compile-time gating for debug tools (Phase 1 + Workflow override support)
    // These flags control whether LeakCanary and Chucker are included in the build
    // - debug: Configurable via Gradle properties (default: true)
    // - release: Both tools completely removed (no stubs, no imports, no UI)
    // 
    // Can be overridden via command line or CI workflow:
    //   ./gradlew assembleDebug -PINCLUDE_LEAKCANARY=false -PINCLUDE_CHUCKER=true
    val includeLeakCanary = project.findProperty("INCLUDE_LEAKCANARY")?.toString()?.toBoolean() ?: true
    val includeChucker = project.findProperty("INCLUDE_CHUCKER")?.toString()?.toBoolean() ?: true
    
    buildConfigField("boolean", "INCLUDE_LEAKCANARY", "$includeLeakCanary")
    buildConfigField("boolean", "INCLUDE_CHUCKER", "$includeChucker")
}