package com.dshatz.pdfmp.imagebuffer

import android.graphics.Bitmap

actual object ImageBufferUtil {
    actual fun allocate(width: Int, height: Int): ImageBuffer {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ImageBuffer(
            bitmap
        )
    }
}