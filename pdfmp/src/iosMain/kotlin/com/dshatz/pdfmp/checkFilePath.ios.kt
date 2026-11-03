package com.dshatz.pdfmp

import kotlinx.io.files.Path
import platform.Foundation.NSFileManager

actual fun checkFilePath(path: Path): Boolean {
    return NSFileManager.defaultManager.fileExistsAtPath(path.toString())
}
