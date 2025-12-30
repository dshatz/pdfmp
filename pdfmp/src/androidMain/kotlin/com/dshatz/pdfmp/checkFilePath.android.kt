package com.dshatz.pdfmp

import kotlinx.io.files.Path
import java.io.File

actual fun checkFilePath(path: Path): Boolean {
    val f = File(path.toString())
    return f.exists()
}

actual fun readResource(name: String): ByteArray {
    error("Not implemented")
}