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

//include(":native-tools")
include(":pdfmp")
include(":pdfmp-compose")
//include(":sample")
include(":androidapp")
include(":desktopapp")
include(":webapp")
include(":sample-shared")
include(":image-buffer")
include(":pdfium-binaries")
rootProject.name = "pdf-multiplatform"