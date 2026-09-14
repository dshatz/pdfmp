package com.dshatz.pdfmp.imagebuffer

import org.jetbrains.skia.Data

actual object ImageBufferUtil {
    actual fun allocate(width: Int, height: Int): ImageBuffer {
        val stride = width * 4
        val size = stride * height
        println("WARN! Allocating $size bytes in skia")
        val data = Data.makeUninitialized(size)
        
        return ImageBuffer(
            width,
            height,
            stride,
            data = data
        )
    }
}