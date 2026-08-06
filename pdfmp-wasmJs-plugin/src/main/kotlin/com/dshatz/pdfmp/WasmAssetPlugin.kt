package com.dshatz.pdfmp

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

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
    }
}

abstract class InjectWasmTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun execute() {
        val targetDir = outputDir.get().asFile
        targetDir.mkdirs()

        val filesToCopy = listOf("pdfium.wasm", "pdfium.js", "pdfium-init.js")
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