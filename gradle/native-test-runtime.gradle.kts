import org.gradle.api.file.SourceDirectorySet

// Lets kotlin/native test binaries find the prebuilt pdfium at runtime (no rpath on mingw, relative install name on macos).
// Name-based on purpose: script plugins can't see the Kotlin Gradle plugin types.
val binariesDir = rootProject.project(":pdfium-binaries").projectDir.resolve("binaries")

// Windows resolves pdfium.dll through PATH.
tasks.matching { it.name == "mingwX64Test" }.configureEach {
    val dir = binariesDir.resolve("mingwX64").absolutePath
    withGroovyBuilder { "environment"("PATH", dir + File.pathSeparator + System.getenv("PATH")) }
}

// Linux and macOS resolve the shared lib through a library path variable
// (dyld also applies it to the relative "./libpdfium.dylib" install name).
val libPathVars = mapOf(
    "linuxX64" to "LD_LIBRARY_PATH",
    "linuxArm64" to "LD_LIBRARY_PATH",
    "macosArm64" to "DYLD_LIBRARY_PATH",
    "macosX64" to "DYLD_LIBRARY_PATH",
)
for ((target, variable) in libPathVars) {
    tasks.matching { it.name == "${target}Test" }.configureEach {
        val dir = binariesDir.resolve(target).absolutePath
        val old = System.getenv(variable)
        withGroovyBuilder { "environment"(variable, if (old.isNullOrEmpty()) dir else dir + File.pathSeparator + old) }
    }
}

// SAMPLE_PDF for native tests: the iOS simulator doesn't start in the module dir, so relative paths break.
// Source sets don't exist yet when this script is applied.
afterEvaluate {
    val sourceSets = extensions.getByName("kotlin").withGroovyBuilder { getProperty("sourceSets") } as NamedDomainObjectContainer<*>
    sourceSets.findByName("nativeTest")?.let { nativeTest ->
        val sample = rootProject.file("sample-shared/src/commonMain/composeResources/files/sample.pdf").invariantSeparatorsPath
        val outDir = layout.buildDirectory.dir("generated/testPaths")
        val generate = tasks.register("generateTestPaths") {
            inputs.property("sample", sample)
            outputs.dir(outDir)
            doLast {
                outDir.get().asFile.resolve("TestPaths.kt").apply { parentFile.mkdirs() }
                    .writeText("package com.dshatz.pdfmp.pdfium\n\nconst val SAMPLE_PDF = \"$sample\"\n")
            }
        }
        (nativeTest.withGroovyBuilder { getProperty("kotlin") } as SourceDirectorySet).srcDir(generate)
    }
}
