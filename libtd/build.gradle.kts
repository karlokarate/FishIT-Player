plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chris.m3usuite.libtd" // darf sich von Java-Package unterscheiden
    compileSdk = 36

    defaultConfig {
        minSdk = 24 // 23 geht auch; nimm >= App-minSdk

        // WICHTIG: ABI-Filter NICHT hier setzen! Sonst beschneidest du das AAR.
        // Die ABI-Auswahl machst du im App-Modul (splits) bzw. im Workflow.
        consumerProguardFiles("consumer-rules.pro")
    }

    // jniLibs-Ordner explizit registrieren (failsafe)
    sourceSets.getByName("main") {
        jniLibs.srcDirs("src/main/jniLibs")
        // Java liegt standardmäßig unter src/main/java → OK für org/drinkless/tdlib/*
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { isMinifyEnabled = false }
    }

    packaging {
        jniLibs { useLegacyPackaging = false } // moderne Packaging-Pfade
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
}

dependencies {
    // TdApi/Client sind plain Java; nur Annotations als compileOnly ist okay
    compileOnly("androidx.annotation:annotation:1.9.1")
}