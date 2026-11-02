package com.dshatz.pdfmp.imagebuffer

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.nativeHeap

@OptIn(ExperimentalForeignApi::class)
actual object ImageBufferUtil {
    actual fun allocate(
        width: Int,
        height: Int
    ): ImageBuffer {
        val stride = width * 4
        val pixels = nativeHeap.allocArray<ByteVar>(stride * height)
        return ImageBuffer(pixels, width, height, stride, owned = true)
    }
}
