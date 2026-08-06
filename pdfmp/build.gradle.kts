@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.dshatz.kni.bundlesNatives
import com.dshatz.kni.bundlesPrebuiltNatives
import com.dshatz.pdfmp.buildlogic.configureOptional
import com.dshatz.pdfmp.buildlogic.configureTests
import com.dshatz.pdfmp.buildlogic.macOsTargets
import org.gradle.internal.extensions.stdlib.capitalized
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.SharedLibrary
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.ksp)
    alias(libs.plugins.publish)
    alias(libs.plugins.kni)
    alias(libs.plugins.android.lib)
    alias(libs.plugins.testballoon)
    jacoco
}

group = "com.dshatz"
version = project.findProperty("version") as? String ?: "0.1.0-SNAPSHOT1"

kni {
    autoWire {
        kspDependency.set(libs.jni.ksp)
        createSourceSets.set(false)
    }
}

private val androidArchMap = mapOf(
    "androidNativeArm64" to "android/arm64-v8a",
    "androidNativeX64"   to "android/x86_64",
    "androidNativeArm32" to "android/armeabi-v7a",
    "androidNativeX86"   to "android/x86"
)

fun KotlinNativeTarget.setupPdfiumCinterop() {
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
        freeCompilerArgs += listOf("-Xadd-light-debug=enable", "-g")
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

macOsTargets.forEach { target ->
    val capitalizedTarget = target.capitalized()

    val patchTask = tasks.register<Exec>("patch${target}Test") {
        val originalDylibPath = project(":pdfium-binaries").projectDir.absolutePath + "/binaries/${target}/libpdfium.dylib"
        executable = "install_name_tool"
        argumentProviders.add(CommandLineArgumentProvider {
            val linkTask = tasks.named<KotlinNativeLink>("linkDebugTest$capitalizedTarget").get()
            val testBinary = linkTask.outputFile.get().absolutePath
            listOf("-change", "./libpdfium.dylib", originalDylibPath, testBinary)
        })
    }

    tasks.withType<KotlinNativeLink>().configureEach {
        if (name == "linkDebugTest$capitalizedTarget") {
            finalizedBy(patchTask)
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
            group("pdfium") {
                group("native") {
                    group("desktopNative") {
                        withLinux()
                        withMingw()
                        withMacos()
                    }
                    group("androidNative") {
                        withAndroidNative()
                    }
                    group("ios") {
                        withIos()
                    }
                }
                withWasmJs()
            }
            group("jniCommon") {
                group("jniNative") {
                    group("desktopNative")
                    group("androidNative")
                }
                group("jniJvm") {
                    withJvm()
                    withAndroidTarget()
                }
            }
            group("consumer") {
                group("jniJvm")
                withIos()
                withWasmJs()
            }
        }
    }
    jvmToolchain(21)

    optionalTargets {

        val iosTargets = listOfNotNull(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        )
        configure(iosTargets) {
            setupPdfiumCinterop()
            setupSharedLib()
            setupIosFramework()
        }

        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            outputModuleName = "pdfmp"
            this.binaries.library()
            browser {
                commonWebpackConfig {
                    output?.library = "pdfmp"
                }
            }
            nodejs()
        }
    }

    val androidTargets = optionalTargets.run {
        listOfNotNull(
            androidNativeX86(),
            androidNativeX64(),
            androidNativeArm32(),
            androidNativeArm64()
        )
    }
    configure(androidTargets) {
        setupPdfiumCinterop()
        setupSharedLib()
        androidLinkerOpts()
    }

    val desktopTargets = optionalTargets.run {
        listOfNotNull(
            linuxX64(),
            linuxArm64(),
            mingwX64(),
            macosArm64(),
            macosX64()
        )
    }
    configure(desktopTargets) {
        setupPdfiumCinterop()
        setupSharedLib()
    }

    jvm {
        this bundlesNatives desktopTargets
        bundlesPrebuiltNatives {
            project.rootProject.layout.projectDirectory.dir("pdfium-binaries").dir("binaries").apply {
                linuxX64 = listOf(dir("linuxX64"))
                linuxArm64 = listOf(dir("linuxArm64"))
                mingwX64 = listOf(dir("mingwX64"))
                macosX64 = listOf(dir("macosX64"))
                macosArm64 = listOf(dir("macosArm64"))
            }
        }
    }

    androidLibrary {
        namespace = "com.dshatz.pdfmp"
        compileSdk = 36
        minSdk = 24

        optimization {
            this.consumerKeepRules.file(project.file("consumer-rules.pro"))
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            this.instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        bundlesNatives(androidTargets)
        bundlesPrebuiltNatives {
            all.add(project.rootProject.layout.projectDirectory
                .dir("pdfium-binaries")
                .dir("binaries").dir("android"))
            /*arm64_v8a.add(project.rootProject.layout.projectDirectory
                .dir("image-buffer")
            )*/
        }
    }


    sourceSets {
        commonMain.dependencies {
            api(libs.io)
            implementation(libs.coroutines)
            implementation(libs.jni.annotations)
            implementation(libs.jni.buffers)
            implementation(libs.jni.flows)
            implementation(libs.jni.serialization)
            implementation(libs.atomic)
            api(project(":image-buffer"))
        }
        val jniCommonMain by getting {
            dependencies {
                api(libs.jni)
            }
        }

        androidMain.configure {
            dependsOn(getByName("consumerMain"))
            dependsOn(getByName("jniJvmMain"))
        }

        val consumerMain by getting {
            dependencies {
                api(project(":image-buffer"))
            }
        }

        val androidNativeMain by getting {
            dependencies {
                api(project(":image-buffer"))
            }
        }


        commonTest.dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.test.core)
            implementation(libs.test.kotest)
        }
        named("androidDeviceTest") {
            dependencies {
                implementation(libs.test.core)
                implementation("androidx.test:runner:1.7.0")
            }
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
        }
        wasmJsMain.dependencies {
            implementation(libs.browser)
//            api(npm("@dshatz/pdfium-wasm", "0.1.2"))
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
        configureOptional("${it}Main") { dependsOn(main) }
        configureOptional("${it}Test") { dependsOn(test) }
    }
    return main to test
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
