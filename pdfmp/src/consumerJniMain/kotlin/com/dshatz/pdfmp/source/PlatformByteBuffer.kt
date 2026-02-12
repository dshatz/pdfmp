package com.dshatz.pdfmp.source

import java.nio.ByteBuffer

actual data class PlatformByteBuffer(
    val buffer: ByteBuffer
) {
    actual fun capacity(): Int = buffer.capacity()

    actual fun setBytes(data: ByteArray) {
        buffer.clear()
        buffer.put(data)
    }

}