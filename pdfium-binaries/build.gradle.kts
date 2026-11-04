import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("base")
    id("maven-publish")
    alias(libs.plugins.mp) apply false
}

group = "com.dshatz"

// Set in settings.gradle.kts (-PkniAllowedTargets or local.properties).
// Kotlin is only needed for the wasm resources; without a target the plugin would fail.
if (gradle.extra["wasmEnabled"] as Boolean) {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(21)
        applyDefaultHierarchyTemplate()
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser()
            binaries.library()
        }
        sourceSets.getByName("wasmJsMain").resources.srcDir(layout.projectDirectory.dir("binaries/wasmJs"))
    }
}

val linuxJar by tasks.registering(Jar::class) {
    // This is the classifier that consumers will use
    archiveClassifier.set("linux-x64")

    from("binaries/linux-x64") {
        into("natives/linux-x64")
    }
}

val macosJar by tasks.registering(Jar::class) {
    archiveClassifier.set("macos-arm64")
    from("binaries/macos-arm64") {
        into("natives/macos-arm64")
    }
}

val windowsJar by tasks.registering(Jar::class) {
    archiveClassifier.set("windows-x64")
    from("binaries/windows-x64") {
        into("natives/windows-x64")
    }
}