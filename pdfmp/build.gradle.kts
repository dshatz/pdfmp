@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.SharedLibrary

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.android.lib)
    alias(libs.plugins.ksp)
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.publish)
}

group = "com.dshatz"
version = "1.0.0"

// Map KMP target names to standard resource folder names (e.g. for JNI loading)
private val desktopTargetMap = mapOf(
    "linuxX64"   to "linux-x64",
    "linuxArm64" to "linux-arm64",
    "mingwX64"   to "windows-x64",
    "macosX64"   to "macos-x64",
    "macosArm64" to "macos-arm64"
)

private val androidArchMap = mapOf(
    "androidNativeArm64" to "android/arm64-v8a",
    "androidNativeX64"   to "android/x86_64",
    "androidNativeArm32" to "android/armeabi-v7a",
    "androidNativeX86"   to "android/x86"
)

fun KotlinNativeTarget.setUpPdfiumCinterop() {
    compilations.getByName("main").cinterops {
        create("pdfium") {
            defFile("${projectDir}/cinterop/pdfium/pdfium.def")
            compilerOpts.add("-I${projectDir}/cinterop/pdfium")
            packageName("com.dshatz.internal.pdfium")
        }
    }
}

private fun KotlinNativeTarget.setupSharedLib() {
    val androidLib = androidArchMap[name]
    val pdfiumPath = androidLib ?: name

    binaries {
        sharedLib {
            baseName = "pdfmp"
            linkerOpts.add("-rpath")
            linkerOpts.add("\$ORIGIN")
        }
    }
    // Link against pdfium
    binaries.all {
        val binariesModuleDir = project(":pdfium-binaries").projectDir
        linkerOpts.add("-L$binariesModuleDir/binaries/$pdfiumPath")
        linkerOpts.add("-lpdfium")
        if (name.contains("linux")) linkerOpts.add("-lcrypt")
    }
}


kotlin {
    applyDefaultHierarchyTemplate {
        group("native") {
            group("nativeJni") {
                group("desktopNative") {
                    withLinux()
                    withMingw()
                    withMacos()
                }
                withAndroidNative()
            }
            withIos()
        }
        group("frontend") {
            withJvm()
            withAndroidTarget()
        }
    }
    jvmToolchain(21)
    androidTarget {}
    jvm()

    // Android Native Targets
    androidNativeX64 { setUpPdfiumCinterop(); setupSharedLib() }
    androidNativeArm64 { setUpPdfiumCinterop(); setupSharedLib() }
    androidNativeArm32 { setUpPdfiumCinterop(); setupSharedLib() }
    androidNativeX86 { setUpPdfiumCinterop(); setupSharedLib() }

    // Desktop Native Targets
    linuxX64 { setUpPdfiumCinterop(); setupSharedLib() }
    linuxArm64 { setUpPdfiumCinterop(); setupSharedLib() }
    mingwX64 { setUpPdfiumCinterop(); setupSharedLib() }
    macosArm64 { setUpPdfiumCinterop(); setupSharedLib() }
    macosX64 { setUpPdfiumCinterop(); setupSharedLib() }

    // iOS Targets
    iosArm64 { setUpPdfiumCinterop(); setupSharedLib() }
    iosSimulatorArm64 { setUpPdfiumCinterop(); setupSharedLib() }

    sourceSets {
        commonMain.dependencies {
            api(libs.io)
        }
        getByName("nativeJniMain") {
            dependencies {
                implementation(libs.jni)
                implementation(libs.jni.annotations)
            }
        }
        getByName("androidNativeMain").dependsOn(getByName("nativeJniMain"))

        getByName("frontendMain") {
            dependsOn(getByName("commonMain"))
            dependencies {
                implementation(libs.coroutines)
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

// --- ANDROID NATIVE PACKAGING (AAR) ---

val packageAndroidNatives = tasks.register<Copy>("packageAndroidNatives") {
    group = "build"
    description = "Aggregates all native libs for Android packaging."
    val outputDir = layout.buildDirectory.dir("generated/jniLibs")
    into(outputDir)
}

kotlin.targets.withType<KotlinNativeTarget>().configureEach {
    val target = this
    val androidLibPath = androidArchMap[target.name]

    if (androidLibPath != null) {
        val abi = androidLibPath.substringAfter("android/")
        val prebuiltSourceFolder = androidLibPath

        target.binaries.withType<SharedLibrary>().configureEach {
            val binary = this
            packageAndroidNatives.configure {
                dependsOn(binary.linkTaskProvider)
                from(binary.outputFile) {
                    duplicatesStrategy = DuplicatesStrategy.INCLUDE
                    into(abi)
                }
            }
        }

        val prebuiltDir = rootProject.project("pdfium-binaries").file("binaries/$prebuiltSourceFolder")
        packageAndroidNatives.configure {
            if (prebuiltDir.exists()) {
                from(prebuiltDir) {
                    duplicatesStrategy = DuplicatesStrategy.INCLUDE
                    include("*.so")
                    into(abi)
                }
            }
        }
    }
}

android {
    namespace = "com.dshatz.pdfmp"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }

    // Inject the gathered native libs into the Android SourceSet so they end up in the AAR
    sourceSets.getByName("main") {
        jniLibs.srcDir(packageAndroidNatives.map { it.destinationDir })
    }
}

// --- JVM DESKTOP NATIVE BUNDLING (JAR) ---

// This hooks into the standard 'jvmJar' task and adds the native files inside.
tasks.named<Jar>("jvmJar") {
    // Iterate over our defined desktop targets
    desktopTargetMap.forEach { (targetName, resourcePath) ->
        val target = kotlin.targets.findByName(targetName) as? KotlinNativeTarget

        if (target != null) {
            // 1. Get the compiled shared library from THIS project (libpdfmp.so)
            val sharedLib = target.binaries.findSharedLib("release")
            if (sharedLib != null) {
                dependsOn(sharedLib.linkTaskProvider)
                from(sharedLib.outputFile) {
                    into("lib/$resourcePath")
                }
            }

            // 2. Get the prebuilt dependency library (libpdfium.so)
            // Note: We replicate the path logic from 'setupSharedLib'
            val prebuiltDir = rootProject.project("pdfium-binaries").file("binaries/$targetName")

            if (prebuiltDir.exists()) {
                from(prebuiltDir) {
                    include("*.so", "*.dll", "*.dylib")
                    into("lib/$resourcePath")
                }
            } else {
                logger.warn("Prebuilt binaries not found for $targetName at $prebuiltDir")
            }
        }
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    add("kspLinuxX64", libs.jni.ksp)
    add("kspLinuxArm64", libs.jni.ksp)
    add("kspMingwX64", libs.jni.ksp)
    add("kspAndroidNativeX64", libs.jni.ksp)
    add("kspAndroidNativeArm64", libs.jni.ksp)
    add("kspAndroidNativeArm32", libs.jni.ksp)
    add("kspAndroidNativeX86", libs.jni.ksp)
    add("kspMacosX64", libs.jni.ksp)
    add("kspMacosArm64", libs.jni.ksp)
}

mavenPublishing {
    coordinates("com.dshatz.pdfmp", "pdfmp", "1.0.0")

    pom {
        name.set("PDF Multiplatform")
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
