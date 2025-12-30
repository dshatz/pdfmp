package com.dshatz.pdfmp

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.io.files.Path
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

actual fun checkFilePath(path: Path): Boolean {
    return NSFileManager.defaultManager.fileExistsAtPath(path.toString())
}

actual fun readResource(name: String): ByteArray {
    val path = NSBundle.mainBundle.pathForResource(name, ofType = null)
        ?: (NSBundle.mainBundle.bundlePath + "/" + name)

    val data = NSData.dataWithContentsOfFile(path)
        ?: throw IllegalArgumentException("Resource not found at: $path")

    return ByteArray(data.length.toInt()).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }
}