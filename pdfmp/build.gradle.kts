@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.dshatz.pdfmp.buildlogic.Host
import com.dshatz.pdfmp.buildlogic.configureTests
import com.dshatz.pdfmp.buildlogic.getHost
import com.dshatz.pdfmp.buildlogic.isMacos
import com.dshatz.pdfmp.buildlogic.nativeTargets
import org.gradle.internal.extensions.stdlib.capitalized
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.SharedLibrary
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kotest)
    alias(libs.plugins.ksp)
    alias(libs.plugins.osdetector)
    alias(libs.plugins.publish)
    jacoco
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
            val iosDir = when (targetName) {
                "iosArm64" -> "ios-arm64"
                "iosX64", "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
                else -> null // Skip unsupported ios native targets
            }

            defFile("${projectDir}/cinterop/pdfium/pdfium.def")
            compilerOpts.add("-I${projectDir}/cinterop/pdfium")
            packageName("com.dshatz.internal.pdfium")

            // ios only
            if (iosDir != null) {
                val libPath = "$binariesModuleDir/binaries/ios-framework/pdfium.xcframework/$iosDir"
                extraOpts("-staticLibrary", "libpdfium.a", "-libraryPath", libPath)
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

    binaries {
        getTest("DEBUG").apply {
            val binariesModuleDir = project(":pdfium-binaries").projectDir.absolutePath
            val libPath = "$binariesModuleDir/binaries/$pdfiumPath"

            linkerOpts("-rpath", libPath)
        }
    }
}

tasks.withType<KotlinNativeLink>().configureEach {
    val taskName = name
    if (taskName.contains("Test", ignoreCase = true) && target.startsWith("macos")) {
        val target = target.substringBefore('_') + target.substringAfter('_').capitalized()
        doLast {
            val originalDylibPath = project(":pdfium-binaries").projectDir.absolutePath + "/binaries/${target}/libpdfium.dylib"

            val testBinary = outputFile.get()

            logger.lifecycle("Patch test executable for $target")
            exec {
                commandLine(
                    "install_name_tool",
                    "-change",
                    "./libpdfium.dylib", // The install_name currently inside the binary
                    originalDylibPath,   // The absolute path you want it to use during test
                    testBinary.absolutePath
                )
            }
            println("Patched test binary dependencies for: ${testBinary.name}")
        }
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
            group("consumer") {
                group("consumerJni") {
                    withJvm()
                    withAndroidTarget()
                }
                withAndroidTarget()
            }
            group("native") {
                group("nativeJni") {
                    group("desktopNativeJni") {
                        withLinux()
                        withMingw()
                        withMacos()
                    }
                    withAndroidNative()
                }
                group("nativeNonJni") {
                    withIos()
                }
            }
        }
    }
    jvmToolchain(21)
    androidTarget {
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }
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
    if (project.isMacos()) {
        macosArm64 { setUpPdfiumCinterop(); setupSharedLib() }
        macosX64 { setUpPdfiumCinterop(); setupSharedLib() }
    }

    // iOS Targets
    iosArm64 { setUpPdfiumCinterop(); setupSharedLib(); setupIosFramework() }
    iosSimulatorArm64 { setUpPdfiumCinterop(); setupSharedLib(); setupIosFramework() }
    iosX64 { setUpPdfiumCinterop(); setupSharedLib(); setupIosFramework() }

    sourceSets {
        commonMain.dependencies {
            api(libs.io)
            implementation(libs.coroutines)
            implementation(libs.jni.annotations)
            implementation(libs.jni.buffers)
        }
        getByName("nativeJniMain") {
            dependencies {
                implementation(libs.jni)
            }
        }
        getByName("androidNativeMain").dependsOn(getByName("nativeJniMain"))
        val (nonAndroidConsumerMain, nonAndroidConsumerTest) = createMainAndTest("nonAndroidConsumer", "consumer", "jvm", "ios")
        nonAndroidConsumerMain.dependencies {
            implementation(libs.skiko)
        }


        commonTest.dependencies {
//            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.kotest)
            implementation(libs.kotest.assertions)
        }
        jvmTest.dependencies {
            val skikoVersion = libs.versions.skiko.get()

            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch").lowercase()

            val targetOs = when {
                osName.contains("win") -> "windows"
                osName.contains("mac") -> "macos"
                else -> "linux"
            }

            val targetArch = when {
                osArch.contains("arm") || osArch.contains("aarch64") -> "arm64"
                else -> "x64"
            }
            implementation("org.jetbrains.skiko:skiko-awt-runtime-$targetOs-$targetArch:$skikoVersion")
            implementation(libs.kotest.junit5)
        }
        val nativeJniMain by getting {
            dependencies {
//                implementation(project(":native-tools"))
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }
    project.configureTests(this)
}

fun NamedDomainObjectContainer<KotlinSourceSet>.createMainAndTest(name: String, parent: String, vararg dependants: String): Pair<KotlinSourceSet, KotlinSourceSet> {
    val main = create("${name}Main")
    val test = create("${name}Test")
    main.dependsOn(getByName("${parent}Main"))
    test.dependsOn(getByName("${parent}Test"))

    dependants.forEach {
        getByName("${it}Main").dependsOn(main)
        getByName("${it}Test").dependsOn(test)
    }
    return main to test
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
        consumerProguardFiles(project.file("consumer-rules.pro"))
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


fun registerNativeResources(taskName: String, buildType: String) = tasks.register<Sync>(taskName) {
    group = "build"
    val outputDir = layout.buildDirectory.dir("generated/native-libs/$buildType")
    into(outputDir)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    desktopTargetMap.forEach { (targetName, resourcePath) ->
        val target = kotlin.targets.findByName(targetName) as? org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
        if (targetName in nativeTargets && target != null) {
            val sharedLib = target.binaries.findSharedLib(buildType)
            if (sharedLib != null) {
                dependsOn(sharedLib.linkTaskProvider)
                from(sharedLib.outputFile) {
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

val generateDebugResources = registerNativeResources("generateDebugNativeResources", "debug")
val generateReleaseResources = registerNativeResources("generateReleaseNativeResources", "release")

kotlin.sourceSets.getByName("jvmTest") {
    resources.srcDir(generateDebugResources)
}

tasks.named<Jar>("jvmJar") {
    from(generateDebugResources)
}

tasks.withType<JavaExec>().configureEach {
    classpath += files(generateDebugResources)
}

dependencies {
    add("kspLinuxX64", libs.jni.ksp)
    add("kspLinuxArm64", libs.jni.ksp)
    add("kspMingwX64", libs.jni.ksp)
    add("kspAndroidNativeX64", libs.jni.ksp)
    add("kspAndroidNativeArm64", libs.jni.ksp)
    add("kspAndroidNativeArm32", libs.jni.ksp)
    add("kspAndroidNativeX86", libs.jni.ksp)
    if (project.isMacos()) {
        add("kspMacosX64", libs.jni.ksp)
        add("kspMacosArm64", libs.jni.ksp)
    }
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


jacoco {
    toolVersion = "0.8.14"
    reportsDirectory = layout.buildDirectory.dir("jacocoReports")
}


// Copy test resources to jvm
tasks.named<ProcessResources>("jvmTestProcessResources") {
    from("src/commonTest/resources")
}

// Copy test resources for Native (iOS/Linux/Windows/MacOS)
kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
    binaries.getTest("DEBUG").linkTaskProvider.configure {
        doLast {
            val binaryDir = outputFile.get().parentFile
            project.copy {
                from("src/commonTest/resources")
                into(binaryDir)
            }
        }
    }
}
