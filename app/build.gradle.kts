import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.objectbox") version "5.0.1"
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

// >>> ABI/Splits per -P (CI: arm64-v8a,armeabi-v7a; lokal optional Universal-APK)
val abiFiltersProp = (project.findProperty("abiFilters") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    .orEmpty()

val universalApkProp = (project.findProperty("universalApk") as String?)
    ?.toBooleanStrictOrNull() ?: false   // CI: false, lokal ggf. true

// WICHTIG: useSplits oben definieren (Scope!), damit unten in splits sichtbar
val useSplits = (project.findProperty("useSplits") as String?)
    ?.toBooleanStrictOrNull() ?: true    // CI: true → nur Splits verwenden

android {
    namespace = "com.chris.m3usuite"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chris.m3usuite"
        minSdk = 23
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
        val showHeaderUi = (project.findProperty("SHOW_HEADER_UI")?.toString()?.toBooleanStrictOrNull()) ?: false
        buildConfigField("boolean", "SHOW_HEADER_UI", showHeaderUi.toString())

        val tvFormsV1 = (project.findProperty("feature.tv_forms_v1")?.toString()?.toBooleanStrictOrNull())
            ?: (project.findProperty("TV_FORMS_V1")?.toString()?.toBooleanStrictOrNull())
            ?: true
        buildConfigField("boolean", "TV_FORMS_V1", tvFormsV1.toString())

        val mediaActionsV1 = (project.findProperty("feature.media_actionbar_v1")?.toString()?.toBooleanStrictOrNull())
            ?: (project.findProperty("MEDIA_ACTIONBAR_V1")?.toString()?.toBooleanStrictOrNull())
            ?: true
        buildConfigField("boolean", "MEDIA_ACTIONBAR_V1", mediaActionsV1.toString())

        val detailScaffoldV1 = (project.findProperty("feature.detail_scaffold_v1")?.toString()?.toBooleanStrictOrNull())
            ?: (project.findProperty("DETAIL_SCAFFOLD_V1")?.toString()?.toBooleanStrictOrNull())
            ?: true
        buildConfigField("boolean", "DETAIL_SCAFFOLD_V1", detailScaffoldV1.toString())

        val uiStateV1 = (project.findProperty("feature.ui_state_v1")?.toString()?.toBooleanStrictOrNull())
            ?: (project.findProperty("UI_STATE_V1")?.toString()?.toBooleanStrictOrNull())
            ?: true
        buildConfigField("boolean", "UI_STATE_V1", uiStateV1.toString())

        val playbackLauncherV1 = (project.findProperty("feature.playback_launcher_v1")?.toString()?.toBooleanStrictOrNull())
            ?: (project.findProperty("PLAYBACK_LAUNCHER_V1")?.toString()?.toBooleanStrictOrNull())
            ?: true
        buildConfigField("boolean", "PLAYBACK_LAUNCHER_V1", playbackLauncherV1.toString())

        // >>> Versionsübergabe vom Workflow (optional)
        (project.findProperty("versionCode") as String?)?.toIntOrNull()?.let { versionCode = it }
        (project.findProperty("versionName") as String?)?.let { versionName = it }

        // >>> ndk.abiFilters NUR wenn Splits deaktiviert sind (lokale Einzelfall-Builds)
        if (!useSplits && abiFiltersProp.isNotEmpty()) {
            ndk {
                abiFiltersProp.forEach { abiFilters += it }
            }
        }
    } // <<<<<< fehlte vorher

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
     * Signing-Config "release" aus MYAPP_* (nur wenn Keystore vorhanden)
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
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                println("⚠️  Release wird UNSIGNIERT gebaut (kein Keystore in MYAPP_* gefunden).")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Compose compiler is managed by the Kotlin Compose Gradle plugin.

    // >>> Split-APKs pro ABI (per Property steuerbar); standardmäßig zwei Splits, keine Universal-APK
    splits {
        abi {
            isEnable = useSplits
            reset()
            include(*(if (abiFiltersProp.isNotEmpty())
                abiFiltersProp.toTypedArray()
            else
                arrayOf("arm64-v8a","armeabi-v7a")))
            isUniversalApk = universalApkProp
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/LICENSE.md",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/NOTICE.md",
            "META-INF/*.kotlin_module",
            "META-INF/INDEX.LIST"
        )
        // Exclude heavy reference artifacts from packaging (kept in repo for reference only)
        resources.excludes += setOf("**/com/chris/m3usuite/reference/**")
        jniLibs {
            useLegacyPackaging = false
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
    // Live Literals für Debug
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
    // Compose UI (Release 08.10.2025)
    val compose = "1.9.3"
    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:$compose")
    debugImplementation("androidx.compose.ui:ui-tooling:$compose")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$compose")

    // AndroidX Core/Activity/Lifecycle/Navigation
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Material (Views)
    implementation("com.google.android.material:material:1.13.0")

    // Media3 (ExoPlayer + UI + HLS)
    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    // Optional:
    // implementation("androidx.media3:media3-ui-compose:$media3")

    // Data / Work / Paging
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")

    // Coil 3 (+ OkHttp Backend)
    implementation("io.coil-kt.coil3:coil:3.3.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-core:3.3.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:5.2.1")
    implementation("com.squareup.okio:okio:3.16.1")

    // JSON (Kotlin Serialization)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Firebase Cloud Messaging (optional; used for TDLib push integration)
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-messaging")

    // JankStats (performance diagnostics)
    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")

    // QR (ZXing core for QR bitmap generation)
    implementation("com.google.zxing:core:3.5.3")

    // Compose for TV (Material)
    implementation("androidx.tv:tv-material:1.0.1")

    // TDLib JNI & Java bindings (Projektmodul)
    implementation(project(":libtd"))
    // Optional: falls ein AAR unter app/libs/tdlib.aar liegt
    val tdlibAar = File(projectDir, "libs/tdlib.aar")
    if (tdlibAar.exists()) {
        implementation(files(tdlibAar))
    }

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    // ObjectBox (5.x)
    implementation("io.objectbox:objectbox-android:5.0.1")
    kapt("io.objectbox:objectbox-processor:5.0.1")
}