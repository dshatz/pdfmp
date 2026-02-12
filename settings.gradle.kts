pluginManagement {
    repositories {
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
//include(":native-tools")
include(":pdfmp")
include(":pdfmp-compose")
//include(":sample")
include(":androidapp")
include(":desktopapp")
include(":sample-shared")
include(":pdfium-binaries")
rootProject.name = "pdf-multiplatform"