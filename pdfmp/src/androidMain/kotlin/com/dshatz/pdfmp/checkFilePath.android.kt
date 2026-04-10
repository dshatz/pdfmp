package com.dshatz.pdfmp

import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import java.io.File
import kotlin.use

actual fun checkFilePath(path: Path): Boolean {
    val f = File(path.toString())
    return f.exists()
}

actual fun readResource(name: String): ByteArray {
    return SystemFileSystem::class.java.classLoader.getResourceAsStream(name)!!.asSource().buffered().use {
        it.readByteArray()
    }
}