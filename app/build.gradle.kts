import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.objectbox") version "3.7.1"
    // id("com.google.gms.google-services") // enable if google-services.json is configured
}

android {
    namespace = "com.chris.m3usuite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chris.m3usuite"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Telegram API credentials (secure lookup, non-committed):
        // Precedence: ENV → .tg.secrets.properties (root, untracked) → project -P props → default
        val secretsFile = File(rootDir, ".tg.secrets.properties")
        val secrets = Properties().apply {
            if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
        }
        fun prop(name: String): String? =
            System.getenv(name)
                ?: (secrets.getProperty(name))
                ?: (project.findProperty(name)?.toString())

        val tgApiIdValue = prop("TG_API_ID")?.toIntOrNull() ?: 0
        val tgApiHashValue = prop("TG_API_HASH") ?: ""

        buildConfigField("int", "TG_API_ID", tgApiIdValue.toString())
        buildConfigField("String", "TG_API_HASH", "\"${tgApiHashValue}\"")

        // Default HTTP User-Agent (secret-injected, non-committed):
        // Precedence: ENV HEADER → /.ua.secrets.properties HEADER → -P HEADER → default empty
        val uaSecretsFile = File(rootDir, ".ua.secrets.properties")
        val uaSecrets = Properties().apply { if (uaSecretsFile.exists()) uaSecretsFile.inputStream().use { load(it) } }
        fun uaProp(name: String): String? =
            System.getenv(name)
                ?: (uaSecrets.getProperty(name))
                ?: (project.findProperty(name)?.toString())
        val defaultUa = uaProp("HEADER").orEmpty()
        buildConfigField("String", "DEFAULT_UA", "\"${defaultUa}\"")

        // Feature switches
        // Toggle visibility of header (User-Agent) editing UI
        val showHeaderUi = (project.findProperty("SHOW_HEADER_UI")?.toString()?.toBooleanStrictOrNull()) ?: false
        buildConfigField("boolean", "SHOW_HEADER_UI", showHeaderUi.toString())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Enable resource shrinking to reduce APK/AAB size
            isShrinkResources = true
            // Explicitly ensure release is not debuggable
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug { isMinifyEnabled = false }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    // Generate split APKs per ABI (32-bit and 64-bit)
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            // Only per-ABI APKs (no universal)
            isUniversalApk = false
        }
    }

    packaging {
        resources.excludes += setOf(
            // Common license/notice duplicates from transitive AARs
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/LICENSE.md",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/NOTICE.md",
            // Kotlin module metadata not needed at runtime
            "META-INF/*.kotlin_module",
            // Build tool index list sometimes duplicated
            "META-INF/INDEX.LIST"
        )
        // Exclude heavy reference artifacts from packaging (kept in repo for reference only)
        resources.excludes += setOf("**/com/chris/m3usuite/reference/**")
        jniLibs {
            useLegacyPackaging = false
            // Strip non-target ABIs if pulled in by transitive deps
            excludes += setOf("**/x86/**", "**/x86_64/**", "**/mips/**", "**/mips64/**", "**/armeabi/**")
        }
    }

    // Keep reference APK dump in repo but exclude it from compilation so it doesn't interfere
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Exclude reference sources from Kotlin/Java compilation tasks to avoid receiver ambiguities in sourceSets DSL
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude("**/com/chris/m3usuite/reference/**")
    // Ensure Compose Live Literals are compiled for debug variants so Studio's Live Edit can update literals at runtime
    if (name.contains("Debug", ignoreCase = true)) {
        compilerOptions.freeCompilerArgs.addAll(
            listOf(
                "-P", "plugin:androidx.compose.compiler.plugins.kotlin:liveLiteralsEnabled=true"
            )
        )
    }
}
tasks.withType<JavaCompile>().configureEach {
    exclude("**/com/chris/m3usuite/reference/**")
}

dependencies {
    val compose = "1.7.6" // aktuellstes Compose (Feb 2025)

    // Core + Compose
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:$compose")
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

    // Room removed

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Paging 3
    implementation("androidx.paging:paging-runtime-ktx:3.3.2")
    implementation("androidx.paging:paging-compose:3.3.2")

    // Coil (Bilder)
    // Coil 3
    implementation("io.coil-kt.coil3:coil:3.0.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")

    // 🔧 WICHTIG: Netzwerk-Backend für HTTP(S)-Bilder
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0")
    implementation("io.coil-kt.coil3:coil-network-core:3.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Firebase Cloud Messaging (optional; used for TDLib push integration)
    implementation("com.google.firebase:firebase-messaging:24.0.0")

    // JankStats (performance diagnostics)
    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")

    // QR (ZXing core for QR bitmap generation)
    implementation("com.google.zxing:core:3.5.2")

    // Media3 (ExoPlayer + UI + DataSource)
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.0")

    // Compose for TV (Material)
    implementation("androidx.tv:tv-material:1.0.1")

    implementation(project(":libtd"))
    // Optional: if a prebuilt TDLib Java AAR is placed at app/libs/tdlib.aar, include it for runtime classes
    val tdlibAar = File(projectDir, "libs/tdlib.aar")
    if (tdlibAar.exists()) {
        implementation(files(tdlibAar))
    }

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // ObjectBox (high-performance local DB)
    implementation("io.objectbox:objectbox-android:3.7.1")
    kapt("io.objectbox:objectbox-processor:3.7.1")
}
