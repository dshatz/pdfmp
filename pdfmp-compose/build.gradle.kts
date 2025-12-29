@file:OptIn(ExperimentalComposeLibrary::class, ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.kt)
    alias(libs.plugins.android.lib)
    alias(libs.plugins.publish)
}

version = project.findProperty("version") as? String ?: "0.1.0-SNAPSHOT1"

kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("nonAndroid") {
                withIos()
                withJvm()
            }
            withAndroidTarget()
        }
    }
    jvmToolchain(21)
    androidTarget()
    jvm()

    val iosTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64())

    val xcf = XCFramework()
    iosTargets.forEach {
        it.binaries.framework {
            baseName = "pdfmpcompose"
            export(project(":pdfmp"))
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":pdfmp"))
            implementation(compose.runtime)
            implementation(compose.material3)
            implementation(compose.foundation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(compose.uiTest)
        }
        jvmTest.dependencies {
            implementation(libs.coroutines.test)
            implementation("io.mockk:mockk:1.14.6")
        }
    }
}

android {
    namespace = "com.dshatz.pdfmp.compose"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}


mavenPublishing {
    signAllPublications()
    publishToMavenCentral(true, validateDeployment = false)
    coordinates("com.dshatz.pdfmp", "pdfmp-compose", project.version.toString())

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
