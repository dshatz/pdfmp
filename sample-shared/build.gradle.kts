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
        compileSdk = 36
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

            implementation(libs.ktor.core)
            implementation(libs.ktor.cio)
            implementation(libs.jni.buffers)
            implementation(libs.coroutines)
        }
    }
}

compose.resources {
    packageOfResClass = "com.dshatz.pdfmp.sample"
}