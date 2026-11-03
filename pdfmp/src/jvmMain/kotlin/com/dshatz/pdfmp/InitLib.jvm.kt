package com.dshatz.pdfmp

import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.io.path.createTempDirectory
import kotlin.use

actual class InitLib {
    private val osName = System.getProperty("os.name").lowercase(Locale.ENGLISH)
    private val osArch = System.getProperty("os.arch").lowercase(Locale.ENGLISH)
    
    actual fun init() {
        try {
            overrideLoadLibrary?.invoke("pdfium") ?: loadLibraryFromJar("pdfium")
            overrideLoadLibrary?.invoke("pdfmp") ?: loadLibraryFromJar("pdfmp")
            PDFBridge.initNative()
        } catch (e: UnsatisfiedLinkError) {
            e("Failed to load native library", e)
        }
    }

    /**
     * Extracts [baseName] from the jar to a temp file and loads it, once per process.
     *
     * [init] runs every time a document is opened. Extracting and loading again each time left a new temp
     * directory behind per document, and on macOS loading a second copy of the same dylib makes the
     * Objective-C runtime warn about duplicate classes.
     *
     * @return the extracted library file.
     */
    internal fun loadLibraryFromJar(baseName: String): File = synchronized(loadedFromJar) {
        loadedFromJar.getOrPut(baseName) { extractAndLoad(baseName) }
    }

    private fun extractAndLoad(baseName: String): File {
        val (platformDir, extension, prefix) = getPlatformDetails()

        val fileName = "${prefix}${baseName}.${extension}"

        val resourcePath = "/lib/$platformDir/$fileName"

        val tmpDir = createTempDirectory("pdfmp_libs").toFile()
        val tmpFile = File(tmpDir, fileName)
        // Deleted in reverse order of registration: the file first, then the (then empty) directory.
        tmpDir.deleteOnExit()
        tmpFile.deleteOnExit()

        val resourceStream = InitLib::class.java.getResourceAsStream(resourcePath)
            ?: throw UnsatisfiedLinkError("Native library not found in JAR at: $resourcePath")

        FileOutputStream(tmpFile).use { out ->
            resourceStream.use { it.copyTo(out) }
        }

        try {
            System.load(tmpFile.absolutePath)
        } catch (e: UnsatisfiedLinkError) {
            e("Failed to load library: ${tmpFile.absolutePath}", e)
            throw e
        }
        return tmpFile
    }

    private data class PlatformInfo(val dir: String, val ext: String, val prefix: String)

    private fun getPlatformDetails(): PlatformInfo {
        val isArm64 = osArch.contains("aarch64") || osArch.contains("arm64")
        val isX64 = osArch.contains("x86_64") || osArch.contains("amd64")

        if (osName.contains("win")) {
            // Windows: uses .dll, no "lib" prefix, folder "windows-x64"
            return PlatformInfo("windows-x64", "dll", "")
        }
        else if (osName.contains("mac")) {
            // Mac: uses .dylib, "lib" prefix
            val arch = if (isArm64) "arm64" else "x64"
            return PlatformInfo("macos-$arch", "dylib", "lib")
        }
        else if (osName.contains("nux") || osName.contains("nix")) {
            // Linux: uses .so, "lib" prefix
            // Check for ARM vs X64
            val arch = if (isArm64) "arm64" else "x64"
            return PlatformInfo("linux-$arch", "so", "lib")
        }

        throw UnsupportedOperationException("Unsupported OS/Arch: $osName / $osArch")
    }
    
    companion object {
        var overrideLoadLibrary: ((name: String) -> Unit)? = null

        /**
         * Libraries already extracted and loaded by [loadLibraryFromJar], by base name.
         */
        private val loadedFromJar = mutableMapOf<String, File>()
    }
}
