import com.dshatz.jsworker.asWebWorker
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.ksp)
    alias(libs.plugins.publish)
    alias(libs.plugins.kni)
    alias(libs.plugins.serial)
    alias(libs.plugins.testballoon)
    alias(libs.plugins.jsworker)
    alias(libs.plugins.wasmplugin)
    jacoco
}

private val androidArchMap = mapOf(
    "androidNativeArm64" to "android/arm64-v8a",
    "androidNativeX64"   to "android/x86_64",
    "androidNativeArm32" to "android/armeabi-v7a",
    "androidNativeX86"   to "android/x86"
)

group = "com.dshatz"
version = project.findProperty("version") as? String ?: "0.1.0-SNAPSHOT1"

kotlin {
    applyDefaultHierarchyTemplate()
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

        wasmJs {
            outputModuleName = "kpdfium"
            this.binaries.library()
            browser {
                commonWebpackConfig {
                    output?.library = "kpdfium"
                }
                testTask {
                    useKarma {
                        useFirefox()
                    }
                }
            }
            asWebWorker()
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

    sourceSets {
        commonMain.dependencies {
            implementation(libs.serial)
        }

        commonTest.dependencies {
            implementation(libs.test.core)
            implementation(libs.test.kotest)
        }

        webMain.dependencies {
            implementation(libs.jsworker.workers)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }
}


dependencies {
    "kspWasmJs"(libs.jsworker.ksp)
//    wasmJsDistribution(project(":pdfium-binaries"))
}

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
