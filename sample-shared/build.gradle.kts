@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.dshatz.pdfmp.buildlogic.nativeTargets
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.kt)
    alias(libs.plugins.android.lib)
    alias(libs.plugins.atomicfu)
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
    }
    val iosTargets = buildList {
        if ("iosX64" in nativeTargets) add(iosX64())
        if ("iosArm64" in nativeTargets) add(iosArm64())
        if ("iosSimulatorArm64" in nativeTargets) add(iosSimulatorArm64())
    }

    val xcf = XCFramework()
    iosTargets.forEach {
        it.binaries.framework {
            baseName = "pdfmpcompose"
            xcf.add(this)
            export(project(":pdfmp-compose"))
        }
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
        }
    }
}

compose.resources {
    packageOfResClass = "com.dshatz.pdfmp.sample"
}