package com.dshatz.pdfmp

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.file.archive.ZipFileTree
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import sun.tools.jconsole.LabeledComponent.layout
import java.io.File
import kotlin.collections.map

class WasmAssetPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("injectWasmAsset", InjectWasmTask::class.java) {
            it.outputDir.set(project.layout.buildDirectory.dir("processedResources/wasmJs/main"))
        }

        project.afterEvaluate {
            project.tasks.named("wasmJsProcessResources").configure {
                it.dependsOn("injectWasmAsset")
            }
        }

        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val targetDir = project.layout.buildDirectory.dir("wasm-resources")
        val copyWasmResources = project.tasks.register<Copy>("copyWasmResources", Copy::class.java) {
            val compilation = kotlin.targets.getByName("wasmJs").compilations.getByName("main")
            val runtimeClasspath = compilation.compileDependencyFiles

            it.from(runtimeClasspath.map { file ->
                println("Dependency ${file.path}")
                if (file.extension == "klib" || file.extension == "jar") {
                    project.zipTree(file)
                } else {
                    project.fileTree(file)
                }
            })
            it.include("**/*.js", "**/*.wasm", "**/*.js.map")
            /*it.include("*.wasm", "*.js", "*.data", "*.json")*/
            it.exclude("META-INF/**")
            it.into(targetDir)
        }

        kotlin.sourceSets.configureEach {
            if (it.name == "wasmJsMain") {
                it.resources.srcDir(targetDir)
            }
        }

        project.afterEvaluate {
            val names = listOf("wasmJsBrowserDistribution", "wasmJsBrowserDevelopmentExecutableDistribution", "wasmJsProcessResources")
            project.tasks.matching { it.name in names }.configureEach {
                it.dependsOn(copyWasmResources)
            }
        }
    }
}


abstract class InjectWasmTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun execute() {
        val targetDir = outputDir.get().asFile
        targetDir.mkdirs()

        val filesToCopy = listOf("pdfium.wasm", "pdfium.js")
        filesToCopy.forEach { fileName ->
            val resourceStream = this::class.java.classLoader.getResourceAsStream(fileName)
                ?: throw IllegalStateException("Embedded $fileName not found in plugin resources.")

            resourceStream.use { input ->
                File(targetDir, fileName).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}