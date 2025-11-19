plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
// Task: Verify TDLib JNI has no dynamic OpenSSL deps (uses scripts/verify-tdlib-readelf.sh)
tasks.register("verifyTdlib", Exec::class) {
    group = "verification"
    description = "Verify tdlib JNI dependencies via readelf"
    commandLine("bash", "${project.rootDir}/scripts/verify-tdlib-readelf.sh")
}
