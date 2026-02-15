plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
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
    // === MCP SDK (official Kotlin SDK from modelcontextprotocol) ===
    // Version 0.4.0 uses root package imports (io.modelcontextprotocol.kotlin.sdk.*)
    // See: https://github.com/modelcontextprotocol/kotlin-sdk
    implementation("io.modelcontextprotocol:kotlin-sdk:0.4.0")

    // === Ktor (required by MCP SDK for transport) ===
    implementation("io.ktor:ktor-client-core:3.1.1")
    implementation("io.ktor:ktor-client-cio:3.1.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.1")

    // === Kotlin extensions ===
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.6.0")

    // === OkHttp for Xtream API calls ===
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // === TDLib JVM Client (g00sha's tdl-coroutines) ===
    // The JVM artifact bundles native libraries for Linux x64, macOS arm64, macOS x64
    // See: https://github.com/g000sha256/tdl-coroutines
    implementation("dev.g000sha256:tdl-coroutines:8.0.0")

    // === Logging (no-op for STDIO server) ===
    implementation("org.slf4j:slf4j-nop:2.0.9")
}

application {
    mainClass.set("com.fishit.player.tools.mcp.MainKt")
}

// =============================================================================
// FishIT Pipeline MCP Server
//
// Standalone JVM application providing MCP tools for:
// - Xtream API testing (real API calls)
// - Telegram schema/mock data generation
// - Pipeline normalization testing
//
// Run: ./gradlew :tools:mcp-server:run
// Build fat JAR: ./gradlew :tools:mcp-server:fatJar
//
// See: tools/mcp-server/README.md
// =============================================================================

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.fishit.player.tools.mcp.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}
