pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        google()
    }
}
includeBuild("build-logic")
include(":pdfmp")
include(":pdfmp-compose")
include(":sample")
include(":sample-shared")
include(":pdfium-binaries")
rootProject.name = "pdf-multiplatform"