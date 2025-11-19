plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("com.google.devtools.ksp") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
}

// Apply quality plugins to all subprojects
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.files("detekt-config.yml"))
        buildUponDefaultConfig = true
        allRules = false
    }
    
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.0.1")
        android.set(true)
        outputToConsole.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)
        
        filter {
            exclude("**/generated/**")
            exclude("**/build/**")
            exclude("**/reference/**")
        }
    }
}
