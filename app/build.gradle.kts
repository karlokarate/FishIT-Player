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

/**
 * Optional: Keystore-Pfade/Secrets aus Gradle-Properties oder ENV lesen.
 * Der Workflow schreibt diese als MYAPP_* in ~/.gradle/gradle.properties.
 */
val keystorePath: String? =
    (project.findProperty("MYAPP_UPLOAD_STORE_FILE") as String?)
        ?: System.getenv("MYAPP_UPLOAD_STORE_FILE")
val keystoreStorePassword: String? =
    (project.findProperty("MYAPP_UPLOAD_STORE_PASSWORD") as String?)
        ?: System.getenv("MYAPP_UPLOAD_STORE_PASSWORD")
val keystoreKeyAlias: String? =
    (project.findProperty("MYAPP_UPLOAD_KEY_ALIAS") as String?)
        ?: System.getenv("MYAPP_UPLOAD_KEY_ALIAS")
val keystoreKeyPassword: String? =
    (project.findProperty("MYAPP_UPLOAD_KEY_PASSWORD") as String?)
        ?: System.getenv("MYAPP_UPLOAD_KEY_PASSWORD")
val hasKeystore = !keystorePath.isNullOrBlank()

android {
    namespace = "com.chris.m3usuite"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chris.m3usuite"
        minSdk = 21
        targetSdk = 36
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

        // TV Forms v1 rollout flag
        // Precedence: -P feature.tv_forms_v1 | -P TV_FORMS_V1 | default true
        val tvFormsV1 = (project.findProperty("feature.tv_forms_v1")?.toString()?.toBooleanStrictOrNull())
            ?: (project.findProperty("TV_FORMS_V1")?.toString()?.toBooleanStrictOrNull())
            ?: true
        buildConfigField("boolean", "TV_FORMS_V1", tvFormsV1.toString())

        // Media ActionBar v1 rollout flag
        // Precedence: -P feature.media_actionbar_v1 | -P MEDIA_ACTIONBAR_V1 | default true
        val mediaActionsV1 = (project.findProperty("feature.media_actionbar_v1")?.toString()?.toBooleanStrictOrNull())
            ?: (project.findProperty("MEDIA_ACTIONBAR_V1")?.toString()?.toBooleanStrictOrNull())
            ?: true
        buildConfigField("boolean", "MEDIA_ACTIONBAR_V1", mediaActionsV1.toString())

        // Detail Scaffold v1 rollout flag
        // Precedence: -P feature.detail_scaffold_v1 | -P DETAIL_SCAFFOLD_V1 | default true
        val detailScaffoldV1 = (project.findProperty("feature.detail_scaffold_v1")?.toString()?.toBooleanStrictOrNull())
            ?: (project.findProperty("DETAIL_SCAFFOLD_V1")?.toString()?.toBooleanStrictOrNull())
            ?: true
        buildConfigField("boolean", "DETAIL_SCAFFOLD_V1", detailScaffoldV1.toString())

        // UiState v1 rollout flag
        // Precedence: -P feature.ui_state_v1 | -P UI_STATE_V1 | default true
        val uiStateV1 = (project.findProperty("feature.ui_state_v1")?.toString()?.toBooleanStrictOrNull())
            ?: (project.findProperty("UI_STATE_V1")?.toString()?.toBooleanStrictOrNull())
            ?: true
        buildConfigField("boolean", "UI_STATE_V1", uiStateV1.toString())

        // Playback launcher v1 rollout flag
        // Precedence: -P feature.playback_launcher_v1 | -P PLAYBACK_LAUNCHER_V1 | default true
        val playbackLauncherV1 = (project.findProperty("feature.playback_launcher_v1")?.toString()?.toBooleanStrictOrNull())
            ?: (project.findProperty("PLAYBACK_LAUNCHER_V1")?.toString()?.toBooleanStrictOrNull())
            ?: true
        buildConfigField("boolean", "PLAYBACK_LAUNCHER_V1", playbackLauncherV1.toString())

        // >>> Neu: Versionen aus -P übernehmen (vom Workflow gesetzt), falls vorhanden
        (project.findProperty("versionCode") as String?)?.toIntOrNull()?.let { versionCode = it }
        (project.findProperty("versionName") as String?)?.let { versionName = it }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    /**
     * >>> Neu: Signing-Config "release" aus MYAPP_* (nur wenn Keystore vorhanden)
     */
    signingConfigs {
        create("release") {
            if (hasKeystore) {
                storeFile = file(keystorePath!!)
                storePassword = keystoreStorePassword
                keyAlias = keystoreKeyAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }

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

            // >>> Neu: Nur signieren, wenn ein Keystore tatsächlich konfiguriert ist.
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Falls kein Keystore vorhanden: unsigniert bauen (APK/AAB werden trotzdem erzeugt)
                println("⚠️  Release wird UNSIGNIERT gebaut (kein Keystore in MYAPP_* gefunden).")
            }
        }
        debug {
            isMinifyEnabled = false
            // debug bleibt wie gehabt
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Compose compiler is managed by the Kotlin Compose Gradle plugin.
    // No explicit kotlinCompilerExtensionVersion needed with Compose 1.9.x + Kotlin 2.x.

    // >>> Angepasst: Split-APKs pro ABI + Universal-APK
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            // Zusätzlich zur je-ABI APK eine Universal-APK erzeugen
            isUniversalApk = true
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
    val compose = "1.9.3" // Compose UI release (Jan 2026)

    // Core + Compose
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")

    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.material3:material3:1.3.1")
    // Material (M2) icons-extended track is not published at 1.9.x; latest is 1.7.x
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:$compose")
    debugImplementation("androidx.compose.ui:ui-tooling:$compose")
    // Compose UI testing
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$compose")

    // Material (für XML-Themes)
    implementation("com.google.android.material:material:1.13.0")

    // Lifecycle Compose (für LocalLifecycleOwner etc.)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:5.2.0")
    implementation("com.squareup.okhttp3:okhttp-zstd:5.2.0")
    implementation("com.squareup.okio:okio:3.10.2")

    // JSON (Kotlin Serialization)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Room removed

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    // Paging 3
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")

    // Coil (Bilder) – Coil 3 + OkHttp Backend
    implementation("io.coil-kt.coil3:coil:3.3.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-core:3.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Firebase Cloud Messaging (optional; used for TDLib push integration)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    // JankStats (performance diagnostics)
    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")

    // QR (ZXing core for QR bitmap generation)
    implementation("com.google.zxing:core:3.5.3")

    // Media3 (ExoPlayer + UI + DataSource)
    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    // Optional:
    // implementation("androidx.media3:media3-ui-compose:$media3")

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
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    // ObjectBox (high-performance local DB)
    implementation("io.objectbox:objectbox-android:3.7.1")
    kapt("io.objectbox:objectbox-processor:3.7.1")
}
