@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

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
version = project.findProperty("version") as? String ?: "0.1.0-SNAPSHOT1"

// Map KMP target names to standard pdfium lib folder.
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
    val targetName = name
    compilations.getByName("main").cinterops {
        create("pdfium") {
            val binariesModuleDir = rootProject.project("pdfium-binaries").projectDir
            val platformFolder = when (targetName) {
                "iosArm64" -> "ios-arm64"
                "iosX64", "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
                else -> null // Skip unsupported native targets
            }

            defFile("${projectDir}/cinterop/pdfium/pdfium.def")
            compilerOpts.add("-I${projectDir}/cinterop/pdfium")
            packageName("com.dshatz.internal.pdfium")

            // ios only
            if (platformFolder != null) {
                val libPath = "$binariesModuleDir/binaries/ios-framework/pdfium.xcframework/$platformFolder"
                extraOpts("-staticLibrary", "libpdfium.a", "-libraryPath", libPath)
//                 includeDirs("$libPath/Headers")
            }
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
            if (androidLib != null) {
                linkerOpts("-ljnigraphics")
            }
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

fun KotlinNativeTarget.setupIosFramework() {
    val targetName = name
    binaries {
        val binariesModuleDir = project(":pdfium-binaries").projectDir
        val sliceName = when (targetName) {
            "iosArm64" -> "ios-arm64"
            "iosX64", "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
            else -> throw GradleException("Unknown target for PDFium: $targetName")
        }
        val libPath = "$binariesModuleDir/binaries/ios-framework/pdfium.xcframework/$sliceName"
        framework {
            this.baseName = "pdfmp"
            isStatic = false
        }

        binaries.all {
            // Link the Static Framework
            linkerOpts("-L$libPath")
            linkerOpts("-lpdfium")

            linkerOpts("-lc++")
            linkerOpts("-framework", "CoreGraphics")
            linkerOpts("-framework", "CoreText")
            linkerOpts("-framework", "ImageIO")
            linkerOpts("-framework", "QuartzCore")
        }
    }
}

kotlin {
    applyDefaultHierarchyTemplate {
        common {
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
            group("consumer") {
                group("consumerJni") {
                    withJvm()
                    withAndroidTarget()
                }
                group("consumerNative") {
                    withIos()
                }
            }
        }
    }
    jvmToolchain(21)
    androidTarget {}
    jvm()

    // Android Native Targets

    val androidTargets = listOf(
        androidNativeX64 {  setUpPdfiumCinterop(); setupSharedLib() },
        androidNativeArm64 {  setUpPdfiumCinterop(); setupSharedLib() },
        androidNativeArm32 {  setUpPdfiumCinterop(); setupSharedLib() },
        androidNativeX86 {  setUpPdfiumCinterop(); setupSharedLib() },
    )

    configure(androidTargets) {
        binaries.all {
            // Force the linker to use 16KB alignment
            linkerOpts("-z", "max-page-size=16384")
            linkerOpts("-Wl,--allow-shlib-undefined")
        }
    }


    // Desktop Native Targets
    linuxX64 { setUpPdfiumCinterop(); setupSharedLib() }
    linuxArm64 { setUpPdfiumCinterop(); setupSharedLib() }
    mingwX64 { setUpPdfiumCinterop(); setupSharedLib() }
    macosArm64 { setUpPdfiumCinterop(); setupSharedLib() }
    macosX64 { setUpPdfiumCinterop(); setupSharedLib() }

    // iOS Targets
    iosArm64 { setUpPdfiumCinterop(); setupSharedLib(); setupIosFramework() }
    iosSimulatorArm64 { setUpPdfiumCinterop(); setupSharedLib(); setupIosFramework() }
    iosX64 { setUpPdfiumCinterop(); setupSharedLib(); setupIosFramework() }

    sourceSets {
        commonMain.dependencies {
            api(libs.io)
            implementation(libs.coroutines)
        }
        getByName("nativeJniMain") {
            dependencies {
                implementation(libs.jni)
                implementation(libs.jni.annotations)
            }
        }
        getByName("consumerMain").dependencies {
//            implementation(libs.skiko)
        }
        getByName("androidNativeMain").dependsOn(getByName("nativeJniMain"))
        getByName("consumerNativeMain").dependsOn(getByName("nativeMain"))
        val nonAndroidConsumer = create("nonAndroidConsumerMain")
        nonAndroidConsumer.dependsOn(getByName("consumerMain"))
        getByName("jvmMain").dependsOn(nonAndroidConsumer)
        getByName("iosMain").dependsOn(nonAndroidConsumer)
        nonAndroidConsumer.dependencies {
            implementation(libs.skiko)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }
}

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

    sourceSets.getByName("main") {
        jniLibs.srcDir(packageAndroidNatives.map { it.destinationDir })
    }

    libraryVariants.configureEach {
        preBuildProvider.configure {
            dependsOn(packageAndroidNatives)
        }
    }
}


val generateNativeResources by tasks.registering(Sync::class) {
    group = "build"
    description = "Copies native libraries to the build directory to be included as resources"

    // Output everything to build/generated/native-libs
    val outputDir = layout.buildDirectory.dir("generated/native-libs")
    into(outputDir)

    // Handle duplicates just in case
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    desktopTargetMap.forEach { (targetName, resourcePath) ->
        val target = kotlin.targets.findByName(targetName) as? org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

        if (target != null) {
            val sharedLib = target.binaries.findSharedLib("release")
            if (sharedLib != null) {
                dependsOn(sharedLib.linkTaskProvider)
                from(sharedLib.outputFile) {
                    // Note: This creates the structure inside the generated folder
                    into("lib/$resourcePath")
                }
            }

            val prebuiltDir = rootProject.project("pdfium-binaries").file("binaries/$targetName")
            if (prebuiltDir.exists()) {
                from(prebuiltDir) {
                    include("*.so", "*.dll", "*.dylib")
                    into("lib/$resourcePath")
                }
            }
        }
    }
}

kotlin.sourceSets.getByName("jvmMain") {
    resources.srcDir(generateNativeResources)
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
    add("kspIosX64", libs.jni.ksp)
    add("kspIosArm64", libs.jni.ksp)
    add("kspIosSimulatorArm64", libs.jni.ksp)
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral(true, validateDeployment = false)
    coordinates("com.dshatz.pdfmp", "pdfmp", project.version.toString())

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
