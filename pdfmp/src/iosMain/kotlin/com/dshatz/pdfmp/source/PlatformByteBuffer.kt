package com.dshatz.pdfmp.source

actual class PlatformByteBuffer(
    val buffer: ByteArray
) {
    actual fun capacity(): Int = buffer.size
}