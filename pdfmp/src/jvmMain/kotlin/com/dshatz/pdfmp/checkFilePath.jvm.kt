package com.dshatz.pdfmp

import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

actual fun checkFilePath(path: Path): Boolean {
    return SystemFileSystem.exists(path)
}

actual fun readResource(name: String): ByteArray {
    return SystemFileSystem::class.java.classLoader.getResourceAsStream(name)!!.asSource().buffered().use {
        it.readByteArray()
    }
}