plugins {
    kotlin("jvm")
    application
}

group = "com.fishit.player.tools"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Kotlin stdlib (provided by Gradle)
    implementation(kotlin("stdlib"))

    // Coroutines for async file scanning
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

application {
    mainClass.set("com.fishit.player.tools.docgen.MainKt")
}

tasks.named<JavaExec>("run") {
    // Allow passing CLI args: ./gradlew :tools:doc-generator:run --args="--all"
    args = project.findProperty("docgen.args")?.toString()?.split(" ") ?: listOf("--all")
    workingDir = rootProject.projectDir
}
