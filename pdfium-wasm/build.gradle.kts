import com.dshatz.jsworker.asWebWorker
import org.gradle.kotlin.dsl.jacoco

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.ksp)
    alias(libs.plugins.publish)
    alias(libs.plugins.kni)
    alias(libs.plugins.serial)
    alias(libs.plugins.testballoon)
    alias(libs.plugins.jsworker)
    alias(libs.plugins.wasmplugin)
    jacoco
}

kotlin {
    jvmToolchain(21)
    applyDefaultHierarchyTemplate()
    wasmJs {
        binaries.executable()
        browser()
        asWebWorker()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":pdfium"))
            api(libs.jsworker.workers)
        }
    }
}

dependencies {
    "kspWasmJs"(libs.jsworker.ksp)
}