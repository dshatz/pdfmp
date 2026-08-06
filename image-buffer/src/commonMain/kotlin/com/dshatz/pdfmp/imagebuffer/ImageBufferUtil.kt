package com.dshatz.pdfmp.imagebuffer

expect object ImageBufferUtil {
    fun allocate(width: Int, height: Int): ImageBuffer
}