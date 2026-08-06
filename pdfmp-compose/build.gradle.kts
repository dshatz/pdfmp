@file:OptIn(ExperimentalComposeLibrary::class, ExperimentalKotlinGradlePluginApi::class,
    ExperimentalWasmDsl::class
)

import com.dshatz.pdfmp.buildlogic.configureTests
import com.dshatz.pdfmp.buildlogic.iosTargets
import com.dshatz.pdfmp.buildlogic.nativeTargets
import com.gradle.scan.agent.serialization.scan.serializer.kryo.it
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.kt)
    alias(libs.plugins.testballoon)
    alias(libs.plugins.ksp) // for testballoon
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kni)
    alias(libs.plugins.publish)
    jacoco
}

version = project.findProperty("version") as? String ?: "0.1.0-SNAPSHOT1"

kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("nonAndroid") {
                withIos()
                withJvm()
                withWasmJs()
            }
            withAndroidTarget()
        }
    }
    jvmToolchain(21)
    androidLibrary {
        namespace = "com.dshatz.pdfmp.compose"
        compileSdk = 36
        minSdk = 24

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            this.instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    jvm()

    optionalTargets {
        val iosTargets = listOfNotNull(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        )
        val xcf = XCFramework()
        configure(iosTargets) {
            binaries.framework {
                baseName = "pdfmpcompose"
                export(project(":pdfmp"))
                xcf.add(this)
            }
        }
        wasmJs {
            outputModuleName = "pdfmp-compose"
            binaries.library()
            browser {
                commonWebpackConfig {
                    output?.library = "pdfmp-compose"
                }
            }
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
            implementation(libs.coroutines.test)
            implementation(compose.uiTest)
            implementation(libs.test.core)
            implementation(libs.test.kotest)
        }
        jvmTest.dependencies {
            implementation(libs.coroutines.test)
            implementation("io.mockk:mockk:1.14.6")
        }
        named("androidDeviceTest").dependencies {
            implementation(libs.test.core)
            implementation("androidx.test:runner:1.7.0")
        }
        wasmJsMain.dependencies {
        }
    }
    configureTests(this)
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

jacoco {
    toolVersion = "0.8.14"
    reportsDirectory = layout.buildDirectory.dir("jacocoReports")
}