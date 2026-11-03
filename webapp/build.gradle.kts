@file:OptIn(ExperimentalWasmDsl::class, ExperimentalDistributionDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalDistributionDsl

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.kt)
    alias(libs.plugins.jsworker)
}

kotlin {
    jvmToolchain(21)
    wasmJs {
        browser {
            distribution {
                this.distributionName = "webApp"
            }
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":sample-shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
        wasmJsMain.dependencies {
            implementation(project(":pdfmp"))
            implementation(project(":pdfmp", "wasmJsWorkerOutput"))
        }
    }
}

dependencies {
    "wasmJsWorker"(project(":pdfium-wasm"))
}