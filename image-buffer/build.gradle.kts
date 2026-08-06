@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)

import com.dshatz.kni.bundlesNatives
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.kni)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.lib)
    jacoco
}

group = "com.dshatz"
version = project.findProperty("version") as? String ?: "0.1.0-SNAPSHOT1"

kni {
    autoWire {
        kspDependency.set(libs.jni.ksp)
        createSourceSets = false
    }
}

fun KotlinNativeTarget.androidLinkerOpts() {
    binaries.all {
        // Force the linker to use 16KB alignment
        linkerOpts("-z", "max-page-size=16384")
        linkerOpts("-z", "common-page-size=16384")
        linkerOpts("-Wl,--allow-shlib-undefined")
    }
}

kotlin {
    applyHierarchyTemplate {
        common {
            group("native") {
                group("androidNative") {
                    withAndroidNative()
                }
                group("desktopNative") {
                    withMacos()
                    withMingw()
                    withLinux()
                }
            }
            group("consumer") {
                group("android") {
                    withAndroidTarget()
                }
                group("desktopNative")
                group("skiko") {
                    withJvm()
                    withIos()
                    withWasmJs()
                    group("desktopNative")
                }
            }
            group("jniJvm") {
                group("android")
                withJvm()
            }
            group("jniNative") {
                group("desktopNative")
                group("androidNative")
            }
            group("jniCommon") {
                group("jniJvm")
                group("jniNative")
            }

        }
    }
    jvmToolchain(21)
    jvm()
    optionalTargets {
        iosX64()
        iosArm64()
        wasmJs {
            binaries.library()
            browser()
        }

        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    val desktopNative = optionalTargets.run {
        listOfNotNull(
            linuxX64(),
            linuxArm64(),
            mingwX64(),
            macosX64(),
            macosArm64()
        )
    }

    val androidNative = optionalTargets.run {
        listOfNotNull(
            androidNativeX64(),
            androidNativeX86(),
            androidNativeArm64(),
            androidNativeArm32()
        )
    }
    androidNative.forEach {
        it.binaries.sharedLib()
        it.androidLinkerOpts()
    }

    androidLibrary {
        namespace = "com.dshatz.pdfmp.imagebuffer"
        compileSdk = 36
        minSdk = 24

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            this.instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        bundlesNatives(androidNative)
    }

    sourceSets {
        val skikoMain by getting {
            dependencies {
                api(libs.skiko)
            }
        }
        val androidMain by getting {
            dependsOn(getByName("consumerMain"))
            dependsOn(getByName("jniJvmMain"))
        }
        val jniCommonMain by getting {
            dependencies {
                api(libs.jni.wrappers)
                implementation(libs.jni.serialization)
                implementation(libs.jni.annotations)
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }
}