package com.dshatz.pdfmp

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

actual fun checkFilePath(path: Path): Boolean {
    return SystemFileSystem.exists(path)
}
