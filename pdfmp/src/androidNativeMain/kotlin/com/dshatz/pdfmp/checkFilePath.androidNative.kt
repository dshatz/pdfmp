package com.dshatz.pdfmp

import platform.posix.F_OK
import platform.posix.access

actual fun checkFilePath(path: kotlinx.io.files.Path): Boolean {
    return access(path.toString(), F_OK) == 0
}

actual fun readResource(name: String): ByteArray {
    error("Not implemented")
}