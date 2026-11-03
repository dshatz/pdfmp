package com.dshatz.pdfmp

import kotlinx.io.files.SystemFileSystem

actual fun checkFilePath(path: kotlinx.io.files.Path): Boolean {
    return SystemFileSystem.exists(path)
}
