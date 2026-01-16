@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.kt)
    alias(libs.plugins.android.app)
}

kotlin {
    jvmToolchain(21)
    jvm {
        mainRun {
            mainClass = "com.dshatz.pdfmp.MainKt"
        }
    }
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":sample-shared"))
//            implementation("com.dshatz.pdfmp:pdfmp-compose-jvm:unspecified")
            implementation(compose.runtime)
            implementation(compose.material3)
            implementation(compose.components.resources)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.common)

        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.12.2")
        }
    }
}

android {
    namespace = "com.dshatz.pdfmp.sample"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = buildTypes.getByName("debug").signingConfig
        }
    }
}

compose.desktop.application {
    mainClass = "com.dshatz.pdfmp.MainKt"
}