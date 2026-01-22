package com.dshatz.pdfmp.source

/**
 * Buffer for transferring [CustomPdfSourceAdapter] bytes to pdfium library.
 */
expect class PlatformByteBuffer {
    fun capacity(): Int
    fun setBytes(data: ByteArray)
}