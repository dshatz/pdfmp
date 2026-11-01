@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.dshatz.pdfmp.buildlogic.nativeTargets
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.compose)
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
    androidLibrary {
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
            implementation(compose.runtime)
            implementation(compose.material3)
            implementation(compose.components.resources)
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
// This module has no Compose UI tests in the browser. The check would require `binaries.executable()`, which a
// library target doesn't need, and it runs even without any wasm test sources.
tasks.matching { it.name == "checkComposeUiTestConfigurationForWasmJs" }.configureEach { enabled = false }
