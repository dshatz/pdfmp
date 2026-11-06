@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)

import com.dshatz.kni.bundlesNatives
import com.vanniktech.maven.publish.DeploymentValidation
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.kni)
    alias(libs.plugins.ksp)
    alias(libs.plugins.serial)
    alias(libs.plugins.android.lib)
    alias(libs.plugins.publish)
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
                group("skikoNative") {
                    group("desktopNativeSkiko") {
                        withMacos()
                        withLinux()
                    }
                    group("ios") {
                        withIos()
                    }
                }
                group("desktopNative") {
                    withMingw()
                    group("desktopNativeSkiko")
                }
            }
            group("consumer") {
                group("android") {
                    withAndroidTarget()
                }
                group("desktopNative")
                group("skiko") {
                    withJvm()
                    group("skikoNative")
                }
                group("web") {
                    withJs()
                    withWasmJs()
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

        // Not covered by optionalTargets; follows the wasm switch from settings.gradle.kts.
        if (gradle.extra["wasmEnabled"] as Boolean) {
            js(IR) {
                binaries.library()
                browser()
            }
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
        compileSdk = 37
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
                implementation(libs.skiko)
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
            }
        }

        commonMain.dependencies {
            implementation(libs.jni.annotations)
        }

        webMain.dependencies {
            implementation(libs.serial)
            implementation(libs.skiko)
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral(true, DeploymentValidation.NONE)
    coordinates("com.dshatz.pdfmp", "image-buffer", project.version.toString())

    pom {
        name.set("PDF Multiplatform - ImageBuffers")
        description.set("A multiplatform PDF display library for Kotlin.")
        inceptionYear.set("2025")
        url.set("https://github.com/dshatz/pdfmp/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("dshatz")
                name.set("Daniels Šatcs")
                url.set("https://github.com/dshatz/")
            }
        }
        scm {
            url.set("https://github.com/dshatz/pdfmp/")
            connection.set("scm:git:git://github.com/dshatz/pdfmp.git")
            developerConnection.set("scm:git:ssh://git@github.com/dshatz/pdfmp.git")
        }
    }
}