pluginManagement {
    repositories {
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
include(":pdfmp")
include(":pdfmp-compose")
include(":sample")
include(":pdfium-binaries")
rootProject.name = "pdf-multiplatform"