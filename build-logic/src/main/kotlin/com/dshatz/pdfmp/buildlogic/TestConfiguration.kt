package com.dshatz.pdfmp.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

val Project.nativeTargets get() = run {
    val nativeTargets: String? by project
    nativeTargets?.split(',') ?: listOf("linuxX64", "linuxArm64", "mingwX64", "macosX64", "macosArm64")
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
    nativeTargets.forEach { target ->
        runCatching {
            tasks.named<Test>("${target}Test") {
                logger.lifecycle("UP-TO-DATE check for $name is disabled, forcing it to run.")
                outputs.upToDateWhen { false }
                ignoreFailures = true
                finalizedBy("jacocoTestReport")
            }
        }.onFailure {
            logger.lifecycle("Skipping native test: $target")
        }
    }


    tasks.register("jacocoTestReport", JacocoReport::class) {
        dependsOn("jvmTest")
        val coverageSourceDirs = kotlin.sourceSets.filter { it.name.endsWith("Main") }.map { "src/${it.name}" }

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