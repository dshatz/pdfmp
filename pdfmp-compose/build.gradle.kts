@file:OptIn(ExperimentalKotlinGradlePluginApi::class,
    ExperimentalWasmDsl::class
)

import com.vanniktech.maven.publish.DeploymentValidation
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.kt)
    alias(libs.plugins.testballoon)
    alias(libs.plugins.ksp) // for testballoon
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kni)
    alias(libs.plugins.publish)
}

version = project.findProperty("version") as? String ?: "0.1.0-SNAPSHOT1"

kotlin {
    compilerOptions {
        optIn.add("com.dshatz.pdfmp.InternalPdfmpApi")
    }
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
    android {
        namespace = "com.dshatz.pdfmp.compose"
        compileSdk = 37
        minSdk = 24
        androidResources.enable = true

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
            implementation(libs.compose.runtime)
            implementation(libs.compose.material3)
            implementation(libs.compose.foundation)
        }
        commonTest.dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.compose.ui.test)
            implementation(libs.test.core)
            implementation(libs.test.kotest)
        }
        jvmTest.dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        named("androidDeviceTest").dependencies {
            implementation(libs.test.core)
            implementation(libs.androidx.runner)
        }
    }
}


mavenPublishing {
    signAllPublications()
    publishToMavenCentral(true, DeploymentValidation.NONE)
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

// This module has no Compose UI tests in the browser. The check would require `binaries.executable()`, which a
// library target doesn't need, and it runs even without any wasm test sources.
tasks.matching { it.name == "checkComposeUiTestConfigurationForWasmJs" }.configureEach { enabled = false }

tasks.withType<Test>().configureEach {
    outputs.upToDateWhen { false }
    failOnNoDiscoveredTests = false
}

tasks.withType<KotlinNativeTest>().configureEach {
    outputs.upToDateWhen { false }
    failOnNoDiscoveredTests = false
}

tasks.withType<KotlinTest>().configureEach {
    outputs.upToDateWhen { false }
    failOnNoDiscoveredTests = false
}
