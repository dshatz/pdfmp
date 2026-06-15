package com.dshatz.pdfmp

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

actual fun checkFilePath(path: kotlinx.io.files.Path): Boolean {
    return SystemFileSystem.exists(path)
}

actual fun readResource(name: String): ByteArray {
    return SystemFileSystem.source(Path(name)).buffered().use {
        it.readByteArray()
    }
}