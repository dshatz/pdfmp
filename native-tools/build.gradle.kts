@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.dshatz.pdfmp.buildlogic.isMacos
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.osdetector)
}

kotlin {
    jvmToolchain(21)
    applyDefaultHierarchyTemplate {
        common {
            group("native") {
                group("desktop") {
                    withLinux()
                    withMingw()
                    withMacos()
                }
                withAndroidNative()
            }
        }
    }
    androidNativeX64()
    androidNativeArm64()
    androidNativeArm32()
    androidNativeX86()

    linuxX64()
    linuxArm64()
    mingwX64()
    if (project.isMacos()) {
        macosArm64()
        macosX64()
    }
    sourceSets {
        nativeMain.dependencies {
            implementation(libs.jni)
        }
    }

    compilerOptions {
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }
}