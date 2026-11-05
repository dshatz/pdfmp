pluginManagement {
    repositories {
        mavenLocal {
            content { includeGroupByRegex("com\\.dshatz.*") }
        }
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal {
            content { includeGroupByRegex("com\\.dshatz.*") }
        }
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
    }
}

plugins {
    id("com.gradle.develocity") version "4.6.1"
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
    }
}

includeBuild("pdfmp-wasmJs-plugin")

fun getProp(key: String): String? {
    return providers.gradleProperty(key).orNull
        ?: java.util.Properties().apply {
            file("local.properties").takeIf { it.exists() }?.reader()?.use(::load)
        }.getProperty(key)
}

// Same list the kni plugin reads; unset means all targets.
val allowedTargets = (
    getProp("kniAllowedTargets")
    )?.split(",")?.map { it.trim() }
val wasmEnabled = allowedTargets == null || "wasmJs" in allowedTargets
gradle.extra["wasmEnabled"] = wasmEnabled
val freshCompose = getProp("freshCompose")
if (freshCompose == "true") System.setProperty("freshCompose", "true") else System.clearProperty("freshCompose")

include(":pdfmp")
include(":pdfium")
include(":pdfmp-compose")
include(":desktopapp")
include(":androidapp")
include(":sample-shared")
include(":image-buffer")
include(":pdfium-binaries")
if (wasmEnabled) {
    include(":pdfium-wasm")
    include(":webapp")
}
rootProject.name = "pdf-multiplatform"