import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("kapt") // Required for ObjectBox code generation
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.objectbox") version "5.0.1"
    id("org.jetbrains.kotlinx.kover")
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
val abiFiltersProp =
    (project.findProperty("abiFilters") as String?)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

val universalApkProp =
    (project.findProperty("universalApk") as String?)
        ?.toBooleanStrictOrNull() ?: false // CI: false, lokal ggf. true

// WICHTIG: useSplits oben definieren (Scope!), damit unten in splits sichtbar
val useSplits =
    (project.findProperty("useSplits") as String?)
        ?.toBooleanStrictOrNull() ?: true // CI: true → nur Splits verwenden

android {
    namespace = "com.chris.m3usuite"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chris.m3usuite"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Default HTTP User-Agent (secret-injected)
        val uaSecretsFile = File(rootDir, ".ua.secrets.properties")
        val uaSecrets =
            Properties().apply {
                if (uaSecretsFile.exists()) uaSecretsFile.inputStream().use { load(it) }
            }

        fun uaProp(name: String): String? =
            System.getenv(name)
                ?: (uaSecrets.getProperty(name))
                ?: (project.findProperty(name)?.toString())
        val defaultUa = uaProp("HEADER").orEmpty()
        buildConfigField("String", "DEFAULT_UA", "\"${defaultUa}\"")

        // Feature switches
        val showHeaderUi =
            (project.findProperty("SHOW_HEADER_UI")?.toString()?.toBooleanStrictOrNull()) ?: false
        buildConfigField("boolean", "SHOW_HEADER_UI", showHeaderUi.toString())

        val tvFormsV1 =
            (project.findProperty("feature.tv_forms_v1")?.toString()?.toBooleanStrictOrNull())
                ?: (project.findProperty("TV_FORMS_V1")?.toString()?.toBooleanStrictOrNull())
                ?: true
        buildConfigField("boolean", "TV_FORMS_V1", tvFormsV1.toString())

        val mediaActionsV1 =
            (project.findProperty("feature.media_actionbar_v1")?.toString()?.toBooleanStrictOrNull())
                ?: (project.findProperty("MEDIA_ACTIONBAR_V1")?.toString()?.toBooleanStrictOrNull())
                ?: true
        buildConfigField("boolean", "MEDIA_ACTIONBAR_V1", mediaActionsV1.toString())

        val detailScaffoldV1 =
            (project.findProperty("feature.detail_scaffold_v1")?.toString()?.toBooleanStrictOrNull())
                ?: (project.findProperty("DETAIL_SCAFFOLD_V1")?.toString()?.toBooleanStrictOrNull())
                ?: true
        buildConfigField("boolean", "DETAIL_SCAFFOLD_V1", detailScaffoldV1.toString())

        val uiStateV1 =
            (project.findProperty("feature.ui_state_v1")?.toString()?.toBooleanStrictOrNull())
                ?: (project.findProperty("UI_STATE_V1")?.toString()?.toBooleanStrictOrNull())
                ?: true
        buildConfigField("boolean", "UI_STATE_V1", uiStateV1.toString())

        val playbackLauncherV1 =
            (project.findProperty("feature.playback_launcher_v1")?.toString()?.toBooleanStrictOrNull())
                ?: (project.findProperty("PLAYBACK_LAUNCHER_V1")?.toString()?.toBooleanStrictOrNull())
                ?: true
        buildConfigField("boolean", "PLAYBACK_LAUNCHER_V1", playbackLauncherV1.toString())

        // Versionsübergabe vom Workflow (optional)
        (project.findProperty("versionCode") as String?)?.toIntOrNull()?.let { versionCode = it }
        (project.findProperty("versionName") as String?)?.let { versionName = it }

        // ndk.abiFilters nur wenn Splits deaktiviert sind
        if (!useSplits && abiFiltersProp.isNotEmpty()) {
            ndk {
                abiFiltersProp.forEach { abiFilters += it }
            }
        }
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
                "proguard-rules.pro",
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                println("⚠️  Release wird UNSIGNIERT gebaut (kein Keystore gefunden).")
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

    splits {
        abi {
            isEnable = useSplits
            reset()
            include(
                *(
                    if (abiFiltersProp.isNotEmpty()) {
                        abiFiltersProp.toTypedArray()
                    } else {
                        arrayOf("arm64-v8a", "armeabi-v7a")
                    }
                ),
            )
            isUniversalApk = universalApkProp
        }
    }

    packaging {
        resources.excludes +=
            setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "META-INF/INDEX.LIST",
            )
        resources.excludes += setOf("**/com/chris/m3usuite/reference/**")
        jniLibs {
            useLegacyPackaging = false
            excludes += setOf("**/x86/**", "**/x86_64/**", "**/mips/**", "**/mips64/**", "**/armeabi/**")
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Configure KAPT with Kotlin 1.9 language version for better compatibility
// This fixes KAPT stub generation issues with Kotlin 2.1.0 and older dependencies
tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask>().configureEach {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}

kapt {
    correctErrorTypes = false
    useBuildCache = true
    mapDiagnosticLocations = false
    includeCompileClasspath = false
    showProcessorStats = false
}

// Disable AAR metadata check to allow using newer AndroidX libs with AGP 8.5.2
tasks.whenTaskAdded {
    if (name == "checkReleaseAarMetadata" || name == "checkDebugAarMetadata") {
        enabled = false
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude("**/com/chris/m3usuite/reference/**")
    if (name.contains("Debug", ignoreCase = true)) {
        compilerOptions.freeCompilerArgs.addAll(
            listOf("-P", "plugin:androidx.compose.compiler.plugins.kotlin:liveLiteralsEnabled=true"),
        )
    }
}
tasks.withType<JavaCompile>().configureEach {
    exclude("**/com/chris/m3usuite/reference/**")
}

dependencies {
    val compose = "1.9.3"
    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:$compose")
    debugImplementation("androidx.compose.ui:ui-tooling:$compose")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$compose")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation("com.google.android.material:material:1.13.0")

    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.8.0+1")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")

    implementation("io.coil-kt.coil3:coil:3.3.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-core:3.3.0")

    implementation("com.squareup.okhttp3:okhttp:5.2.1")
    implementation("com.squareup.okio:okio:3.16.1")
    implementation("com.github.junrar:junrar:7.5.5")

    implementation("org.slf4j:slf4j-api:2.0.16")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // TDLib Kotlin Coroutines Client (Android)
    implementation("dev.g000sha256:tdl-coroutines-android:5.0.0")

    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.tv:tv-material:1.0.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    implementation("io.objectbox:objectbox-android:5.0.1")
    implementation("io.objectbox:objectbox-kotlin:5.0.1")
    // Note: objectbox-processor is NOT needed when using the ObjectBox Gradle plugin.
    // The plugin handles code generation via bytecode transformation.

    // TDLib Logging/Tools Cluster - Quality & Debug Tools
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    debugImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.10.2")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
