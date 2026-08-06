package com.dshatz.pdfmp.imagebuffer

actual object ImageBufferUtil {
    actual fun allocate(
        width: Int,
        height: Int
    ): ImageBuffer {
        return ImageBuffer(allocateSkikoPixmap(width, height))
    }
}