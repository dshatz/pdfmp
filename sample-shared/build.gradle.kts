import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.kt)
    alias(libs.plugins.android.lib)
}

kotlin {
    jvmToolchain(21)
    jvm {
        mainRun {
            mainClass = "com.dshatz.pdfmp.MainKt"
        }
    }
    androidTarget()
    val iosTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64())

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
        }
    }
}

android {
    namespace = "com.dshatz.pdfmp.sampleshared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}

compose.resources {
    packageOfResClass = "com.dshatz.pdfmp.sample"
}