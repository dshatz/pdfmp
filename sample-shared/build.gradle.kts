@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.mp)
    if (freshCompose) {
        alias(libs.plugins.compose.fresh)
    } else {
        alias(libs.plugins.compose)
    }
    alias(libs.plugins.kni)
    alias(libs.plugins.compose.kt)
    alias(libs.plugins.android.lib)
}

kotlin {
    jvmToolchain(21)
    jvm {
        mainRun {
            mainClass = "com.dshatz.pdfmp.MainKt"
        }
    }
    android {
        namespace = "com.dshatz.pdfmp.sampleshared"
        compileSdk = 37
        minSdk = 24
        androidResources.enable = true
    }

    optionalTargets {
        val iosTargets = listOfNotNull(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        )
        val xcf = XCFramework()
        configure(iosTargets) {
            binaries.framework {
                baseName = "pdfmpcompose"
                xcf.add(this)
                export(project(":pdfmp-compose"))
            }
        }
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser {
                commonWebpackConfig {
                    outputFileName = "sample-shared"
                }
            }
            binaries.library()
        }
        /*js {
            browser()
            binaries.library()
        }*/
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":pdfmp-compose"))
            if (freshCompose) {
                implementation(libs.compose.runtime.fresh)
                implementation(libs.compose.resources.fresh)
            } else {
                implementation(libs.compose.runtime)
                implementation(libs.compose.resources)
            }
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)

            implementation(libs.jni.buffers)
            implementation(libs.coroutines)
            implementation(libs.filekit.dialogs)
        }
        androidMain.dependencies {
            // FileProvider, for the "Content URI" demo.
            implementation(libs.androidx.core)
        }
    }
}

compose.resources {
    packageOfResClass = "com.dshatz.pdfmp.sample"
}
