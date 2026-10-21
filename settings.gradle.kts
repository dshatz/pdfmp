pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
    }
}

plugins {
    id("com.gradle.develocity") version "4.3"
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
    }
}

includeBuild("build-logic")
includeBuild("pdfmp-wasmJs-plugin")

include(":pdfmp")
include(":pdfium")
include(":pdfmp-compose")
include(":desktopapp")
include(":androidapp")
include(":webapp")
include(":sample-shared")
include(":image-buffer")
include(":pdfium-binaries")
include(":pdfium-wasm")
rootProject.name = "pdf-multiplatform"