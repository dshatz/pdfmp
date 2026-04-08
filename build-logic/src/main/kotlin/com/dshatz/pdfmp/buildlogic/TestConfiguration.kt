package com.dshatz.pdfmp.buildlogic

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

private val androidTargets = listOf(
    "androidNativeX86",
    "androidNativeX64",
    "androidNativeArm32",
    "androidNativeArm64"
)
val Project.nativeTargets get() = run {
    val nativeTargets: String? by project
    nativeTargets?.split(',') ?: (listOf("linuxX64", "linuxArm64", "mingwX64", "macosX64", "macosArm64") + androidTargets)
}

fun Project.configureTests(kotlin: KotlinMultiplatformExtension) {
    tasks.named<Test>("jvmTest") {
        useJUnitPlatform()
    }
    tasks.withType<KotlinJvmTest>().configureEach {
        logger.lifecycle("UP-TO-DATE check for $name is disabled, forcing it to run.")
        outputs.upToDateWhen { false }
        ignoreFailures = true
        finalizedBy("jacocoTestReport")
    }
    tasks.withType<KotlinNativeSimulatorTest>().forEach {
        logger.lifecycle("Found native simulator test: $it")
    }

    runCatching {
        tasks.withType<KotlinTest>() {
            logger.lifecycle("UP-TO-DATE check for $name is disabled, forcing it to run.")
            outputs.upToDateWhen { false }
            ignoreFailures = true
            finalizedBy("jacocoTestReport")
        }
    }


    tasks.register("jacocoTestReport", JacocoReport::class) {
        dependsOn("jvmTest")
        val coverageSourceDirs = kotlin.sourceSets.filter { it.name.endsWith("Main") }.map { "src/${it.name}/kotlin" }

        val buildDirectory = layout.buildDirectory

        val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
        nativeTargets.forEach {
            runCatching {
                val target = kotlin.targets.getByName(it)
                val compilation = target.compilations.getByName("main")
                dependsOn("${target.name}Test")
                classDirectories.from(compilation.output.classesDirs)
            }
        }
        classDirectories.from(jvmMain.output.classesDirs)

        sourceDirectories.setFrom(files(coverageSourceDirs))

        buildDirectory.files("jacoco/jvmTest.exec").let {
            executionData.setFrom(it)
        }

        reports {
            xml.required.set(true)
            csv.required.set(true)
            html.required.set(true)
        }
    }
}

fun NamedDomainObjectContainer<KotlinSourceSet>.configureOptional(name: String, configure: KotlinSourceSet.() -> Unit) {
    if (name in names) {
        getByName(name).configure()
    }
}

fun KotlinMultiplatformExtension.addIosTargets(nativeTargets: List<String>): List<KotlinNativeTarget> {
    return buildList {
        if ("iosX64" in nativeTargets) add(iosX64())
        if ("iosArm64" in nativeTargets) add(iosArm64())
        if ("iosSimulatorArm64" in nativeTargets) add(iosSimulatorArm64())
    }
}

fun KotlinMultiplatformExtension.addAndroidNativeTargets(nativeTargets: List<String>): List<KotlinNativeTarget> {
    return buildList {
        if ("androidNativeX86" in nativeTargets) add(androidNativeX86())
        if ("androidNativeX64" in nativeTargets) add(androidNativeX64())
        if ("androidNativeArm32" in nativeTargets) add(androidNativeArm32())
        if ("androidNativeArm64" in nativeTargets) add(androidNativeArm64())
    }
}